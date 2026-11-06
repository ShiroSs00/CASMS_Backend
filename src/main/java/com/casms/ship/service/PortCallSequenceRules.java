package com.casms.ship.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Route identity rules independent of whether calls use relative or concrete times. */
public final class PortCallSequenceRules {
    private PortCallSequenceRules() {}

    public record Call(String key, Integer order, UUID portId, boolean disembarkation) {}

    public record Violation(Call call, String code, String field, String message) {}

    public static List<Violation> validate(List<Call> calls) {
        var ordered = calls.stream().filter(call -> call.order() != null)
                .sorted(Comparator.comparing(Call::order)).toList();
        var violations = new ArrayList<Violation>();
        boolean foundEnd = false;
        for (int i = 0; i < ordered.size(); i++) {
            Call call = ordered.get(i);
            if (i > 0 && call.portId() != null && call.portId().equals(ordered.get(i - 1).portId())) {
                violations.add(new Violation(call, "CONSECUTIVE_SAME_PORT", "portId",
                        "Hai lượt ghé liên tiếp không được trùng cảng. Phải ghé một cảng khác trước khi quay lại."));
            }
            if (call.disembarkation()) {
                if (foundEnd) {
                    violations.add(new Violation(call, "DUPLICATE_DISEMBARKATION", "isDisembarkation",
                            "Chỉ được đánh dấu một lượt ghé là cảng kết thúc."));
                }
                foundEnd = true;
            }
        }
        if (foundEnd && !ordered.getLast().disembarkation()) {
            ordered.stream().filter(Call::disembarkation).forEach(call ->
                    violations.add(new Violation(call, "DISEMBARKATION_MUST_BE_FINAL_STOP", "isDisembarkation",
                            "Cảng kết thúc phải là lượt ghé cuối theo thứ tự lịch trình.")));
        }
        return violations;
    }
}
