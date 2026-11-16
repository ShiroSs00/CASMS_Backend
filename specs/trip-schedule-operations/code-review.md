# Đối chiếu mã nguồn BE-09 đến BE-20

Đối chiếu [đặc tả](spec.md) với `origin/main` tại `a37c4d5`. Bằng chứng là mã nguồn và mã kiểm thử hiện có.

## Lịch trình, cảng và đặt dịch vụ

| Hạng mục | Đã có | Cần bổ sung |
| --- | --- | --- |
| BE-09 · AC-09 | `TripPlanStop` lưu số ngày tương đối và giờ địa phương. `ScheduleMaterializationService` chiếu bản mẫu thành timestamp, tạo itinerary riêng, lưu nguồn và chỉnh các mốc theo độ lệch giờ đi. | Thêm khung khởi hành sớm/muộn cho mẫu đã được version hóa. Áp dụng một phép kiểm tra chung cho preview, tìm tàu, tạo chuyến và đổi lịch. Khi đổi giờ, rà các mốc và booking bị ảnh hưởng. Kiểm thử hai chuyến cùng một bản mẫu và hai biên của khung. |
| BE-10 · AC-10 | `TripService` kiểm tra số ngày/đêm nhập. `ScheduleMaterializationService` kiểm tra thứ tự cảng, giao nhau và ngày trả khách dự kiến, sau đó trả nhãn từ mẫu. | Tính giờ trôi qua bằng timestamp và tính riêng số ngày lịch, số lần qua nửa đêm theo múi giờ cảng khởi hành. Đối chiếu hành trình đã chiếu với nhãn mẫu. Mốc phụ thuộc chặng tối thiểu cần dữ liệu tuyến đã duyệt. |
| BE-11 · AC-11 | `CruiseStop` đã lưu giờ dự kiến/thực tế bằng `Instant`. `CruiseStopResponse` đã trả múi giờ cảng. `PortTimeZoneHelper.strictLocalInstant` phát hiện giờ DST thiếu hoặc mơ hồ. | Tái sử dụng các trường và helper. Rà các nhánh vẫn neo ngày đầu bằng múi giờ tàu mặc định, các đường ghi giờ thực tế và thứ tự đến/rời cảng. Kiểm tra cả dự kiến và thực tế đi qua nửa đêm, lệch múi giờ và DST. |
| BE-12 · AC-12a/b | `ShoreExcursionService.validateSchedule` kiểm tra giờ đến, hạn lên tàu, tender và mốc bắt đầu trả khách. `CruiseStopService.validateExcursionsUnderStop` rà tour khi đổi giờ cảng, hiện dùng giờ đến và hạn lên tàu/rời cảng. `ItineraryProgramService` có đường lập lịch riêng cho hoạt động và tour. | Dùng chung quy tắc cửa sổ cho chiếu lịch, tạo/dời hoạt động, tạo/dời tour và đổi mốc cảng/trả khách. Cảng cuối neo vào giờ kết thúc chuyến. Cảng giữa neo vào cửa sổ ghé cảng và hạn quay lại. Chuẩn hóa hai biên đang khác giữa luồng tạo và cập nhật tour. Bổ sung ngưỡng còn lại, thời lượng trên 60 phút và khoảng đệm theo đặc tả đã duyệt. |
| BE-12 · AC-12c/d | `CruiseStopService` đã ghi thời gian cập/rời thực tế. `ServiceSchedule` có thời gian bắt đầu/kết thúc. `ServiceRequestService` dùng kiểm tra lịch, điều kiện trên tàu và quota, được gọi từ API yêu cầu dịch vụ và POS. `ActivityService.register` là đường đăng ký hoạt động riêng. | Tạo quy tắc trạng thái theo đồng hồ và sự kiện cảng. Khóa đặt mới cả khi chờ xác nhận; chỉ mở lại khi xác nhận rời. Tạo cảnh báo OPS có chống gửi trùng. Tách kiểm tra **giờ của lượt sử dụng** và **quyền nhận đặt ngay lúc gọi API**. Thêm nhóm giờ cấu hình được, xử lý qua nửa đêm và qua nhiều nhóm. Gắn kiểm tra vào luồng đặt mới của API dịch vụ, POS và đăng ký hoạt động trên tàu nếu thuộc phạm vi. Rà cả đường đặt trước theo gói trước khi chọn điểm chặn. |

