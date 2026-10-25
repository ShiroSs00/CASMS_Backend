package com.casms.activity.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phân loại chi tiết các hoạt động trên tàu (Onboard Activity Categories)")
public enum OnboardActivityCategory {

    @Schema(description = "Giải trí & Nghệ thuật (Đại nhạc hội, biểu diễn ảo thuật, ban nhạc Jazz, tiệc DJ)")
    ENTERTAINMENT("Giải trí & Nghệ thuật", "🎭"),

    @Schema(description = "Thể thao & Sức khỏe (Yoga bình minh, Lotus Spa, Gym & Fitness, thể thao boong)")
    SPORTS_WELLNESS("Thể thao & Sức khỏe", "🧘"),

    @Schema(description = "Gia đình & Trẻ em (Kids Club, Đấu trường VR công nghệ, tìm kho báu, rạp phim 3D)")
    FAMILY_KIDS("Gia đình & Trẻ em", "👨‍👩‍👧"),

    @Schema(description = "Sự kiện đặc biệt & Lễ hội (Gala Thuyền trưởng, White Night Party, Countdown, Dạ vũ)")
    SPECIAL_EVENTS("Sự kiện đặc biệt", "🥂"),

    @Schema(description = "Trải nghiệm & Lớp học kỹ năng (Lớp làm bánh Pháp, pha chế cocktail, khiêu vũ)")
    WORKSHOPS_CLASSES("Trải nghiệm & Lớp học", "🎨");

    private final String displayName;
    private final String icon;

    OnboardActivityCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
