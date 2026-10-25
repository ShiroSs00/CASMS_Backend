package com.casms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;
import java.util.List;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().replaceWithSchema(
                LocalTime.class,
                new StringSchema()
                        .type("string")
                        .format("time")
                        .example("14:30:00")
                        .pattern("^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$")
        );
    }

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("CASMS - Cruise Administration & Management System API")
                        .description("Tài liệu API cho Hệ thống Quản trị & Điều hành Du thuyền CASMS (Modular Monolith).\n\n"
                                + "### Hướng dẫn xác thực:\n"
                                + "- Nhấn nút **Authorize** bên phải và nhập token JWT.\n"
                                + "- Định dạng: `Bearer <token>` hoặc chỉ `<token>`.\n"
                                + "- Sau khi authorize, các API bảo mật sẽ tự động đính kèm header `Authorization: Bearer <token>`.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CASMS Development Team")
                                .email("contact@casms.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url(contextPath).description("Local Development Server (Context Path)")
                ))
                .tags(List.of(
                        new Tag().name("Cruise Management").description("APIs quản lý kế hoạch và vòng đời chuyến đi du thuyền (Tạo, gán tàu, duyệt, phát hành, vận hành, hủy)"),
                        new Tag().name("Cruise Regulation Management").description("APIs quản lý và áp dụng quy định vận hành cho chuyến đi du thuyền"),
                        new Tag().name("Cruise Stop Management").description("APIs quản lý điểm dừng trong lịch trình chuyến đi"),
                        new Tag().name("Ship Management").description("APIs quản lý tàu (Tạo mới, cập nhật, đổi trạng thái, kiểm tra lịch trình, sức chứa)"),
                        new Tag().name("Ship Regulation Management").description("APIs quản lý và áp dụng quy định vận hành cho tàu du lịch"),
                        new Tag().name("Deck Management").description("APIs quản lý tầng/boong tàu (Tạo mới, cập nhật, danh sách cabin, sức chứa boong)"),
                        new Tag().name("Cabin Management").description("APIs quản lý phòng/cabin (Tạo mới, cập nhật, tìm phòng trống, khóa/mở phòng, lịch sử gán)"),
                        new Tag().name("Port Management").description("APIs quản lý cảng biển (Tạo mới, cập nhật, kích hoạt, vô hiệu hóa, sẵn sàng vận hành)"),
                        new Tag().name("Destination Management").description("APIs quản lý điểm đến du lịch"),
                        new Tag().name("Itinerary Management").description("APIs quản lý lịch trình chuyến đi du thuyền"),
                        new Tag().name("Itinerary Day Management").description("APIs quản lý ngày trong lịch trình chuyến đi"),
                        new Tag().name("Itinerary Change History Management").description("APIs quản lý và tra cứu lịch sử thay đổi lịch trình chuyến đi"),
                        new Tag().name("Operational Regulation Management").description("APIs quản lý danh mục quy định vận hành và an toàn hàng hải"),
                        new Tag().name("Operational Issue Management").description("APIs quản lý, báo cáo và giải quyết sự cố vận hành"),
                        new Tag().name("Activities & Excursions").description("APIs hoạt động trên tàu và tham quan trên bờ"),
                        new Tag().name("Shore Excursion Management").description("APIs quản lý tour và hoạt động tham quan trên bờ cho hành khách"),
                        new Tag().name("Supplier Management").description("APIs quản lý nhà cung cấp dịch vụ và đối tác tour tham quan"),
                        new Tag().name("Tour Group Management").description("APIs quản lý đoàn/nhóm tham quan tour trên bờ, phân công hướng dẫn viên"),
                        new Tag().name("Tour Group Passenger Management").description("APIs quản lý phân bổ hành khách trong nhóm tour, điểm danh và điều chuyển nhóm"),
                        new Tag().name("Identity & Users").description("APIs quản lý tài khoản, người dùng và hành khách"),
                        new Tag().name("Booking & Contracts").description("APIs quản lý đặt chỗ và hợp đồng du thuyền"),
                        new Tag().name("Check-in & Boarding").description("APIs làm thủ tục lên tàu, thẻ định danh và trả phòng"),
                        new Tag().name("Services & Packages").description("APIs quản lý dịch vụ trên tàu và gói dịch vụ tour"),
                        new Tag().name("Finance & Payment").description("APIs quản lý tài khoản chi tiêu, giao dịch, hóa đơn và thanh toán"),
                        new Tag().name("Trip & Itinerary Planning").description("APIs lập kế hoạch hành trình chuẩn (Trip, Version, Day, Stop, Entry, Catalog)"),
                        new Tag().name("Notifications").description("APIs thông báo hệ thống")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập JWT Bearer token để xác thực các API bảo mật")));
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("1. Tất cả API (All)")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GroupedOpenApi shipAndCruiseApi() {
        return GroupedOpenApi.builder()
                .group("2. Đội tàu & Chuyến đi (Fleet & Cruise)")
                .pathsToMatch(
                        "/cruise/**", "/cruises/**",
                        "/cruise-stops/**", "/cruise-stop/**",
                        "/ship/**", "/ships/**",
                        "/deck/**", "/decks/**",
                        "/cabin/**", "/cabins/**",
                        "/ports/**", "/port/**",
                        "/destinations/**", "/destination/**",
                        "/itineraries/**", "/itinerary/**",
                        "/itinerary-days/**", "/itinerary-day/**",
                        "/itinerary-change-history/**",
                        "/operational-regulations/**",
                        "/operational-issues/**",
                        "/trips/**",
                        "/trip-schedule-versions/**",
                        "/trip-planning/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi bookingApi() {
        return GroupedOpenApi.builder()
                .group("3. Đặt chỗ & Hợp đồng (Booking & Contract)")
                .pathsToMatch("/booking/**")
                .build();
    }

    @Bean
    public GroupedOpenApi checkinApi() {
        return GroupedOpenApi.builder()
                .group("4. Check-in & Lên tàu (Check-in & Boarding)")
                .pathsToMatch("/checkin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi servicePackageApi() {
        return GroupedOpenApi.builder()
                .group("5. Dịch vụ & Gói tour (Services & Packages)")
                .pathsToMatch("/servicepackage/**")
                .build();
    }

    @Bean
    public GroupedOpenApi activityApi() {
        return GroupedOpenApi.builder()
                .group("6. Hoạt động & Tham quan (Activities & Excursions)")
                .pathsToMatch(
                        "/activity/**",
                        "/shore-excursions/**",
                        "/suppliers/**",
                        "/tour-groups/**",
                        "/tour-group-passengers/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi paymentApi() {
        return GroupedOpenApi.builder()
                .group("7. Tài chính & Thanh toán (Finance & Payment)")
                .pathsToMatch("/payment/**")
                .build();
    }

    @Bean
    public GroupedOpenApi identityApi() {
        return GroupedOpenApi.builder()
                .group("8. Tài khoản & Thông báo (Identity & Notifications)")
                .pathsToMatch("/identity/**", "/notification/**", "/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tripPlanningApi() {
        return GroupedOpenApi.builder()
                .group("9. Lập kế hoạch hành trình (Trip & Itinerary Planning)")
                .pathsToMatch(
                        "/trips/**",
                        "/trip-schedule-versions/**",
                        "/trip-planning/**"
                )
                .build();
    }
}
