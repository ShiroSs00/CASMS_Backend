package com.casms.trip.dto.response;

import com.casms.activity.enums.OnboardActivityCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin danh mục phân loại hoạt động trên tàu phục vụ render tabs / bộ lọc trên Frontend")
public class ActivityCategoryResponse {

    @Schema(description = "Mã danh mục", example = "ENTERTAINMENT")
    private OnboardActivityCategory code;

    @Schema(description = "Tên hiển thị tiếng Việt", example = "Giải trí & Nghệ thuật")
    private String name;

    @Schema(description = "Biểu tượng gợi ý", example = "🎭")
    private String icon;

    @Schema(description = "Mô tả danh mục", example = "Đại nhạc hội, biểu diễn ảo thuật, ban nhạc Jazz, tiệc DJ")
    private String description;
}
