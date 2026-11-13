-- Opt-in catalogue; values and prices are fictional planning references, not approved offers.
-- Run V110 and V111 through Flyway first. No production deployment runs this seed automatically.
BEGIN;
INSERT INTO "ServiceCategory"(category_code,category_name)
VALUES
('VN-CAT-01','Nhà hàng'),
('VN-CAT-02','Đồ uống'),
('VN-CAT-03','Spa và chăm sóc sức khỏe'),
('VN-CAT-04','Buồng phòng'),
('VN-CAT-05','Internet'),
('VN-CAT-06','Giải trí'),
('VN-CAT-07','Nhiếp ảnh'),
('VN-CAT-08','Giặt ủi'),
('VN-CAT-09','Dịch vụ khách hàng'),
('VN-CAT-10','Trẻ em và gia đình'),
('VN-CAT-11','Thể thao'),
('VN-CAT-12','Tour bờ')
ON CONFLICT(category_code) DO NOTHING;

INSERT INTO "Service"(service_id,service_code,service_name,description,service_type,service_category_id,
price,currency_code,status,requires_staff,created_at,updated_at)
SELECT gen_random_uuid(),v.code,v.name,'Dữ liệu tham khảo - cần phê duyệt trước khi bán', v.category,
sc.service_category_id,v.price::numeric,'VND','INACTIVE'::"Service_status",v.staff::boolean,now(),now()
FROM (VALUES
('VN-SVC-001','Buffet sáng','VN-CAT-01','0',true),
('VN-SVC-002','Buffet trưa','VN-CAT-01','0',true),
('VN-SVC-003','Set menu tối','VN-CAT-01','0',true),
('VN-SVC-004','Bữa tối hải sản','VN-CAT-01','650000',true),
('VN-SVC-005','Nhà hàng gọi món','VN-CAT-01','450000',true),
('VN-SVC-006','Cà phê pha máy','VN-CAT-02','65000',true),
('VN-SVC-007','Trà chiều','VN-CAT-02','120000',true),
('VN-SVC-008','Nước ép trái cây','VN-CAT-02','95000',true),
('VN-SVC-009','Mocktail','VN-CAT-02','115000',true),
('VN-SVC-010','Phục vụ đồ uống tại bàn','VN-CAT-02','35000',true),
('VN-SVC-011','Massage thư giãn','VN-CAT-03','750000',true),
('VN-SVC-012','Massage đá nóng','VN-CAT-03','980000',true),
('VN-SVC-013','Chăm sóc da mặt','VN-CAT-03','640000',true),
('VN-SVC-014','Xông hơi','VN-CAT-03','280000',true),
('VN-SVC-015','Chăm sóc chân','VN-CAT-03','360000',true),
('VN-SVC-016','Dọn phòng hằng ngày','VN-CAT-04','0',true),
('VN-SVC-017','Dịch vụ phòng buổi tối','VN-CAT-04','0',true),
('VN-SVC-018','Trang trí sinh nhật','VN-CAT-04','550000',true),
('VN-SVC-019','Chuẩn bị phòng tuần trăng mật','VN-CAT-04','950000',true),
('VN-SVC-020','Phục vụ bữa sáng tại phòng','VN-CAT-04','180000',true),
('VN-SVC-021','Wi-Fi nhắn tin','VN-CAT-05','150000',false),
('VN-SVC-022','Wi-Fi tiêu chuẩn','VN-CAT-05','280000',false),
('VN-SVC-023','Wi-Fi tốc độ cao','VN-CAT-05','450000',false),
('VN-SVC-024','Hỗ trợ kết nối thiết bị','VN-CAT-05','0',false),
('VN-SVC-025','Gói Internet gia đình','VN-CAT-05','650000',false),
('VN-SVC-026','Vé chương trình biểu diễn','VN-CAT-06','0',true),
('VN-SVC-027','Karaoke theo giờ','VN-CAT-06','250000',true),
('VN-SVC-028','Phòng chiếu phim','VN-CAT-06','0',true),
('VN-SVC-029','Workshop nấu ăn','VN-CAT-06','380000',true),
('VN-SVC-030','Đặt chỗ sự kiện','VN-CAT-06','0',true),
('VN-SVC-031','Chụp ảnh chân dung','VN-CAT-07','300000',true),
('VN-SVC-032','Chụp ảnh gia đình','VN-CAT-07','550000',true),
('VN-SVC-033','Album ảnh số','VN-CAT-07','390000',true),
('VN-SVC-034','In ảnh lưu niệm','VN-CAT-07','75000',true),
('VN-SVC-035','Chụp ảnh sự kiện','VN-CAT-07','650000',true),
('VN-SVC-036','Giặt đồ thường','VN-CAT-08','120000',true),
('VN-SVC-037','Giặt nhanh','VN-CAT-08','180000',true),
('VN-SVC-038','Ủi quần áo','VN-CAT-08','60000',true),
('VN-SVC-039','Giặt hấp','VN-CAT-08','220000',true),
('VN-SVC-040','Gói giặt theo túi','VN-CAT-08','250000',true),
('VN-SVC-041','Hỗ trợ hành lý','VN-CAT-09','0',true),
('VN-SVC-042','Hỗ trợ yêu cầu đặc biệt','VN-CAT-09','0',true),
('VN-SVC-043','Dịch vụ lễ tân','VN-CAT-09','0',true),
('VN-SVC-044','Hỗ trợ đặt dịch vụ','VN-CAT-09','0',true),
('VN-SVC-045','Hỗ trợ khách đoàn','VN-CAT-09','0',true),
('VN-SVC-046','Câu lạc bộ trẻ em','VN-CAT-10','0',true),
('VN-SVC-047','Hoạt động gia đình','VN-CAT-10','0',true),
('VN-SVC-048','Trông trẻ theo giờ','VN-CAT-10','350000',true),
('VN-SVC-049','Bộ trò chơi gia đình','VN-CAT-10','120000',true),
('VN-SVC-050','Workshop thủ công','VN-CAT-10','180000',true),
('VN-SVC-051','Lớp yoga','VN-CAT-11','120000',true),
('VN-SVC-052','Huấn luyện thể hình','VN-CAT-11','350000',true),
('VN-SVC-053','Giãn cơ buổi sáng','VN-CAT-11','0',true),
('VN-SVC-054','Thi đấu thể thao nhẹ','VN-CAT-11','0',true),
('VN-SVC-055','Hướng dẫn thiền','VN-CAT-11','160000',true),
('VN-SVC-056','Hỗ trợ đặt tour bờ','VN-CAT-12','0',true),
('VN-SVC-057','Hướng dẫn tập trung tour','VN-CAT-12','0',true),
('VN-SVC-058','Hỗ trợ lên xe tour','VN-CAT-12','0',true),
('VN-SVC-059','Dịch vụ phiên dịch tour','VN-CAT-12','250000',true),
('VN-SVC-060','Hỗ trợ khách có nhu cầu đặc biệt','VN-CAT-12','0',true)
) v(code,name,category,price,staff)
JOIN "ServiceCategory" sc ON sc.category_code=v.category
ON CONFLICT(service_code) DO NOTHING;

