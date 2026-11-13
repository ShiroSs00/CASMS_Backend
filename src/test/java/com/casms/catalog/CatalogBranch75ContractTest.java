package com.casms.catalog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogBranch75ContractTest {
    @Test void localeNormalizesAndRejectsInvalidValues() {
        assertEquals("vi",CatalogLocaleReadController.normalizeLocale(null));
        assertEquals("en-US",CatalogLocaleReadController.normalizeLocale("en-us"));
        assertEquals("en",CatalogLocaleReadController.normalizeLocale("en"));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogLocaleReadController.normalizeLocale("en-US<script>"));
    }
    @Test void quotaMustBeExplicitAndPositive() {
        assertTrue(PackageBenefitReadController.configuredQuota(1,"SERVICE_UNIT"));
        assertFalse(PackageBenefitReadController.configuredQuota(null,"SERVICE_UNIT"));
        assertFalse(PackageBenefitReadController.configuredQuota(0,"SERVICE_UNIT"));
        assertFalse(PackageBenefitReadController.configuredQuota(2,""));
    }
    @Test void verificationEvidenceMustBeHttpUrl() {
        assertTrue(OperationalTourReviewController.validEvidenceUrl("https://example.org/port"));
        assertFalse(OperationalTourReviewController.validEvidenceUrl("file:///etc/passwd"));
        assertFalse(OperationalTourReviewController.validEvidenceUrl("https://user:pass@example.org/doc"));
        assertFalse(OperationalTourReviewController.validEvidenceUrl("not a URL"));
    }
}