Mã nguồn để rà sâu:

- `ship/service/ScheduleMaterializationService.java` dòng 75, 135, 676, 729, 781, 948, 1015, 1184 và 1292. `ship/util/PortTimeZoneHelper.java` dòng 299.
- `activity/service/ShoreExcursionService.java` dòng 311 và `ship/service/CruiseStopService.java` dòng 485, 523, 531 và 699. `ship/service/ItineraryProgramService.java` dòng 111 và 132.
- `servicepackage/service/ServiceRequestService.java` dòng 73, 159 và 257. `servicepackage/controller/ServicepackageController.java` dòng 672. `pos/PosService.java` dòng 324. `activity/service/ActivityService.java` dòng 500.

## Số khách và gán tàu

| Hạng mục | Đã có | Cần bổ sung |
| --- | --- | --- |
| BE-13 · AC-13 | `CruiseCreateRequest` có `expectedPassengerCount`. V115 lưu giá trị tùy chọn có ràng buộc dương. `CruiseService.create` chỉ bắt buộc số khách với payload v3. Khai báo số khách hiện kéo theo nguồn lịch và tàu. | Bắt buộc số nguyên dương cho tất cả chuyến thực tế mới. Hỗ trợ `expectedPassengers` và hợp đồng cũ theo đặc tả. Giữ khả năng đọc dữ liệu lịch sử thiếu giá trị. Tách kiểm tra số khách khỏi quyết định có bắt buộc chọn tàu ngay hay không. |
| BE-14 · AC-14 | `POST /cruise/eligible-ships` và alias `/cruises/eligible-ships` đã chiếu khoảng thời gian, lọc READY/IN_SERVICE, sức chứa và trùng lịch. | Tái sử dụng API. Đồng bộ với điều kiện đủ cấu hình boong/cabin và các điều kiện vận hành dùng khi gán. Liên kết kết quả với thời điểm và phiên bản mẫu đã kiểm tra. |
| BE-15 · AC-15 | Tạo có tàu, gán, đổi tàu và luồng delay đã có các kiểm tra dưới khóa tàu ở nhiều nhánh. Đã có ca PostgreSQL cho hai thao tác gán trực tiếp đồng thời. | Thống nhất bộ kiểm tra giữ khóa tàu cho mọi đường ghi. Sửa cập nhật ngày của tàu đang gán và `/apply-change` đổi ngày trên cùng tàu. Bộ kiểm tra tác động hiện chỉ kiểm tra trùng lịch khi đổi sang tàu khác. Rà cả trường hợp chỉ đổi giờ về, hiện nhánh áp dụng thay đổi bỏ qua giá trị này. Thêm test đồng thời gán tàu và dời lịch qua hai API khác nhau. |

Mã nguồn để rà sâu:

- `ship/dto/request/CruiseCreateRequest.java` dòng 21. `ship/controller/CruiseController.java` dòng 75. `ship/repository/ShipRepository.java` dòng 41.
- `ship/service/CruiseService.java` dòng 159, 185, 304, 636, 660, 844, 944, 4144 và 4256. `ship/service/CruiseImpactAssessmentService.java` dòng 125. `ship/service/CruiseDelayService.java` dòng 94.
- `src/test/java/com/casms/ship/service/CruiseOperationalReadinessPostgresIntegrationTest.java` dòng 289 có sẵn bài kiểm tra khóa khi gán trực tiếp.

## Ngân sách, hồ sơ và duyệt vận hành