INSERT INTO "OnboardActivity"(onboard_activity_id,activity_code,activity_name,category,description,capacity,price,currency_code,status,catalog_entry,created_at,updated_at)
SELECT gen_random_uuid(),code,name,category,'Mẫu chương trình - chưa lên lịch',capacity::integer,0,'VND','PLANNED'::"OnboardActivity_status",true,now(),now()
FROM (VALUES
('VN-ACT-001','Chào đón hành khách','SPECIAL_EVENTS','45'),
('VN-ACT-002','Hướng dẫn an toàn','SPECIAL_EVENTS','60'),
('VN-ACT-003','Yoga bình minh','SPORTS_WELLNESS','75'),
('VN-ACT-004','Thưởng trà Việt','ENTERTAINMENT','90'),
('VN-ACT-005','Workshop nấu món Việt','ENTERTAINMENT','30'),
('VN-ACT-006','Biểu diễn nhạc acoustic','ENTERTAINMENT','45'),
('VN-ACT-007','Đố vui văn hóa Việt Nam','ENTERTAINMENT','60'),
('VN-ACT-008','Thiền thư giãn','SPORTS_WELLNESS','75'),
('VN-ACT-009','Trò chơi gia đình','FAMILY_KIDS','90'),
('VN-ACT-010','Lớp gấp giấy','FAMILY_KIDS','30'),
('VN-ACT-011','Lớp làm bánh','FAMILY_KIDS','45'),
('VN-ACT-012','Giao lưu cùng thủy thủ đoàn','ENTERTAINMENT','60'),
('VN-ACT-013','Kể chuyện biển đảo','ENTERTAINMENT','75'),
('VN-ACT-014','Chiếu phim gia đình','FAMILY_KIDS','90'),
('VN-ACT-015','Biểu diễn nhạc dân tộc','ENTERTAINMENT','30'),
('VN-ACT-016','Hướng dẫn chụp ảnh','ENTERTAINMENT','45'),
('VN-ACT-017','Đêm nhạc trữ tình','ENTERTAINMENT','60'),
('VN-ACT-018','Hội thi kiến thức biển','ENTERTAINMENT','75'),
('VN-ACT-019','Trò chơi tìm kho báu','FAMILY_KIDS','90'),
('VN-ACT-020','Lớp khiêu vũ','ENTERTAINMENT','30'),
('VN-ACT-021','Thể dục buổi sáng','SPORTS_WELLNESS','45'),
('VN-ACT-022','Vẽ tranh phong cảnh','FAMILY_KIDS','60'),
('VN-ACT-023','Workshop pha mocktail','ENTERTAINMENT','75'),
('VN-ACT-024','Gala chào mừng','SPECIAL_EVENTS','90'),
('VN-ACT-025','Chương trình thiếu nhi','FAMILY_KIDS','30'),
('VN-ACT-026','Hội thi nấu ăn','ENTERTAINMENT','45'),
('VN-ACT-027','Thưởng thức âm nhạc chiều','ENTERTAINMENT','60'),
('VN-ACT-028','Hướng dẫn bảo vệ môi trường biển','ENTERTAINMENT','75'),
('VN-ACT-029','Hướng dẫn sơ cứu cơ bản','SPORTS_WELLNESS','90'),
('VN-ACT-030','Ngày hội thể thao','SPORTS_WELLNESS','30'),
('VN-ACT-031','Đêm giao lưu văn hóa','SPECIAL_EVENTS','45'),
('VN-ACT-032','Chương trình nghệ thuật dân gian','ENTERTAINMENT','60'),
('VN-ACT-033','Lớp làm đồ thủ công','FAMILY_KIDS','75'),
('VN-ACT-034','Buổi chia sẻ hành trình','ENTERTAINMENT','90'),
('VN-ACT-035','Tiệc chia tay','SPECIAL_EVENTS','30')
) v(code,name,category,capacity)
ON CONFLICT(activity_code) DO NOTHING;

