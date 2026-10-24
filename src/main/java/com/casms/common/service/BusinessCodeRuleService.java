package com.casms.common.service;

import com.casms.common.dto.BusinessCodeRuleResponse;
import com.casms.common.dto.BusinessCodeRuleResponse.TokenMetadata;
import com.casms.common.dto.BusinessCodeRuleUpdateRequest;
import com.casms.common.dto.CodePreviewRequest;
import com.casms.common.dto.CodePreviewResponse;
import com.casms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessCodeRuleService {

    private final JdbcTemplate jdbcTemplate;
    private static final Pattern SEQ_REGEX = Pattern.compile("\\{SEQ:?(\\d+)?\\}");

    public List<BusinessCodeRuleResponse> getAllRules() {
        String sql = "SELECT entity_type, pattern, scope_strategy, description, is_active, updated_at FROM business_code_rule ORDER BY entity_type";
        return jdbcTemplate.query(sql, (rs, i) -> {
            String entityType = rs.getString("entity_type");
            return BusinessCodeRuleResponse.builder()
                    .entityType(entityType)
                    .pattern(rs.getString("pattern"))
                    .scopeStrategy(rs.getString("scope_strategy"))
                    .description(rs.getString("description"))
                    .active(rs.getBoolean("is_active"))
                    .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now())
                    .supportedTokens(getSupportedTokens(entityType))
                    .defaultSampleContext(getDefaultSampleContext(entityType))
                    .build();
        });
    }

    public BusinessCodeRuleResponse getRuleByEntityType(String entityType) {
        String normalizedEntity = entityType.trim().toUpperCase();
        String sql = "SELECT entity_type, pattern, scope_strategy, description, is_active, updated_at FROM business_code_rule WHERE UPPER(entity_type) = ?";
        List<BusinessCodeRuleResponse> list = jdbcTemplate.query(sql, (rs, i) -> BusinessCodeRuleResponse.builder()
                .entityType(rs.getString("entity_type"))
                .pattern(rs.getString("pattern"))
                .scopeStrategy(rs.getString("scope_strategy"))
                .description(rs.getString("description"))
                .active(rs.getBoolean("is_active"))
                .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now())
                .supportedTokens(getSupportedTokens(normalizedEntity))
                .defaultSampleContext(getDefaultSampleContext(normalizedEntity))
                .build(), normalizedEntity);

        if (list.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Không tìm thấy cấu hình mã cho: " + entityType);
        }
        return list.get(0);
    }

    public CodePreviewResponse previewCode(CodePreviewRequest request) {
        String entityType = request.getEntityType().trim().toUpperCase();
        String pattern = request.getPattern().trim();
        List<String> warnings = new ArrayList<>();

        Matcher seqMatcher = SEQ_REGEX.matcher(pattern);
        if (!seqMatcher.find()) {
            warnings.add("Cảnh báo: Thiếu token số thứ tự {SEQ:n}. Mã có nguy cơ bị trùng lặp nếu sinh hàng loạt.");
        }

        Map<String, String> context = new HashMap<>(getDefaultSampleContext(entityType));
        if (request.getSampleContext() != null) {
            context.putAll(request.getSampleContext());
        }

        LocalDate now = LocalDate.now();
        String yearFull = String.valueOf(now.getYear());
        String yearShort = yearFull.substring(2);

        String result = pattern;
        result = result.replace("{ENTITY}", entityType);
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey().toUpperCase() + "}", entry.getValue());
            }
        }
        result = result.replace("{YYYY}", yearFull);
        result = result.replace("{YY}", yearShort);

        // Preview format with sample sequence 1
        Matcher matcher = SEQ_REGEX.matcher(result);
        if (matcher.find()) {
            int padding = 4;
            if (matcher.group(1) != null) {
                try {
                    padding = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
            String formattedSeq = String.format("%0" + padding + "d", 1);
            result = matcher.replaceFirst(formattedSeq);
        }

        String simulatedKey = buildSequenceKey(entityType, request.getScopeStrategy(), context, yearFull);

        return CodePreviewResponse.builder()
                .previewCode(result)
                .simulatedKey(simulatedKey)
                .valid(warnings.isEmpty())
                .warnings(warnings)
                .build();
    }

    @Transactional
    public BusinessCodeRuleResponse updateRule(String entityType, BusinessCodeRuleUpdateRequest request) {
        String normalizedEntity = entityType.trim().toUpperCase();
        String pattern = request.getPattern().trim();

        if (!pattern.contains("{SEQ")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PATTERN",
                    "Pattern bắt buộc phải chứa token {SEQ:n} (ví dụ {SEQ:4}) để đảm bảo tính duy nhất.");
        }

        String sql = """
            UPDATE business_code_rule 
            SET pattern = ?, scope_strategy = ?, description = ?, updated_at = CURRENT_TIMESTAMP
            WHERE UPPER(entity_type) = ?
        """;
        int updated = jdbcTemplate.update(sql, pattern, request.getScopeStrategy().trim().toUpperCase(), request.getDescription(), normalizedEntity);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Không tìm thấy cấu hình mã để cập nhật cho: " + entityType);
        }

        log.info("Updated Business Code Rule for {}: pattern={}, strategy={}", normalizedEntity, pattern, request.getScopeStrategy());
        return getRuleByEntityType(normalizedEntity);
    }

    /**
     * Sinh mã thực tế an toàn đa luồng (concurrency-safe) từ cơ sở dữ liệu.
     */
    @Transactional
    public String generateCode(String entityType, Map<String, String> context) {
        String normalizedEntity = entityType.trim().toUpperCase();
        String sql = "SELECT pattern, scope_strategy FROM business_code_rule WHERE UPPER(entity_type) = ? AND is_active = true";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, normalizedEntity);

        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Chưa cấu hình rule sinh mã cho: " + entityType);
        }

        String pattern = (String) rows.get(0).get("pattern");
        String scopeStrategy = (String) rows.get(0).get("scope_strategy");

        LocalDate now = LocalDate.now();
        String yearFull = String.valueOf(now.getYear());
        String yearShort = yearFull.substring(2);

        String seqKey = buildSequenceKey(normalizedEntity, scopeStrategy, context, yearFull);
        long nextVal = getNextSequenceValue(seqKey);

        String result = pattern;
        result = result.replace("{ENTITY}", normalizedEntity);
        if (context != null) {
            for (Map.Entry<String, String> entry : context.entrySet()) {
                if (entry.getValue() != null) {
                    result = result.replace("{" + entry.getKey().toUpperCase() + "}", entry.getValue());
                }
            }
        }
        result = result.replace("{YYYY}", yearFull);
        result = result.replace("{YY}", yearShort);

        Matcher matcher = SEQ_REGEX.matcher(result);
        if (matcher.find()) {
            int padding = 4;
            if (matcher.group(1) != null) {
                try {
                    padding = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
            result = matcher.replaceFirst(String.format("%0" + padding + "d", nextVal));
        }

        return result;
    }

    private long getNextSequenceValue(String sequenceKey) {
        String sql = """
            INSERT INTO code_sequence_tracker (sequence_key, current_value, updated_at)
            VALUES (?, 1, CURRENT_TIMESTAMP)
            ON CONFLICT (sequence_key)
            DO UPDATE SET 
                current_value = code_sequence_tracker.current_value + 1,
                updated_at = CURRENT_TIMESTAMP
            RETURNING current_value;
        """;
        Long val = jdbcTemplate.queryForObject(sql, Long.class, sequenceKey);
        return val != null ? val : 1L;
    }

    private String buildSequenceKey(String entityType, String scopeStrategy, Map<String, String> context, String year) {
        if (scopeStrategy == null) {
            return entityType + "_GLOBAL";
        }
        return switch (scopeStrategy.toUpperCase()) {
            case "BY_YEAR" -> entityType + "_" + year;
            case "BY_TYPE_AND_YEAR" -> {
                String scope = context != null && context.get("SCOPE") != null ? context.get("SCOPE") : "GEN";
                String type = context != null && context.get("TYPE") != null ? context.get("TYPE") : "STD";
                yield String.format("%s_%s_%s_%s", entityType, scope, type, year);
            }
            case "BY_PORT" -> {
                String port = context != null && context.get("PORT") != null ? context.get("PORT") : "ANY";
                yield entityType + "_" + port;
            }
            default -> entityType + "_GLOBAL";
        };
    }

    private List<TokenMetadata> getSupportedTokens(String entityType) {
        List<TokenMetadata> tokens = new ArrayList<>();
        tokens.add(TokenMetadata.builder().key("{ENTITY}").label("Tên thực thể (" + entityType + ")").example(entityType).build());
        tokens.add(TokenMetadata.builder().key("{YYYY}").label("Năm (4 số)").example("2026").build());
        tokens.add(TokenMetadata.builder().key("{YY}").label("Năm (2 số)").example("26").build());
        tokens.add(TokenMetadata.builder().key("{SEQ:3}").label("STT 3 chữ số").example("001").build());
        tokens.add(TokenMetadata.builder().key("{SEQ:4}").label("STT 4 chữ số").example("0001").build());
        tokens.add(TokenMetadata.builder().key("{SEQ:5}").label("STT 5 chữ số").example("00001").build());

        if ("TRIP".equalsIgnoreCase(entityType)) {
            tokens.add(TokenMetadata.builder().key("{SCOPE}").label("Phạm vi (EX: Quốc tế, DO: Nội địa)").example("EX").build());
            tokens.add(TokenMetadata.builder().key("{TYPE}").label("Loại chuyến (IT: Lịch trình, CH: Thuê bao)").example("IT").build());
        } else if ("SHIP".equalsIgnoreCase(entityType)) {
            tokens.add(TokenMetadata.builder().key("{SHIP_TYPE}").label("Loại tàu (LUX: Du thuyền, EXP: Thám hiểm, CAT: Hai thân)").example("LUX").build());
        } else if ("CRUISE".equalsIgnoreCase(entityType)) {
            tokens.add(TokenMetadata.builder().key("{SHIP}").label("Mã tàu thực tế").example("SH01").build());
            tokens.add(TokenMetadata.builder().key("{PORT}").label("Mã cảng xuất phát").example("SGN").build());
        } else if ("EXCURSION".equalsIgnoreCase(entityType)) {
            tokens.add(TokenMetadata.builder().key("{PORT}").label("Mã cảng ghé").example("SGN").build());
        }
        return tokens;
    }

    private Map<String, String> getDefaultSampleContext(String entityType) {
        Map<String, String> map = new HashMap<>();
        if ("TRIP".equalsIgnoreCase(entityType)) {
            map.put("SCOPE", "EX");
            map.put("TYPE", "IT");
        } else if ("SHIP".equalsIgnoreCase(entityType)) {
            map.put("SHIP_TYPE", "LUX");
        } else if ("CRUISE".equalsIgnoreCase(entityType)) {
            map.put("SHIP", "SH01");
            map.put("PORT", "SGN");
        } else if ("EXCURSION".equalsIgnoreCase(entityType)) {
            map.put("PORT", "DAD");
        }
        return map;
    }
}
