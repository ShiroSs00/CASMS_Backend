package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin phiên bản Trip mẫu đủ điều kiện xuất bản (PUBLISHED) để tạo Cruise Draft (C01)")
public class EligibleTripSourceResponse {

    @Schema(description = "ID của phiên bản mẫu lịch trình", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID versionId;

    @Schema(description = "Số thứ tự phiên bản", example = "1")
    private Integer versionNumber;

    @Schema(description = "Revision hiện tại của phiên bản", example = "3")
    private Long revision;

    @Schema(description = "Thời điểm phiên bản được phê duyệt xuất bản")
    private Instant publishedAt;

    @Schema(description = "ID của Trip nguồn", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID tripId;

    @Schema(description = "Mã Trip nguồn", example = "VN-DOM-01")
    private String tripCode;

    @Schema(description = "Tên Trip nguồn", example = "Hành trình Xuyên Việt 3N2Đ")
    private String tripName;

    @Schema(description = "Thời lượng số ngày", example = "3")
    private Integer durationDays;

    @Schema(description = "Thời lượng số đêm", example = "2")
    private Integer durationNights;

    @Schema(description = "Phạm vi địa lý đã định (DOMESTIC, INTERNATIONAL)", example = "DOMESTIC")
    private String plannedScope;

    @Schema(description = "Danh sách mã quốc gia được phép của Trip", example = "[\"VN\"]")
    private List<String> countryCodes;

    @Schema(description = "Danh sách miền nội địa đăng ký (nếu là DOMESTIC)", example = "[\"BAC_BO\", \"TRUNG_BO\"]")
    private List<String> domesticRegions;

    @Schema(description = "ID cảng khởi hành xuất phát đầu tuyến")
    private UUID departurePortId;

    @Schema(description = "Tên cảng khởi hành xuất phát đầu tuyến", example = "Cảng Quốc tế Hạ Long")
    private String departurePortName;

    @Schema(description = "Mã cảng khởi hành", example = "VNHLG")
    private String departurePortCode;

    @Schema(description = "ID cảng đích kết thúc tuyến")
    private UUID returnPortId;

    @Schema(description = "Tên cảng đích kết thúc tuyến", example = "Cảng Sài Gòn")
    private String returnPortName;

    @Schema(description = "Mã cảng đích", example = "VNSGN")
    private String returnPortCode;

    @Schema(description = "Tổng số điểm dừng cảng trong hải trình", example = "3")
    private int totalStops;

    @Schema(description = "Tóm tắt thứ tự các cảng ghé", example = "[\"Hạ Long (VNHLG)\", \"Đà Nẵng (VNDAD)\", \"Nha Trang (VNNHA)\"]")
    private List<String> stopsSummary;
}