INSERT INTO "TourPackage"(tour_package_id,package_code,package_name,description,package_type,price,currency_code,status,created_at,updated_at)
SELECT gen_random_uuid(),code,name,'Gói tham khảo - chưa được phép mở bán',type::"TourPackage_package_type",price::numeric,'VND','DRAFT'::"TourPackage_status",now(),now()
FROM (VALUES
('VN-PKG-001','Hành trình - Cơ bản','BASIC','390000'),
('VN-PKG-002','Hành trình - Nâng cao','PLUS','790000'),
('VN-PKG-003','Hành trình - Mở rộng','ADVANCED','1390000'),
('VN-PKG-004','Hành trình - Cao cấp','PREMIUM','2490000'),
('VN-PKG-005','Hành trình - Đặc biệt','PREMIUM_PLUS','3590000'),
('VN-PKG-006','Hành trình - Mua thêm','ADD_ON','590000'),
('VN-PKG-007','Gia đình - Cơ bản','BASIC','540000'),
('VN-PKG-008','Gia đình - Nâng cao','PLUS','940000'),
('VN-PKG-009','Gia đình - Mở rộng','ADVANCED','1540000'),
('VN-PKG-010','Gia đình - Cao cấp','PREMIUM','2640000'),
('VN-PKG-011','Gia đình - Đặc biệt','PREMIUM_PLUS','3740000'),
('VN-PKG-012','Gia đình - Mua thêm','ADD_ON','740000'),
('VN-PKG-013','Nghỉ dưỡng - Cơ bản','BASIC','690000'),
('VN-PKG-014','Nghỉ dưỡng - Nâng cao','PLUS','1090000'),
('VN-PKG-015','Nghỉ dưỡng - Mở rộng','ADVANCED','1690000'),
('VN-PKG-016','Nghỉ dưỡng - Cao cấp','PREMIUM','2790000'),
('VN-PKG-017','Nghỉ dưỡng - Đặc biệt','PREMIUM_PLUS','3890000'),
('VN-PKG-018','Nghỉ dưỡng - Mua thêm','ADD_ON','890000'),
('VN-PKG-019','Khám phá - Cơ bản','BASIC','840000'),
('VN-PKG-020','Khám phá - Nâng cao','PLUS','1240000'),
('VN-PKG-021','Khám phá - Mở rộng','ADVANCED','1840000'),
('VN-PKG-022','Khám phá - Cao cấp','PREMIUM','2940000'),
('VN-PKG-023','Khám phá - Đặc biệt','PREMIUM_PLUS','4040000'),
('VN-PKG-024','Khám phá - Mua thêm','ADD_ON','1040000')
) v(code,name,type,price)
ON CONFLICT(package_code) DO NOTHING;

