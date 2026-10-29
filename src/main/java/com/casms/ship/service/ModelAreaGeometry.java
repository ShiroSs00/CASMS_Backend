package com.casms.ship.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Model-space geometry for a physical area. Binary mesh data remains in the immutable asset. */
final class ModelAreaGeometry {
    private final JsonNode nodes;
    private final int[] parents;
    ModelAreaGeometry(JsonNode glb) {
        nodes = glb.path("nodes");
        parents = new int[nodes.size()];
        Arrays.fill(parents, -1);
        for (int i = 0; i < nodes.size(); i++) for (JsonNode child : nodes.get(i).path("children")) {
            if (!child.isIntegralNumber() || !child.canConvertToInt() || child.asInt() < 0 || child.asInt() >= nodes.size())
                throw new IllegalArgumentException("Invalid GLB child reference");
            int c = child.asInt();
            if (parents[c] != -1 || c == i) throw new IllegalArgumentException("Ambiguous GLB parent or cycle");
            parents[c] = i;
        }
    }

    double[] world(int index) {
        List<Integer> chain = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int i = index; i != -1; i = parents[i]) {
            if (!seen.add(i)) throw new IllegalArgumentException("Cycle in area ancestors");
            chain.add(i);
        }
        double[] result = identity();
        for (int j = chain.size() - 1; j >= 0; j--) result = multiply(result, local(nodes.get(chain.get(j))));
        for (double v : result) if (!Double.isFinite(v)) throw new IllegalArgumentException("Non-finite area world transform");
        return result;
    }

    static double[] vector(JsonNode n, int size) {
        if (!n.isArray() || n.size() != size) throw new IllegalArgumentException("Invalid area transform vector");
        double[] a = new double[size];
        for (int i = 0; i < size; i++) {
            if (!n.get(i).isNumber() || !Double.isFinite(n.get(i).asDouble())) throw new IllegalArgumentException("Non-finite area transform");
            a[i] = n.get(i).asDouble();
        }
        return a;
    }

    private static double[] identity() { return new double[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}; }

    private static double[] local(JsonNode n) {
        if (n.has("matrix")) {
            if (n.has("translation") || n.has("rotation") || n.has("scale")) throw new IllegalArgumentException("GLB node mixes matrix and TRS");
            double[] m = vector(n.get("matrix"), 16);
            if (Math.abs(m[3]) + Math.abs(m[7]) + Math.abs(m[11]) + Math.abs(m[15] - 1) > 1e-8)
                throw new IllegalArgumentException("Area matrix must be affine");
            return m;
        }
        double[] t = n.has("translation") ? vector(n.get("translation"), 3) : new double[3];
        double[] s = n.has("scale") ? vector(n.get("scale"), 3) : new double[]{1,1,1};
        double[] q = n.has("rotation") ? vector(n.get("rotation"), 4) : new double[]{0,0,0,1};
        double norm = 0;
        for (double v : q) norm += v*v;
        if (Math.abs(norm - 1) > 1e-4) throw new IllegalArgumentException("Area quaternion must have unit length");
        double x = q[0], y = q[1], z = q[2], w = q[3];
        return new double[]{(1-2*y*y-2*z*z)*s[0],(2*x*y+2*z*w)*s[0],(2*x*z-2*y*w)*s[0],0,
                (2*x*y-2*z*w)*s[1],(1-2*x*x-2*z*z)*s[1],(2*y*z+2*x*w)*s[1],0,
                (2*x*z+2*y*w)*s[2],(2*y*z-2*x*w)*s[2],(1-2*x*x-2*y*y)*s[2],0,t[0],t[1],t[2],1};
    }
    private static double[] multiply(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) for (int k = 0; k < 4; k++)
            r[c*4+row] += a[k*4+row] * b[c*4+k];
        return r;
    }

    static double[] scale(double[] m) {
        double[] s = {Math.hypot(Math.hypot(m[0],m[1]),m[2]), Math.hypot(Math.hypot(m[4],m[5]),m[6]), Math.hypot(Math.hypot(m[8],m[9]),m[10])};
        double determinant = m[0]*(m[5]*m[10]-m[9]*m[6])-m[4]*(m[1]*m[10]-m[9]*m[2])+m[8]*(m[1]*m[6]-m[5]*m[2]);
        if (determinant < 0) s[0] = -s[0];
        return s;
    }

    static double[] rotation(double[] m, double[] s) {
        if (s[0] == 0 || s[1] == 0 || s[2] == 0) return null;
        // A rotated child under a non-uniformly scaled parent can produce shear.
        // Euler angles cannot represent that transform; the exact worldMatrix is retained.
        for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
            double dot = 0;
            for (int row = 0; row < 3; row++) dot += (m[a * 4 + row] / s[a]) * (m[b * 4 + row] / s[b]);
            if (Math.abs(dot) > 1e-6) return null;
        }
        double y = Math.asin(Math.max(-1, Math.min(1, m[8]/s[2])));
        return Math.abs(m[8]/s[2]) < .9999999
                ? new double[]{Math.atan2(-m[9]/s[2],m[10]/s[2]), y, Math.atan2(-m[4]/s[1],m[0]/s[0])}
                : new double[]{Math.atan2(m[6]/s[1],m[5]/s[1]), y, 0};
    }
}