| Hạng mục | Đã có | Cần bổ sung |
| --- | --- | --- |
| BE-16 · AC-16 | CRM phụ trách nhập tổng VND trong DRAFT. Service khóa chuyến, so revision khi sửa. | Bổ sung quyết định duyệt/từ chối, phương thức mở lại chỉnh sửa và nộp revision mới. Ghi lịch sử số tiền, lý do và người quyết định. Giữ một tổng VND nếu scope đã duyệt không yêu cầu theo khoản. |
| BE-17 · AC-17 | Handoff đã tách ASM và SHIP, một bản ghi theo chuyến và nhóm. Đã kiểm tra trạng thái/revision khi phản hồi. | Quy định ánh xạ yêu cầu sang bộ phận, hồ sơ và người phụ trách. Giữ lịch sử mỗi lần giao, trả và giao lại. Cập nhật một nhóm có kiểm thử độc lập với nhóm còn lại. |
| BE-18 · AC-18 | Bốn phần chuẩn bị có vai trò khác nhau. `submitSection` vẫn giới hạn CRM ở controller, interceptor và service. | Kiểm tra vai trò và người được phân công theo từng phần tại cả ba tầng, chặn nộp ngoài phạm vi chuyến. Rà đường nộp hàng loạt và mutation guard để các bộ phận có thể nộp độc lập. |
| BE-19 · AC-19 | OPS đã xác nhận và trả bổ sung có lý do, giữ khóa, so revision và chặn tự duyệt. | Thêm quyết định từ chối riêng theo nghiệp vụ và quy tắc nộp lại hồ sơ bộ phận. Lưu revision mà OPS đã đọc. Cần người duyệt khác nếu OPS cũng là người nộp phần SAFETY_CREW. |
| BE-20 · AC-20 | Event đã lưu snapshot nội bộ và revision, API trả metadata. Invalidation chủ yếu đổi CONFIRMED sang RETURNED, có đường tác động tất cả phần và có thể bỏ ghi event khi thiếu actor. | Trả lịch sử trạng thái trước/sau, revision nguồn/đích, mốc và tác nhân. Đánh dấu lỗi thời cho cả hồ sơ SUBMITTED và CONFIRMED bị ảnh hưởng. Xác định dependency theo phần. Kiểm thử cập nhật/duyệt đồng thời và kiểm tra lịch sử sau mỗi quyết định. |

Mã nguồn để rà sâu:

- `ship/service/CruisePreparationService.java` dòng 103, 166, 280, 530, 620, 693, 767 và 788.
- `ship/service/CruiseWorkHandoffService.java` dòng 21 và 58. `ship/controller/CruisePreparationController.java` dòng 106.
- `ship/service/CruisePlanOperationsAccess.java` dòng 187 và `ship/service/CruisePlanMutationGuard.java` dòng 106.

Các đường dẫn mã nguồn rút gọn ở trên tương đối với `src/main/java/com/casms/` trong checkout `.codex-ship-types-main`.

## Trình tự triển khai đề xuất

1. Chốt hợp đồng thời gian rồi triển khai phép chiếu lịch và số ngày/đêm. API tìm và gán tàu cùng dùng khoảng giờ từ phép chiếu đó.
2. Dùng chung kiểm tra sức chứa và lịch dưới khóa tàu, ưu tiên chặn đường đổi ngày trên cùng tàu ở `/apply-change`.
3. Áp dụng cửa sổ tạo hoạt động/tour và cổng nhận đặt dịch vụ cùng trạng thái cảng. Chốt khoảng đệm giữa chuyến, thời điểm/kênh cảnh báo và phạm vi các đường đặt trước.
4. Hoàn thiện ngân sách, quyền nộp độc lập, ba quyết định OPS và audit. Chốt danh sách bộ phận/người nộp, cách phân biệt từ chối với trả bổ sung và quyền nộp lại từng loại hồ sơ.

Đợt rà soát chỉ đọc mã. Chưa thay đổi backend, chưa chạy kiểm thử hoặc xác minh PostgreSQL.