INSERT INTO "CommercialPolicyCatalog"(policy_code,policy_name,policy_group,description) VALUES
('VN-POL-001','Điều kiện giữ chỗ','Đặt chỗ','Giữ chỗ chỉ được xác nhận khi còn năng lực phục vụ và thanh toán theo điều kiện của đợt bán. Thời hạn giữ chỗ phải được cấu hình và được khách đồng ý.'),
('VN-POL-002','Xác nhận đặt chỗ điện tử','Đặt chỗ','Xác nhận nêu rõ hành trình, mã đặt chỗ, khách đi cùng, loại phòng, quyền lợi, số tiền, trạng thái và liên hệ hỗ trợ.'),
('VN-POL-003','Thay đổi thông tin hành khách','Đặt chỗ','Mọi thay đổi họ tên và giấy tờ sau xác nhận phải tuân thủ thời điểm khóa danh sách, điều kiện vé và quy trình kiểm soát danh tính.'),
('VN-POL-004','Đặt cọc khi đặt chỗ','Thanh toán','Khoản đặt cọc và mốc thanh toán tùy từng đợt bán; hệ thống phải lưu snapshot điều kiện được khách chấp nhận trước khi thu tiền.'),
('VN-POL-005','Thanh toán số dư','Thanh toán','Khoản còn lại phải hoàn tất theo thời hạn được nêu trên hợp đồng và thông báo trước hạn; không tự động đặt một mốc chung cho mọi chuyến.'),
('VN-POL-006','Biên nhận và đối soát','Thanh toán','Mọi giao dịch phải có mã tham chiếu, trạng thái, nguồn và lịch sử đối soát; hoàn trả cần liên kết giao dịch gốc.'),
('VN-POL-007','Hủy tự nguyện trước chuyến','Hủy và hoàn tiền','Điều kiện hủy phụ thuộc loại vé, thời điểm yêu cầu và hợp đồng; các mức khấu trừ phải được phê duyệt trước khi công bố.'),
('VN-POL-008','Đổi hành trình hoặc ngày đi','Hủy và hoàn tiền','Chỉ thực hiện khi chuyến thay thế còn chỗ và giá; phải hiển thị phần chênh lệch và lấy xác nhận của khách.'),
('VN-POL-009','Dịch vụ bị ngừng cung cấp','Hủy và hoàn tiền','Thông báo thay thế hoặc hoàn tiền theo quyền lợi đã mua, lưu nguyên nhân và phê duyệt của bộ phận có thẩm quyền.'),
('VN-POL-010','Trẻ em và người giám hộ','Hành khách','Trẻ vị thành niên phải đi cùng người giám hộ phù hợp; áp dụng giới hạn độ tuổi theo từng hoạt động và nhà cung cấp.'),
('VN-POL-011','Hành lý và vật dụng hạn chế','Hành khách','Hạn mức hành lý, vật dụng cấm và hàng dễ cháy được công bố trong thông tin trước chuyến; xác nhận quy chuẩn riêng của tàu.'),
('VN-POL-012','Hỗ trợ khả năng tiếp cận','Hành khách','Ghi nhận nhu cầu hỗ trợ trước chuyến, đánh giá khả năng bố trí, giao đầu mối theo dõi và không lưu thông tin y tế quá mức cần thiết.'),
('VN-POL-013','Đặt trước và giữ suất','Dịch vụ','Các dịch vụ giới hạn chỗ phải có lịch, sức chứa, giờ khóa đặt và xác nhận; không vượt khả năng cung cấp.'),
('VN-POL-014','Không có mặt tại giờ hẹn','Dịch vụ','Quy trình no-show và khả năng đổi khung giờ cần dựa trên điều khoản đã công bố, lưu lịch sử thông báo cho khách.'),
('VN-POL-015','Quyền lợi kèm gói','Dịch vụ','Mỗi quyền lợi có mã dịch vụ, hạn mức, thời hạn và đơn vị sử dụng; hệ thống phải chống sử dụng vượt định mức.'),
('VN-POL-016','Sức chứa và đăng ký','Hoạt động','Không mở thêm suất vượt sức chứa được quản lý tàu phê duyệt; danh sách chờ được xử lý theo thứ tự quy định.'),
('VN-POL-017','Hoãn vì thời tiết','Hoạt động','Hoạt động ngoài trời phải tạm ngừng khi điều kiện không an toàn; thông báo lựa chọn thay thế hoặc hoàn phí theo hợp đồng.'),
('VN-POL-018','Hướng dẫn và giám sát trẻ em','Hoạt động','Hoạt động có trẻ em phải có người phụ trách đủ điều kiện, giới hạn độ tuổi và quy trình bàn giao với người giám hộ.'),
('VN-POL-019','Điểm tập trung và điểm danh','Tour bờ','Phiếu tour xác nhận địa điểm, giờ tập trung, hướng dẫn viên và số điện thoại tổng đài do nhà cung cấp cung cấp chính thức.'),
('VN-POL-020','Thời hạn quay về tàu','Tour bờ','Lịch tour phải bố trí khoảng đệm an toàn trước thời hạn tất cả hành khách phải lên tàu và có quy trình xử lý trễ.'),
('VN-POL-021','Hủy tour do điều kiện cảng','Tour bờ','Đơn vị điều hành phối hợp với nhà cung cấp khi tàu đổi cảng hoặc thời tiết xấu; quyền lợi khách tuân theo điều khoản đặt tour.'),
('VN-POL-022','Hướng dẫn an toàn bắt buộc','An toàn','Khách phải được phổ biến nội quy và điểm tập trung khẩn cấp; việc ghi nhận tham dự phải có dấu thời gian.'),
('VN-POL-023','Trang bị cứu sinh','An toàn','Tàu phải duy trì thiết bị và số lượng trang bị cứu sinh theo phê duyệt kỹ thuật; không tự khai báo đạt khi chưa xác minh.'),
('VN-POL-024','Khu vực hạn chế tiếp cận','An toàn','Chỉ người được phân quyền mới có thể vào buồng điều khiển, kho kỹ thuật hoặc khu vực hạn chế; sự kiện truy cập cần được lưu.'),
('VN-POL-025','Năng lực trước phân công','Nhân sự','HR phải xác minh tài khoản hoạt động và năng lực/chứng chỉ còn hiệu lực tương ứng với dịch vụ hoặc hoạt động được giao.'),
('VN-POL-026','Định biên tối thiểu theo dịch vụ','Nhân sự','Nhu cầu nhân sự phải lưu theo chuyến và dịch vụ, kiểm tra số lượng thực phân công trước khi chuyển phê duyệt.'),
('VN-POL-027','Không trùng ca giữa các chuyến','Nhân sự','Một nhân viên chỉ có một dịch vụ đang hiệu lực trên mỗi chuyến, không được có ca trùng thời gian tại các chuyến khác.'),
('VN-POL-028','Điều chỉnh lịch trình','Vận hành','Đổi giờ hoặc địa điểm phải ghi phiên bản, lý do, người đề xuất và các cấp phê duyệt cần thiết.'),
('VN-POL-029','Đổi hoặc bỏ cảng ghé','Vận hành','Phải kiểm tra ảnh hưởng đến tour, danh sách khách, nhà cung cấp và thông báo trước khi áp dụng thay đổi.'),
('VN-POL-030','Bàn giao và xác nhận trước chuyến','Vận hành','HR gửi kết quả phân công, quản lý tàu xác nhận, người lập lịch rà soát và OPS phê duyệt; mọi thay đổi phát sinh yêu cầu kiểm tra lại.')
ON CONFLICT(policy_code) DO NOTHING;

