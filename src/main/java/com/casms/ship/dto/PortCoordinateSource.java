package com.casms.ship.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Data;

import java.time.Instant;

/** DEC-PORT-02 locality provenance. The external ID is never the local Port UUID. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        description =
                "Nguồn tọa độ thành phố/đảo; thông tin cấu trúc không phải xác minh tính xác thực"
                    + " bên ngoài")
public class PortCoordinateSource {
    @NotBlank
    @Pattern(regexp = "OPEN_METEO_GEOCODING")
    private String provider;

    @NotBlank
    @Pattern(regexp = "GEONAMES")
    private String dataset;

    @NotNull
    @Positive
    @Max(9007199254740991L)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
            using = ExternalLocationIdDeserializer.class)
    private Long externalLocationId;

    @NotBlank private String locationName;

    @NotBlank
    @Pattern(regexp = "GEOCODED_LOCALITY")
    private String coordinateReferenceType;

    private String countryCode;
    private String featureCode;
    private String timezone;
    private String admin1;

    @Schema(
            accessMode = Schema.AccessMode.READ_ONLY,
            description = "Thời điểm do server ghi; giá trị client gửi không được sử dụng")
    private Instant recordedAt;

    /**
     * Reject fractional/string IDs rather than letting Jackson silently coerce them to integers.
     */
    public static class ExternalLocationIdDeserializer
            extends com.fasterxml.jackson.databind.JsonDeserializer<Long> {
        @Override
        public Long deserialize(
                com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context)
                throws java.io.IOException {
            if (parser.currentToken() != com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT) {
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            }
            return parser.getLongValue();
        }
    }
}
