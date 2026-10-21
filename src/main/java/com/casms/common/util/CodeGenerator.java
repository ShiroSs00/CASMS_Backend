package com.casms.common.util;

import java.util.function.Predicate;

public final class CodeGenerator {

    private CodeGenerator() {
    }

    /**
     * Generates a unique business code in the format ENTITY-XXX
     * (e.g. CRUISE-001, ACTIVITY-001, ISSUE-001, TOURGROUP-001, etc.)
     *
     * @param entityPrefix    Prefix representing the entity (e.g. "CRUISE", "ACTIVITY")
     * @param startSeq        Starting sequence number (usually count + 1)
     * @param existsPredicate Predicate checking if candidate code already exists in DB
     * @return Unique formatted code
     */
    public static String generate(String entityPrefix, long startSeq, Predicate<String> existsPredicate) {
        String prefix = (entityPrefix != null && !entityPrefix.isBlank()) ? entityPrefix.trim().toUpperCase() : "ENTITY";
        long seq = Math.max(1, startSeq);
        while (true) {
            String candidate = String.format("%s-%03d", prefix, seq);
            if (existsPredicate == null || !existsPredicate.test(candidate)) {
                return candidate;
            }
            seq++;
        }
    }
}