-- Synthetic Vietnamese employee candidates: no accounts created by catalog.sql.
INSERT INTO "OperationalSeedEmployee"(employee_code,full_name,department,position,proposed_role,proposed_email) VALUES
('VN-EMP-0001','Vũ Ngọc Khang','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0001@example.invalid'),
('VN-EMP-0002','Dương Hữu Phong','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0002@example.invalid'),
('VN-EMP-0003','Trần Quốc Uyên','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0003@example.invalid'),
('VN-EMP-0004','Võ Gia Hoa','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0004@example.invalid'),
('VN-EMP-0005','Trương Thị Nga','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0005@example.invalid'),
('VN-EMP-0006','Lê Hoài Trí','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0006@example.invalid'),
('VN-EMP-0007','Đặng Phương Hà','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0007@example.invalid'),
('VN-EMP-0008','Đinh Thanh Mai','Ban quản lý tàu','Quản lý tàu','SHIP_MANAGER','vn-emp-0008@example.invalid'),
('VN-EMP-0009','Phạm Khánh Tâm','Nhân sự','Chuyên viên nhân sự','HR_MANAGER','vn-emp-0009@example.invalid'),
('VN-EMP-0010','Bùi Minh Chi','Nhân sự','Chuyên viên nhân sự','HR_MANAGER','vn-emp-0010@example.invalid'),
('VN-EMP-0011','Mai Đức Lan','Nhân sự','Chuyên viên nhân sự','HR_MANAGER','vn-emp-0011@example.invalid'),
('VN-EMP-0012','Hoàng Văn Quân','Nhân sự','Chuyên viên nhân sự','HR_MANAGER','vn-emp-0012@example.invalid'),
('VN-EMP-0013','Đỗ Ngọc Vy','Nhân sự','Chuyên viên nhân sự','HR_MANAGER','vn-emp-0013@example.invalid'),
('VN-EMP-0014','Cao Hữu Huy','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0014@example.invalid'),
('VN-EMP-0015','Huỳnh Quốc Nhung','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0015@example.invalid'),
('VN-EMP-0016','Hồ Gia Tú','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0016@example.invalid'),
('VN-EMP-0017','Lý Thị Hiếu','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0017@example.invalid'),
('VN-EMP-0018','Phan Hoài Nam','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0018@example.invalid'),
('VN-EMP-0019','Ngô Phương Trang','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0019@example.invalid'),
('VN-EMP-0020','Nguyễn Thanh Giang','Điều hành','Chuyên viên điều hành','OPERATIONS_MANAGER','vn-emp-0020@example.invalid'),
('VN-EMP-0021','Vũ Khánh Long','Quản lý lịch trình','Điều phối lịch trình','CRUISE_MANAGER','vn-emp-0021@example.invalid'),
('VN-EMP-0022','Dương Minh Sơn','Quản lý lịch trình','Điều phối lịch trình','CRUISE_MANAGER','vn-emp-0022@example.invalid'),
('VN-EMP-0023','Trần Đức Bình','Quản lý lịch trình','Điều phối lịch trình','CRUISE_MANAGER','vn-emp-0023@example.invalid'),
('VN-EMP-0024','Võ Văn Khôi','Quản lý lịch trình','Điều phối lịch trình','CRUISE_MANAGER','vn-emp-0024@example.invalid'),
('VN-EMP-0025','Trương Ngọc Phúc','Quản lý lịch trình','Điều phối lịch trình','CRUISE_MANAGER','vn-emp-0025@example.invalid'),
('VN-EMP-0026','Lê Hữu Việt','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0026@example.invalid'),
('VN-EMP-0027','Đặng Quốc Hùng','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0027@example.invalid'),
('VN-EMP-0028','Đinh Gia Ngân','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0028@example.invalid'),
('VN-EMP-0029','Phạm Thị Trung','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0029@example.invalid'),
('VN-EMP-0030','Bùi Hoài Hải','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0030@example.invalid'),
('VN-EMP-0031','Mai Phương Minh','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0031@example.invalid'),
('VN-EMP-0032','Hoàng Thanh Thảo','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0032@example.invalid'),
('VN-EMP-0033','Đỗ Khánh Dũng','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0033@example.invalid'),
('VN-EMP-0034','Cao Minh Linh','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0034@example.invalid'),
('VN-EMP-0035','Huỳnh Đức Quỳnh','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0035@example.invalid'),
('VN-EMP-0036','Hồ Văn An','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0036@example.invalid'),
('VN-EMP-0037','Lý Ngọc Khang','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0037@example.invalid'),
('VN-EMP-0038','Phan Hữu Phong','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0038@example.invalid'),
('VN-EMP-0039','Ngô Quốc Uyên','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0039@example.invalid'),
('VN-EMP-0040','Nguyễn Gia Hoa','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0040@example.invalid'),
('VN-EMP-0041','Vũ Thị Nga','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0041@example.invalid'),
('VN-EMP-0042','Dương Hoài Trí','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0042@example.invalid'),
('VN-EMP-0043','Trần Phương Hà','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0043@example.invalid'),
('VN-EMP-0044','Võ Thanh Mai','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0044@example.invalid'),
('VN-EMP-0045','Trương Khánh Tâm','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0045@example.invalid'),
('VN-EMP-0046','Lê Minh Chi','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0046@example.invalid'),
('VN-EMP-0047','Đặng Đức Lan','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0047@example.invalid'),
('VN-EMP-0048','Đinh Văn Quân','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0048@example.invalid'),
('VN-EMP-0049','Phạm Ngọc Vy','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0049@example.invalid'),
('VN-EMP-0050','Bùi Hữu Huy','Nhà hàng','Nhân viên phục vụ nhà hàng','ONBOARD_STAFF','vn-emp-0050@example.invalid'),
('VN-EMP-0051','Mai Quốc Nhung','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0051@example.invalid'),
('VN-EMP-0052','Hoàng Gia Tú','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0052@example.invalid'),
('VN-EMP-0053','Đỗ Thị Hiếu','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0053@example.invalid'),
('VN-EMP-0054','Cao Hoài Nam','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0054@example.invalid'),
('VN-EMP-0055','Huỳnh Phương Trang','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0055@example.invalid'),
('VN-EMP-0056','Hồ Thanh Giang','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0056@example.invalid'),
('VN-EMP-0057','Lý Khánh Long','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0057@example.invalid'),
('VN-EMP-0058','Phan Minh Sơn','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0058@example.invalid'),
('VN-EMP-0059','Ngô Đức Bình','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0059@example.invalid'),
('VN-EMP-0060','Nguyễn Văn Khôi','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0060@example.invalid'),
('VN-EMP-0061','Vũ Ngọc Phúc','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0061@example.invalid'),
('VN-EMP-0062','Dương Hữu Việt','Bar và đồ uống','Nhân viên pha chế','ONBOARD_STAFF','vn-emp-0062@example.invalid'),
('VN-EMP-0063','Trần Quốc Hùng','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0063@example.invalid'),
('VN-EMP-0064','Võ Gia Ngân','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0064@example.invalid'),
('VN-EMP-0065','Trương Thị Trung','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0065@example.invalid'),
('VN-EMP-0066','Lê Hoài Hải','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0066@example.invalid'),
('VN-EMP-0067','Đặng Phương Minh','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0067@example.invalid'),
('VN-EMP-0068','Đinh Thanh Thảo','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0068@example.invalid'),
('VN-EMP-0069','Phạm Khánh Dũng','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0069@example.invalid'),
('VN-EMP-0070','Bùi Minh Linh','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0070@example.invalid'),
('VN-EMP-0071','Mai Đức Quỳnh','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0071@example.invalid'),
('VN-EMP-0072','Hoàng Văn An','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0072@example.invalid'),
('VN-EMP-0073','Đỗ Ngọc Khang','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0073@example.invalid'),
('VN-EMP-0074','Cao Hữu Phong','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0074@example.invalid'),
('VN-EMP-0075','Huỳnh Quốc Uyên','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0075@example.invalid'),
('VN-EMP-0076','Hồ Gia Hoa','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0076@example.invalid'),
('VN-EMP-0077','Lý Thị Nga','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0077@example.invalid'),
('VN-EMP-0078','Phan Hoài Trí','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0078@example.invalid'),
('VN-EMP-0079','Ngô Phương Hà','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0079@example.invalid'),
('VN-EMP-0080','Nguyễn Thanh Mai','Buồng phòng','Nhân viên buồng phòng','ONBOARD_STAFF','vn-emp-0080@example.invalid'),
('VN-EMP-0081','Vũ Khánh Tâm','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0081@example.invalid'),
('VN-EMP-0082','Dương Minh Chi','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0082@example.invalid'),
('VN-EMP-0083','Trần Đức Lan','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0083@example.invalid'),
('VN-EMP-0084','Võ Văn Quân','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0084@example.invalid'),
('VN-EMP-0085','Trương Ngọc Vy','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0085@example.invalid'),
('VN-EMP-0086','Lê Hữu Huy','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0086@example.invalid'),
('VN-EMP-0087','Đặng Quốc Nhung','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0087@example.invalid'),
('VN-EMP-0088','Đinh Gia Tú','Spa','Kỹ thuật viên spa','ONBOARD_STAFF','vn-emp-0088@example.invalid'),
('VN-EMP-0089','Phạm Thị Hiếu','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0089@example.invalid'),
('VN-EMP-0090','Bùi Hoài Nam','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0090@example.invalid'),
('VN-EMP-0091','Mai Phương Trang','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0091@example.invalid'),
('VN-EMP-0092','Hoàng Thanh Giang','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0092@example.invalid'),
('VN-EMP-0093','Đỗ Khánh Long','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0093@example.invalid'),
('VN-EMP-0094','Cao Minh Sơn','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0094@example.invalid'),
('VN-EMP-0095','Huỳnh Đức Bình','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0095@example.invalid'),
('VN-EMP-0096','Hồ Văn Khôi','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0096@example.invalid'),
('VN-EMP-0097','Lý Ngọc Phúc','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0097@example.invalid'),
('VN-EMP-0098','Phan Hữu Việt','Giải trí','Điều phối hoạt động','ONBOARD_STAFF','vn-emp-0098@example.invalid'),
('VN-EMP-0099','Ngô Quốc Hùng','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0099@example.invalid'),
('VN-EMP-0100','Nguyễn Gia Ngân','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0100@example.invalid'),
('VN-EMP-0101','Vũ Thị Trung','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0101@example.invalid'),
('VN-EMP-0102','Dương Hoài Hải','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0102@example.invalid'),
('VN-EMP-0103','Trần Phương Minh','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0103@example.invalid'),
('VN-EMP-0104','Võ Thanh Thảo','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0104@example.invalid'),
('VN-EMP-0105','Trương Khánh Dũng','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0105@example.invalid'),
('VN-EMP-0106','Lê Minh Linh','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0106@example.invalid'),
('VN-EMP-0107','Đặng Đức Quỳnh','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0107@example.invalid'),
('VN-EMP-0108','Đinh Văn An','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0108@example.invalid'),
('VN-EMP-0109','Phạm Ngọc Khang','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0109@example.invalid'),
('VN-EMP-0110','Bùi Hữu Phong','Tour','Hướng dẫn viên','ONBOARD_STAFF','vn-emp-0110@example.invalid'),
('VN-EMP-0111','Mai Quốc Uyên','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0111@example.invalid'),
('VN-EMP-0112','Hoàng Gia Hoa','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0112@example.invalid'),
('VN-EMP-0113','Đỗ Thị Nga','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0113@example.invalid'),
('VN-EMP-0114','Cao Hoài Trí','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0114@example.invalid'),
('VN-EMP-0115','Huỳnh Phương Hà','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0115@example.invalid'),
('VN-EMP-0116','Hồ Thanh Mai','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0116@example.invalid'),
('VN-EMP-0117','Lý Khánh Tâm','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0117@example.invalid'),
('VN-EMP-0118','Phan Minh Chi','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0118@example.invalid'),
('VN-EMP-0119','Ngô Đức Lan','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0119@example.invalid'),
('VN-EMP-0120','Nguyễn Văn Quân','POS và dịch vụ khách hàng','Nhân viên dịch vụ khách hàng','ONBOARD_STAFF','vn-emp-0120@example.invalid')
ON CONFLICT(employee_code) DO NOTHING;

