package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Yêu cầu cập nhật hồ sơ và trạng thái phối hợp cảng (BE-20)")
public class CruisePortCoordinationRequest {

    @Schema(description = "Tên đầu mối cảng / Port Agent", example = "Nguyễn Văn Hùng")
    @Size(max = 200, message = "Tên đầu mối không vượt quá 200 ký tự")
    private String portContactName;

    @Schema(description = "Vai trò đầu mối phối hợp", example = "Đại diện Cảng vụ / Port Agent")
    @Size(max = 100, message = "Vai trò không vượt quá 100 ký tự")
    private String portContactRole;

    @Schema(description = "Số điện thoại liên hệ", example = "+84-90-1234567")
    @Size(max = 50, message = "Số điện thoại không vượt quá 50 ký tự")
    private String portContactPhone;

    @Schema(description = "Email liên hệ", example = "agent@halongport.vn")
    @Size(max = 255, message = "Email không vượt quá 255 ký tự")
    private String portContactEmail;

    @Schema(description = "Phạm vi trách nhiệm phối hợp", example = "Làm thủ tục cập bến, hoa tiêu và sắp xếp cầu cảng")
    @Size(max = 500, message = "Trách nhiệm không vượt quá 500 ký tự")
    private String portContactResponsibility;

    @Schema(description = "Trạng thái phối hợp cảng: NOT_STARTED, SUBMITTED, IN_COORDINATION, CONFIRMED, REJECTED", example = "CONFIRMED")
    private String coordinationStatus;

    @Schema(description = "Hạn chót hoàn tất phối hợp cảng (ISO-8601)", example = "2026-01-14T12:00:00Z")
    private Instant coordinationDeadline;

    @Schema(description = "Mã hồ sơ phối hợp cảng", example = "DOS-HPH-2026-001")
    @Size(max = 100, message = "Mã hồ sơ không vượt quá 100 ký tự")
    private String dossierReference;

    @Schema(description = "Ghi chú điều phối cảng", example = "Đã gửi danh sách thuyền viên và chứng thư an toàn")
    @Size(max = 1000, message = "Ghi chú không vượt quá 1000 ký tự")
    private String notes;
}
