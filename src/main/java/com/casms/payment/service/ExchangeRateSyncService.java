package com.casms.payment.service;

import com.casms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateSyncService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> TRACKED_CURRENCIES = Set.of("VND", "SGD", "EUR", "JPY", "THB", "HKD", "MYR");
    private static final BigDecimal MIN_MANUAL_RATE = new BigDecimal("1000");
    private static final BigDecimal MAX_MANUAL_RATE = new BigDecimal("1000000");
    private static final BigDecimal MIN_AUTO_RATE = new BigDecimal("10000");
    private static final BigDecimal MAX_AUTO_RATE = new BigDecimal("100000");
    private static final BigDecimal MAX_AUTO_DEVIATION_RATIO = new BigDecimal("0.05");

    private static final String VCB_URL = "https://portal.vietcombank.com.vn/Usercontrols/TVPortal.TyGia/pXML.aspx";
    private static final String FALLBACK_URL = "https://open.er-api.com/v6/latest/USD";

    private final JdbcTemplate jdbc;
    private final CurrencyExchangeService currencyExchangeService;
    private final ObjectMapper objectMapper;

    @Value("${casms.fx.auto-sync-enabled:true}")
    private boolean autoSyncEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record RateView(
            LocalDate effectiveOn,
            BigDecimal vndPerUsd,
            String source,
            Instant publishedAt,
            boolean autoSynced) {}

    public record SyncResult(
            LocalDate effectiveOn,
            BigDecimal vndPerUsd,
            String source,
            Instant publishedAt,
            boolean autoSynced,
            String providerUsed,
            boolean fallbackUsed,
            int syncedCurrenciesCount,
            String message) {}

    private record FetchedRates(
            BigDecimal vndPerUsd,
            Map<String, BigDecimal> usdBaseRates,
            String sourceLabel,
            String providerUsed,
            boolean fallbackUsed) {}

    @Transactional(readOnly = true)
    public RateView getCurrentRate() {
        return jdbc.query("""
                SELECT effective_on, vnd_per_usd, source, published_at FROM "UsdDisplayRate"
                WHERE effective_on <= ? ORDER BY effective_on DESC LIMIT 1
                """,
                (rs, i) -> {
                    var ts = rs.getTimestamp(4);
                    return toRateView(
                            rs.getObject(1, LocalDate.class),
                            rs.getBigDecimal(2),
                            rs.getString(3),
                            ts != null ? ts.toInstant() : null);
                },
                LocalDate.now(VIETNAM))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public RateView saveManualRate(LocalDate effectiveOn, BigDecimal vndPerUsd, String source, UUID actorUserId) {
        LocalDate todayVn = LocalDate.now(VIETNAM);
        if (effectiveOn == null || effectiveOn.isAfter(todayVn)) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "REFERENCE_RATE_FUTURE_DATE",
                    "Ngày hiệu lực của tỷ giá phải là hôm nay hoặc trước đó.");
        }
        if (vndPerUsd == null
                || vndPerUsd.compareTo(MIN_MANUAL_RATE) < 0
                || vndPerUsd.compareTo(MAX_MANUAL_RATE) > 0) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "REFERENCE_RATE_OUT_OF_RANGE",
                    "Tỷ giá USD/VND không hợp lý (phải nằm trong khoảng 1.000 đến 1.000.000 VND cho 1 USD).");
        }
        String cleanSource = source == null ? "" : source.strip();
        if (cleanSource.isEmpty() || cleanSource.length() > 160) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RATE_SOURCE",
                    "Nguồn tỷ giá bắt buộc nhập và tối đa 160 ký tự.");
        }

        BigDecimal normalizedRate = vndPerUsd.setScale(4, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        upsertDisplayRate(effectiveOn, normalizedRate, cleanSource, actorUserId);

        RateView latest = getCurrentRate();
        if (latest == null || !effectiveOn.isBefore(latest.effectiveOn())) {
            currencyExchangeService.registerExchangeRate("USD", "VND", normalizedRate, cleanSource, now);
        }

        return readRateByDate(effectiveOn)
                .orElseGet(() -> toRateView(effectiveOn, normalizedRate, cleanSource, now));
    }

    @Scheduled(cron = "${casms.fx.sync-cron:0 30 8,14 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void scheduledAutoSync() {
        if (!autoSyncEnabled) {
            return;
        }
        try {
            UUID systemActor = resolveSystemPublisherId();
            if (systemActor == null) {
                log.warn("[FX-SYNC] Skipped scheduled sync: no valid publisher user found in database.");
                return;
            }
            SyncResult result = syncNow(systemActor, false);
            log.info("[FX-SYNC] Scheduled sync succeeded: 1 USD = {} VND ({}, {} currencies).",
                    result.vndPerUsd(), result.providerUsed(), result.syncedCurrenciesCount());
        } catch (BusinessException ex) {
            log.warn("[FX-SYNC] Scheduled sync skipped/rejected [{}]: {}", ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("[FX-SYNC] Scheduled sync failed unexpectedly: {}", ex.getMessage());
        }
    }

    @Transactional
    public SyncResult syncNow(UUID actorUserId, boolean force) {
        UUID publisherId = actorUserId != null ? actorUserId : resolveSystemPublisherId();
        if (publisherId == null) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FX_PUBLISHER_NOT_FOUND",
                    "Không xác định được tài khoản ghi nhận tỷ giá trong hệ thống.");
        }

        FetchedRates fetched = fetchFromProviders();
        BigDecimal newRate = fetched.vndPerUsd().setScale(4, RoundingMode.HALF_UP);

        if (newRate.compareTo(MIN_AUTO_RATE) < 0 || newRate.compareTo(MAX_AUTO_RATE) > 0) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FX_RATE_ANOMALY",
                    String.format(Locale.ROOT,
                            "Tỷ giá nhận được (%.2f VND/USD) nằm ngoài ngưỡng an toàn (10.000–100.000 VND/USD). Hệ thống giữ nguyên tỷ giá hiện hành.",
                            newRate));
        }

        RateView previous = getCurrentRate();
        if (!force && previous != null && previous.vndPerUsd() != null && previous.vndPerUsd().signum() > 0) {
            BigDecimal diff = newRate.subtract(previous.vndPerUsd()).abs();
            BigDecimal ratio = diff.divide(previous.vndPerUsd(), 6, RoundingMode.HALF_UP);
            if (ratio.compareTo(MAX_AUTO_DEVIATION_RATIO) > 0) {
                BigDecimal percent = ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "FX_RATE_ANOMALY",
                        String.format(Locale.ROOT,
                                "Tỷ giá mới (%s VND/USD) chênh lệch %s%% so với tỷ giá hiện hành (%s VND/USD), vượt ngưỡng an toàn 5%%. Vui lòng kiểm tra trước khi xác nhận ghi đè.",
                                newRate.stripTrailingZeros().toPlainString(),
                                percent.toPlainString(),
                                previous.vndPerUsd().stripTrailingZeros().toPlainString()));
            }
        }

        LocalDate todayVn = LocalDate.now(VIETNAM);
        Instant now = Instant.now();
        upsertDisplayRate(todayVn, newRate, fetched.sourceLabel(), publisherId);

        int syncedCount = 0;
        for (Map.Entry<String, BigDecimal> entry : fetched.usdBaseRates().entrySet()) {
            if (entry.getValue() != null && entry.getValue().signum() > 0) {
                currencyExchangeService.registerExchangeRate(
                        "USD", entry.getKey(), entry.getValue(), fetched.sourceLabel(), now);
                syncedCount++;
            }
        }

        RateView saved = readRateByDate(todayVn)
                .orElseGet(() -> toRateView(todayVn, newRate, fetched.sourceLabel(), now));

        String msg = fetched.fallbackUsed()
                ? "Đã đồng bộ tỷ giá từ nguồn dự phòng (Vietcombank tạm thời không phản hồi)."
                : "Đã đồng bộ tỷ giá bán ra mới nhất từ Vietcombank.";

        return new SyncResult(
                saved.effectiveOn(),
                saved.vndPerUsd(),
                saved.source(),
                saved.publishedAt(),
                saved.autoSynced(),
                fetched.providerUsed(),
                fetched.fallbackUsed(),
                syncedCount,
                msg);
    }

    private FetchedRates fetchFromProviders() {
        try {
            return fetchFromVietcombank();
        } catch (Exception vcbError) {
            log.warn("[FX-SYNC] Vietcombank XML fetch failed ({}), switching to fallback provider.", vcbError.getMessage());
            try {
                return fetchFromOpenErApi();
            } catch (Exception fallbackError) {
                log.error("[FX-SYNC] Both FX providers failed. VCB: {} | Fallback: {}",
                        vcbError.getMessage(), fallbackError.getMessage());
                throw new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "FX_UPSTREAM_UNAVAILABLE",
                        "Không thể kết nối nguồn tỷ giá (Vietcombank và nguồn dự phòng). Tỷ giá hiện hành vẫn được giữ nguyên; vui lòng thử lại sau hoặc nhập ghi đè thủ công.");
            }
        }
    }

    private FetchedRates fetchFromVietcombank() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(VCB_URL))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "OceVie-CASMS-FXSync/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null || resp.body().isBlank()) {
            throw new IllegalStateException("Vietcombank HTTP " + resp.statusCode());
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(resp.body())));
        NodeList nodes = doc.getElementsByTagName("Exrate");

        Map<String, BigDecimal> vndPerUnit = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String code = el.getAttribute("CurrencyCode");
            if (code == null || code.isBlank()) continue;
            String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
            BigDecimal rate = parseVcbDecimal(el.getAttribute("Sell"));
            if (rate == null) {
                rate = parseVcbDecimal(el.getAttribute("Transfer"));
            }
            if (rate != null && rate.signum() > 0) {
                vndPerUnit.put(normalizedCode, rate);
            }
        }

        BigDecimal usdVnd = vndPerUnit.get("USD");
        if (usdVnd == null || usdVnd.signum() <= 0) {
            throw new IllegalStateException("Vietcombank XML missing USD rate");
        }

        Map<String, BigDecimal> usdBaseRates = new LinkedHashMap<>();
        usdBaseRates.put("VND", usdVnd.setScale(6, RoundingMode.HALF_UP));
        for (String target : TRACKED_CURRENCIES) {
            if ("VND".equals(target)) continue;
            BigDecimal targetVnd = vndPerUnit.get(target);
            if (targetVnd != null && targetVnd.signum() > 0) {
                usdBaseRates.put(target, usdVnd.divide(targetVnd, 6, RoundingMode.HALF_UP));
            }
        }

        return new FetchedRates(
                usdVnd,
                usdBaseRates,
                "Vietcombank (Bán ra · Tự động)",
                "VIETCOMBANK_XML",
                false);
    }

    private FetchedRates fetchFromOpenErApi() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(FALLBACK_URL))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "OceVie-CASMS-FXSync/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300 || resp.body() == null || resp.body().isBlank()) {
            throw new IllegalStateException("OpenER API HTTP " + resp.statusCode());
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode ratesNode = root.path("rates");
        if (ratesNode.isMissingNode() || !ratesNode.has("VND")) {
            throw new IllegalStateException("OpenER API missing VND rate");
        }

        BigDecimal usdVnd = ratesNode.get("VND").decimalValue();
        if (usdVnd == null || usdVnd.signum() <= 0) {
            throw new IllegalStateException("OpenER API returned invalid VND rate");
        }

        Map<String, BigDecimal> usdBaseRates = new LinkedHashMap<>();
        for (String target : TRACKED_CURRENCIES) {
            JsonNode node = ratesNode.get(target);
            if (node != null && node.isNumber()) {
                BigDecimal val = node.decimalValue();
                if (val != null && val.signum() > 0) {
                    usdBaseRates.put(target, val.setScale(6, RoundingMode.HALF_UP));
                }
            }
        }

        return new FetchedRates(
                usdVnd,
                usdBaseRates,
                "Open Exchange Rate API (Dự phòng · Tự động)",
                "OPEN_ER_API",
                true);
    }

    private static BigDecimal parseVcbDecimal(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim().replace(",", "");
        if (cleaned.isEmpty() || "-".equals(cleaned)) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void upsertDisplayRate(LocalDate effectiveOn, BigDecimal vndPerUsd, String source, UUID publisherId) {
        jdbc.update("""
                INSERT INTO "UsdDisplayRate"(effective_on, vnd_per_usd, source, published_by, published_at)
                VALUES(?, ?, ?, ?, now())
                ON CONFLICT (effective_on) DO UPDATE SET
                    vnd_per_usd = excluded.vnd_per_usd,
                    source = excluded.source,
                    published_by = excluded.published_by,
                    published_at = now()
                """, effectiveOn, vndPerUsd, source, publisherId);
    }

    private Optional<RateView> readRateByDate(LocalDate effectiveOn) {
        return jdbc.query("""
                SELECT effective_on, vnd_per_usd, source, published_at FROM "UsdDisplayRate"
                WHERE effective_on = ?
                """,
                (rs, i) -> {
                    var ts = rs.getTimestamp(4);
                    return toRateView(
                            rs.getObject(1, LocalDate.class),
                            rs.getBigDecimal(2),
                            rs.getString(3),
                            ts != null ? ts.toInstant() : null);
                },
                effectiveOn)
                .stream()
                .findFirst();
    }

    private UUID resolveSystemPublisherId() {
        List<UUID> existing = jdbc.queryForList("""
                SELECT published_by FROM "UsdDisplayRate" ORDER BY published_at DESC LIMIT 1
                """, UUID.class);
        if (!existing.isEmpty() && existing.getFirst() != null) {
            return existing.getFirst();
        }
        List<UUID> financeOrAdmin = jdbc.queryForList("""
                SELECT u.user_id FROM "User" u
                JOIN "UserRole" ur ON ur.user_id = u.user_id
                JOIN "Role" r ON r.role_id = ur.role_id
                WHERE u.status = 'ACTIVE' AND r.role_name IN ('FINANCE_OFFICER', 'ADMIN')
                ORDER BY CASE WHEN r.role_name = 'FINANCE_OFFICER' THEN 0 ELSE 1 END, u.created_at ASC
                LIMIT 1
                """, UUID.class);
        if (!financeOrAdmin.isEmpty()) {
            return financeOrAdmin.getFirst();
        }
        List<UUID> anyUser = jdbc.queryForList("""
                SELECT user_id FROM "User" WHERE status = 'ACTIVE' ORDER BY created_at ASC LIMIT 1
                """, UUID.class);
        return anyUser.isEmpty() ? null : anyUser.getFirst();
    }

    private static RateView toRateView(LocalDate effectiveOn, BigDecimal vndPerUsd, String source, Instant publishedAt) {
        boolean isAuto = source != null && source.contains("Tự động");
        return new RateView(effectiveOn, vndPerUsd, source, publishedAt, isAuto);
    }
}