-- Shore excursions require verified suppliers/ports before becoming live records.
INSERT INTO "OperationalSeedTour"(tour_code,tour_name,port_region,duration_minutes,capacity,reference_price_vnd) VALUES
('VN-SHORE-001','Tham quan hang Sửng Sốt và vịnh Hạ Long','Hạ Long',240,35,790000),
('VN-SHORE-002','Chèo kayak khu vực Hang Luồn (phụ thuộc thời tiết)','Hạ Long',180,24,650000),
('VN-SHORE-003','Thăm làng chài Cửa Vạn','Hạ Long',240,28,690000),
('VN-SHORE-004','Ngắm cảnh vịnh Hạ Long bằng thuyền nhỏ','Hạ Long',180,40,590000),
('VN-SHORE-005','Tham quan làng Việt Hải, Cát Bà','Cát Bà',300,24,890000),
('VN-SHORE-006','Khám phá vịnh Lan Hạ bằng thuyền','Cát Bà',240,28,790000),
('VN-SHORE-007','Tham quan rừng ngập mặn Phù Long','Cát Bà',240,25,720000),
('VN-SHORE-008','Trải nghiệm làng chài ven biển Cát Bà','Cát Bà',180,30,590000),
('VN-SHORE-009','Tham quan Ngũ Hành Sơn','Đà Nẵng',210,35,650000),
('VN-SHORE-010','Khám phá bán đảo Sơn Trà','Đà Nẵng',240,30,720000),
('VN-SHORE-011','Tham quan bảo tàng Điêu khắc Chăm','Đà Nẵng',180,35,580000),
('VN-SHORE-012','Thăm phố cổ Hội An từ Đà Nẵng','Đà Nẵng',360,30,990000),
('VN-SHORE-013','Tham quan tháp Bà Ponagar','Nha Trang',180,32,590000),
('VN-SHORE-014','Tắm biển và lặn ngắm san hô (phụ thuộc thời tiết)','Nha Trang',300,20,990000),
('VN-SHORE-015','Khám phá văn hóa địa phương Nha Trang','Nha Trang',210,30,680000),
('VN-SHORE-016','Tham quan Viện Hải dương học','Nha Trang',180,35,620000),
('VN-SHORE-017','Tham quan Dinh Cậu và chợ đêm','Phú Quốc',240,30,690000),
('VN-SHORE-018','Trải nghiệm làng chài Hàm Ninh','Phú Quốc',240,30,790000),
('VN-SHORE-019','Tham quan vườn tiêu Phú Quốc','Phú Quốc',210,30,580000),
('VN-SHORE-020','Trải nghiệm Hòn Thơm (phụ thuộc vận hành)','Phú Quốc',360,24,1290000)
ON CONFLICT(tour_code) DO NOTHING;

