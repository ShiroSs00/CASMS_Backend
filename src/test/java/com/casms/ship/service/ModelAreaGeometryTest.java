package com.casms.ship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelAreaGeometryTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void preservesParentTransformAndConvertsQuaternionToRadians() throws Exception {
        var doc=mapper.readTree("{\"nodes\":[{\"translation\":[10,2,0],\"children\":[1]},{\"translation\":[1,3,4],\"rotation\":[0,0.7071067811865475,0,0.7071067811865476],\"scale\":[2,3,4]}]}");
        double[] world=new ModelAreaGeometry(doc).world(1), scale=ModelAreaGeometry.scale(world);
        assertEquals(11,world[12],1e-9);assertEquals(5,world[13],1e-9);assertEquals(4,world[14],1e-9);
        assertArrayEquals(new double[]{2,3,4},scale,1e-9);
        assertEquals(Math.PI/2,ModelAreaGeometry.rotation(world,scale)[1],1e-9);
    }
    @Test void acceptsAffineMatrixWithoutInventingMissingTransforms() throws Exception {
        var doc=mapper.readTree("{\"nodes\":[{\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,7,8,9,1]}]}");
        assertEquals(7,new ModelAreaGeometry(doc).world(0)[12]);
    }
    @Test void preservesShearWithoutInventingEulerRotation() throws Exception {
        var doc=mapper.readTree("{\"nodes\":[{\"matrix\":[1,0,0,0,1,1,0,0,0,0,1,0,7,8,9,1]}]}");
        double[] world = new ModelAreaGeometry(doc).world(0);
        assertEquals(1, world[4]);
        assertNull(ModelAreaGeometry.rotation(world, ModelAreaGeometry.scale(world)));
    }
    @Test void rejectsCyclicHierarchy() throws Exception {
        var doc=mapper.readTree("{\"nodes\":[{\"children\":[1]},{\"children\":[0]}]}");
        assertThrows(IllegalArgumentException.class,()->new ModelAreaGeometry(doc).world(0));
    }
    @Test void rejectsMalformedQuaternionAndChildReferences() throws Exception {
        var doc=mapper.readTree("{\"nodes\":[{\"rotation\":[0,0,0,0]}]}");
        assertThrows(IllegalArgumentException.class,()->new ModelAreaGeometry(doc).world(0));
        assertThrows(IllegalArgumentException.class,()->new ModelAreaGeometry(mapper.readTree("{\"nodes\":[{\"children\":[99]}]}")));
    }
}