-- Service/Activity entitlements are attached to their intended draft packages.
INSERT INTO "TourPackageService"(tour_package_id,service_id,benefit_type)
SELECT p.tour_package_id,s.service_id,'INCLUDED'::"TourPackageService_benefit_type"
FROM "TourPackage" p JOIN "Service" s
  ON s.service_code = ANY(CASE p.package_type::text
     WHEN 'BASIC' THEN ARRAY['VN-SVC-001','VN-SVC-021','VN-SVC-036']
     WHEN 'PLUS' THEN ARRAY['VN-SVC-001','VN-SVC-006','VN-SVC-022','VN-SVC-036']
     WHEN 'ADVANCED' THEN ARRAY['VN-SVC-001','VN-SVC-007','VN-SVC-023','VN-SVC-011','VN-SVC-036']
     WHEN 'PREMIUM' THEN ARRAY['VN-SVC-004','VN-SVC-007','VN-SVC-023','VN-SVC-011','VN-SVC-031','VN-SVC-036']
     WHEN 'PREMIUM_PLUS' THEN ARRAY['VN-SVC-004','VN-SVC-007','VN-SVC-023','VN-SVC-011','VN-SVC-031','VN-SVC-039','VN-SVC-046']
     ELSE ARRAY['VN-SVC-011','VN-SVC-031','VN-SVC-039'] END)
WHERE p.package_code LIKE 'VN-PKG-%' AND p.status='DRAFT'
ON CONFLICT(tour_package_id,service_id) DO NOTHING;
INSERT INTO "TourPackageActivity"(tour_package_id,onboard_activity_id,benefit_type)
SELECT p.tour_package_id,a.onboard_activity_id,'OPTIONAL'::"TourPackageActivity_benefit_type"
FROM "TourPackage" p JOIN "OnboardActivity" a
  ON a.activity_code = ANY(CASE p.package_type::text
    WHEN 'BASIC' THEN ARRAY['VN-ACT-001','VN-ACT-002','VN-ACT-006']
    WHEN 'PLUS' THEN ARRAY['VN-ACT-001','VN-ACT-002','VN-ACT-006','VN-ACT-009']
    WHEN 'ADVANCED' THEN ARRAY['VN-ACT-003','VN-ACT-006','VN-ACT-020']
    WHEN 'PREMIUM' THEN ARRAY['VN-ACT-003','VN-ACT-006','VN-ACT-024']
    WHEN 'PREMIUM_PLUS' THEN ARRAY['VN-ACT-003','VN-ACT-006','VN-ACT-024','VN-ACT-035']
    ELSE ARRAY['VN-ACT-005','VN-ACT-016'] END)
WHERE p.package_code LIKE 'VN-PKG-%' AND p.status='DRAFT'
ON CONFLICT(tour_package_id,onboard_activity_id) DO NOTHING;

-- Vietnamese source strings, with stable keys for FE i18n overrides.
INSERT INTO "CatalogTranslation"(entity_kind,entity_code,locale,display_name)
SELECT 'SERVICE_CATEGORY',category_code,'vi',category_name FROM "ServiceCategory"
WHERE category_code LIKE 'VN-CAT-%'
ON CONFLICT(entity_kind,entity_code,locale) DO NOTHING;
INSERT INTO "CatalogTranslation"(entity_kind,entity_code,locale,display_name)
SELECT 'SERVICE',service_code,'vi',service_name FROM "Service" WHERE service_code LIKE 'VN-SVC-%'
ON CONFLICT(entity_kind,entity_code,locale) DO NOTHING;
INSERT INTO "CatalogTranslation"(entity_kind,entity_code,locale,display_name)
SELECT 'ACTIVITY',activity_code,'vi',activity_name FROM "OnboardActivity"
WHERE activity_code LIKE 'VN-ACT-%'
ON CONFLICT(entity_kind,entity_code,locale) DO NOTHING;
INSERT INTO "CatalogTranslation"(entity_kind,entity_code,locale,display_name)
SELECT 'PACKAGE',package_code,'vi',package_name FROM "TourPackage"
WHERE package_code LIKE 'VN-PKG-%'
ON CONFLICT(entity_kind,entity_code,locale) DO NOTHING;
INSERT INTO "CatalogTranslation"(entity_kind,entity_code,locale,display_name,description)
SELECT 'POLICY',policy_code,'vi',policy_name,description FROM "CommercialPolicyCatalog"
WHERE policy_code LIKE 'VN-POL-%'
ON CONFLICT(entity_kind,entity_code,locale) DO NOTHING;
INSERT INTO "CatalogTranslation"(entity_kind,entity_code,locale,display_name)
SELECT 'SHORE_TOUR',tour_code,'vi',tour_name FROM "OperationalSeedTour"
WHERE tour_code LIKE 'VN-SHORE-%'
ON CONFLICT(entity_kind,entity_code,locale) DO NOTHING;

COMMIT;
