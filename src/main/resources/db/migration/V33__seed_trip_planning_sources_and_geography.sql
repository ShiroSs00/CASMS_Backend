-- V33__seed_trip_planning_sources_and_geography.sql
-- 1. Schema Updates: Allow master/catalog sources without departure cruise/stops
ALTER TABLE "OnboardActivity" ALTER COLUMN "cruise_id" DROP NOT NULL;
ALTER TABLE "ShoreExcursion" ALTER COLUMN "cruise_stop_id" DROP NOT NULL;
ALTER TABLE "ShoreExcursion" ADD COLUMN IF NOT EXISTS "port_id" UUID REFERENCES "Port"("port_id");

-- 2. Destinations (Miền Nam Việt Nam, Thái Lan, Malaysia)
INSERT INTO "Destination" (destination_id, destination_name, country_code, description, status, created_at, updated_at)
VALUES 
  ('a1000000-0000-0000-0000-000000000007', 'TP. Hồ Chí Minh & Nam Bộ', 'VN', 'Khu vực Đông Nam Bộ, Đồng bằng sông Cửu Long và các cảng biển miền Nam Việt Nam', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000008', 'Vịnh Thái Lan & Biển Andaman', 'TH', 'Khu vực du lịch biển Bangkok, Pattaya, Phuket, Thái Lan', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000009', 'Eo Biển Malacca & Malaysia Hub', 'MY', 'Khu vực trung tâm trung chuyển cảng Klang, Penang, Malaysia', 'ACTIVE', NOW(), NOW())
ON CONFLICT (destination_id) DO UPDATE 
SET destination_name = EXCLUDED.destination_name,
    country_code = EXCLUDED.country_code,
    description = EXCLUDED.description;

-- 3. Ports (Cảng miền Nam, Miền Trung mở rộng, Quốc tế)
INSERT INTO "Port" (port_id, port_code, port_name, country_code, time_zone_id, latitude, longitude, status, destination_id, description, created_at, updated_at)
VALUES 
  -- Miền Nam Việt Nam (Asia/Ho_Chi_Minh - UTC+7)
  ('b1000000-0000-0000-0000-000000000011', 'VNSGN', 'Saigon Hiep Phuoc Cruise Port', 'VN', 'Asia/Ho_Chi_Minh', 10.6333, 106.7500, 'ACTIVE', 'a1000000-0000-0000-0000-000000000007', 'Cảng hành khách quốc tế Hiệp Phước, TP. Hồ Chí Minh', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000012', 'VNVUT', 'Cai Mep - Phu My International Port', 'VN', 'Asia/Ho_Chi_Minh', 10.5369, 107.0319, 'ACTIVE', 'a1000000-0000-0000-0000-000000000007', 'Cảng Quốc tế Cái Mép - Thị Vải, Phú Mỹ, Bà Rịa - Vũng Tàu', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000013', 'VNPQC', 'Phu Quoc International Passenger Port', 'VN', 'Asia/Ho_Chi_Minh', 10.2238, 103.9634, 'ACTIVE', 'a1000000-0000-0000-0000-000000000007', 'Cảng hành khách quốc tế Dương Đông, Phú Quốc, Kiên Giang', NOW(), NOW()),

  -- Miền Trung mở rộng (Asia/Ho_Chi_Minh - UTC+7)
  ('b1000000-0000-0000-0000-000000000014', 'VNNHA', 'Nha Trang Tourist Port', 'VN', 'Asia/Ho_Chi_Minh', 12.2045, 109.2158, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'Cảng du lịch quốc tế Nha Trang, Khánh Hòa', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000015', 'VNHUE', 'Chan May Port Thua Thien Hue', 'VN', 'Asia/Ho_Chi_Minh', 16.3267, 107.9867, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'Cảng biển quốc tế Chân Mây, Thừa Thiên Huế', NOW(), NOW()),

  -- Thái Lan (Asia/Bangkok - UTC+7)
  ('b1000000-0000-0000-0000-000000000016', 'THBKK', 'Laem Chabang Cruise Terminal Bangkok', 'TH', 'Asia/Bangkok', 13.0825, 100.8833, 'ACTIVE', 'a1000000-0000-0000-0000-000000000008', 'Cảng du thuyền quốc tế Laem Chabang, Bangkok, Thái Lan', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000017', 'THHKT', 'Phuket Deep Sea Cruise Port', 'TH', 'Asia/Bangkok', 7.8286, 98.4069, 'ACTIVE', 'a1000000-0000-0000-0000-000000000008', 'Cảng du lịch nước sâu quốc tế Phuket, Thái Lan', NOW(), NOW()),

  -- Malaysia (Asia/Kuala_Lumpur - UTC+8)
  ('b1000000-0000-0000-0000-000000000018', 'MYPKG', 'Boustead Cruise Centre Port Klang', 'MY', 'Asia/Kuala_Lumpur', 2.9994, 101.3928, 'ACTIVE', 'a1000000-0000-0000-0000-000000000009', 'Trung tâm du thuyền quốc tế Boustead, Cảng Klang (Kuala Lumpur)', NOW(), NOW())
ON CONFLICT (port_code) DO UPDATE 
SET port_name = EXCLUDED.port_name,
    country_code = EXCLUDED.country_code,
    time_zone_id = EXCLUDED.time_zone_id,
    destination_id = EXCLUDED.destination_id,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    description = EXCLUDED.description;

-- 4. Tour Suppliers
INSERT INTO "Supplier" (supplier_id, supplier_name, supplier_type, contact_name, contact_phone, contact_email, notes, status, verification_status, created_at, updated_at)
VALUES
  ('c1000000-0000-0000-0000-000000000001', 'Ha Long Heritage Tours Co.', 'TOUR_OPERATOR', 'Nguyễn Văn Hùng', '+84 912345678', 'contact@halongtours.vn', 'Bến tàu Tuần Châu, Hạ Long, Quảng Ninh', 'ACTIVE', 'VERIFIED', NOW(), NOW()),
  ('c1000000-0000-0000-0000-000000000002', 'Da Nang Green Travel JSC', 'TOUR_OPERATOR', 'Trần Thị Mai', '+84 905123456', 'tours@danangtravel.vn', 'Số 120 Bạch Đằng, Hải Châu, Đà Nẵng', 'ACTIVE', 'VERIFIED', NOW(), NOW()),
  ('c1000000-0000-0000-0000-000000000003', 'Saigon Star Discovery Travel', 'TOUR_OPERATOR', 'Lê Hoàng Nam', '+84 988776655', 'ops@saigonstar.vn', 'Số 45 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh', 'ACTIVE', 'VERIFIED', NOW(), NOW()),
  ('c1000000-0000-0000-0000-000000000004', 'Singapore Island Explorer Tours Pte Ltd', 'TOUR_OPERATOR', 'Michael Tan', '+65 6789 0123', 'booking@sgexplorertours.com', '10 Marina Boulevard, Singapore', 'ACTIVE', 'VERIFIED', NOW(), NOW())
ON CONFLICT (supplier_id) DO UPDATE
SET supplier_name = EXCLUDED.supplier_name,
    supplier_type = EXCLUDED.supplier_type,
    contact_name = EXCLUDED.contact_name,
    contact_phone = EXCLUDED.contact_phone,
    contact_email = EXCLUDED.contact_email,
    notes = EXCLUDED.notes,
    verification_status = EXCLUDED.verification_status,
    status = EXCLUDED.status;

-- 5. Master Catalog: Onboard Activities
INSERT INTO "OnboardActivity" (onboard_activity_id, activity_code, activity_name, description, start_time, end_time, location, capacity, price, currency_code, status, created_at, updated_at)
VALUES
  ('d1000000-0000-0000-0000-000000000001', 'AC-YOGA-01', 'Lớp Yoga & Thiền Đón Bình Minh', 'Lớp thiền định và yoga năng lượng chào ngày mới cùng chuyên gia quốc tế trên boong tàu ngắm cảnh biển bao la.', '2026-01-01 06:00:00+00', '2026-01-01 07:15:00+00', 'Sun Deck 10 - Boong ngắm cảnh phía mũi', 35, 0.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000002', 'AC-MAGIC-01', 'Đại Nhạc Hội Ảo Thuật & Xiếc Quốc Tế', 'Chương trình biểu diễn nghệ thuật giải trí đẳng cấp thế giới với các nghệ sĩ ảo thuật và nhào lộn quốc tế.', '2026-01-01 20:00:00+00', '2026-01-01 21:30:00+00', 'Starlight Grand Theater - Tầng 5', 450, 0.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000003', 'AC-TEA-01', 'Tiệc Trà Chiều Hoàng Gia & Hòa Tấu Piano', 'Thưởng thức trà cao cấp phong cách quý tộc Anh kèm bánh ngọt scone, macaron trong tiếng dương cầm du dương.', '2026-01-01 15:00:00+00', '2026-01-01 16:30:00+00', 'Ocean View Lounge - Tầng 7', 80, 25.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000004', 'AC-COOK-01', 'Lớp Học Nghệ Thuật Ẩm Thực & Làm Bánh Pháp', 'Cùng bếp trưởng 5 sao thực hành chế biến các món hải sản cao cấp và tráng miệng đặc sắc.', '2026-01-01 10:00:00+00', '2026-01-01 11:30:00+00', 'Culinary Studio - Tầng 4', 20, 45.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000005', 'AC-JAZZ-01', 'Đêm Nhạc Jazz Sống & Thưởng Thức Rượu Vang', 'Không gian lãng mạn với ban nhạc jazz sống, cocktail chữ ký và các dòng rượu vang thượng hạng.', '2026-01-01 21:00:00+00', '2026-01-01 23:00:00+00', 'Moonlight Jazz Club - Tầng 8', 100, 15.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000006', 'AC-SPA-01', 'Trị Liệu Massage Đá Nóng Thư Giãn Lotus Spa', 'Gói thư giãn cơ thể chuyên sâu bằng thảo dược tự nhiên và đá nóng basalt.', '2026-01-01 09:00:00+00', '2026-01-01 10:30:00+00', 'Lotus Wellness Spa - Tầng 9', 15, 60.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000007', 'AC-VR-01', 'Giải Đấu Thể Thao Điện Tử & Thực Tế Ảo VR', 'Khu vui chơi công nghệ hiện đại với kính VR 4K, máy lái xe F1 mô phỏng và thi đấu đối kháng giao lưu.', '2026-01-01 14:00:00+00', '2026-01-01 17:00:00+00', 'Cyber Arena - Tầng 6', 50, 10.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000008', 'AC-POOL-01', 'Tiệc Bể Bơi & DJ Sunset Party', 'Âm nhạc EDM sôi động cùng cocktail nhiệt đới và các hoạt động mini-game dưới nước khi hoàng hôn buông xuống.', '2026-01-01 16:30:00+00', '2026-01-01 18:30:00+00', 'Panorama Pool Deck - Tầng 10', 200, 0.0000, 'USD', 'PLANNED', NOW(), NOW())
ON CONFLICT (activity_code) DO UPDATE
SET activity_name = EXCLUDED.activity_name,
    description = EXCLUDED.description,
    location = EXCLUDED.location,
    capacity = EXCLUDED.capacity,
    price = EXCLUDED.price,
    status = EXCLUDED.status;

-- 6. Master Catalog: Shore Excursions (Mapped directly to Port)
INSERT INTO "ShoreExcursion" (shore_excursion_id, port_id, excursion_code, excursion_name, supplier_id, start_time, planned_return_time, meeting_point, minimum_participants, maximum_capacity, price, currency_code, status, created_at, updated_at)
VALUES
  -- Hạ Long (VNHLG: 'b1000000-0000-0000-0000-000000000007')
  ('e1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000007', 'EX-HLG-KAYAK', 'Chèo Thuyền Kayak & Khám Phá Hang Luồn Vịnh Hạ Long', 'c1000000-0000-0000-0000-000000000001', '2026-01-01 08:30:00+00', '2026-01-01 12:00:00+00', 'Sảnh đón khách nhà ga Cảng tàu khách quốc tế Hạ Long', 8, 40, 55.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('e1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000007', 'EX-HLG-SUNGSOT', 'Thám Hiểm Hang Sửng Sốt & Tắm Biển Đảo Ti Tốp', 'c1000000-0000-0000-0000-000000000001', '2026-01-01 09:00:00+00', '2026-01-01 14:30:00+00', 'Cầu cảng số 2 - Cảng quốc tế Hạ Long', 10, 60, 75.0000, 'USD', 'PLANNED', NOW(), NOW()),

  -- Đà Nẵng (VNDAD: 'b1000000-0000-0000-0000-000000000009')
  ('e1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000009', 'EX-DAD-HOIAN', 'Khám Phá Phố Cổ Hội An & Thả Đèn Hoa Đăng Về Đêm', 'c1000000-0000-0000-0000-000000000002', '2026-01-01 14:00:00+00', '2026-01-01 20:30:00+00', 'Khu vực bãi đỗ xe du lịch Cảng Tiên Sa', 15, 80, 65.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('e1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000009', 'EX-DAD-BANA', 'Tham Quan Khu Du Lịch Bà Nà Hills & Check-in Cầu Vàng', 'c1000000-0000-0000-0000-000000000002', '2026-01-01 08:30:00+00', '2026-01-01 16:00:00+00', 'Cổng an ninh số 1 Cảng Tiên Sa', 10, 50, 95.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('e1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000009', 'EX-DAD-MARBLE', 'Danh Thắng Ngũ Hành Sơn & Bán Đảo Sơn Trà Chùa Linh Ứng', 'c1000000-0000-0000-0000-000000000002', '2026-01-01 09:00:00+00', '2026-01-01 13:30:00+00', 'Quầy hướng dẫn tour Cảng Tiên Sa', 10, 45, 50.0000, 'USD', 'PLANNED', NOW(), NOW()),

  -- Sài Gòn (VNSGN: 'b1000000-0000-0000-0000-000000000011')
  ('e1000000-0000-0000-0000-000000000006', 'b1000000-0000-0000-0000-000000000011', 'EX-SGN-CUCHI', 'Hành Trình Lịch Sử Địa Đạo Củ Chi & Bắn Súng Thể Thao', 'c1000000-0000-0000-0000-000000000003', '2026-01-01 08:00:00+00', '2026-01-01 14:00:00+00', 'Nhà đón khách Ga Cảng Hiệp Phước', 10, 50, 70.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('e1000000-0000-0000-0000-000000000007', 'b1000000-0000-0000-0000-000000000011', 'EX-SGN-CITY', 'City Tour Sài Gòn Năng Động: Dinh Độc Lập & Chợ Bến Thành', 'c1000000-0000-0000-0000-000000000003', '2026-01-01 09:00:00+00', '2026-01-01 15:00:00+00', 'Quầy điều hành tour Cảng Hiệp Phước', 10, 60, 60.0000, 'USD', 'PLANNED', NOW(), NOW()),

  -- Singapore (SGSIN: 'b1000000-0000-0000-0000-000000000010')
  ('e1000000-0000-0000-0000-000000000008', 'b1000000-0000-0000-0000-000000000010', 'EX-SIN-GARDENS', 'Tham Quan Siêu Cây Gardens by the Bay & SkyPark Marina Bay Sands', 'c1000000-0000-0000-0000-000000000004', '2026-01-01 10:00:00+00', '2026-01-01 16:00:00+00', 'Arrival Hall, Marina Bay Cruise Centre Singapore', 12, 70, 85.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('e1000000-0000-0000-0000-000000000009', 'b1000000-0000-0000-0000-000000000010', 'EX-SIN-SENTOSA', 'Khám Phá Đảo Sentosa & Đi Cáp Treo Qua Biển Ngắm Toàn Cảnh', 'c1000000-0000-0000-0000-000000000004', '2026-01-01 09:30:00+00', '2026-01-01 15:30:00+00', 'Coach Bay Area, Marina Bay Cruise Centre', 12, 55, 90.0000, 'USD', 'PLANNED', NOW(), NOW())
ON CONFLICT (shore_excursion_id) DO UPDATE
SET excursion_name = EXCLUDED.excursion_name,
    port_id = EXCLUDED.port_id,
    meeting_point = EXCLUDED.meeting_point,
    maximum_capacity = EXCLUDED.maximum_capacity,
    price = EXCLUDED.price,
    status = EXCLUDED.status;

-- 7. Master Catalog: Services (Serving Windows)
INSERT INTO "Service" (service_id, service_code, service_name, description, service_type, price, currency_code, capacity, status, created_at, updated_at)
VALUES
  ('f1000000-0000-0000-0000-000000000001', 'SRV-BF-01', 'Buffet Sáng Quốc Tế Grand Ocean', 'Tiệc buffet sáng phong phú ẩm thực Á - Âu, trà và cà phê tươi hảo hạng.', 'DINING', 0.0000, 'USD', 600, 'ACTIVE', NOW(), NOW()),
  ('f1000000-0000-0000-0000-000000000002', 'SRV-LU-01', 'Buffet Trưa Hải Sản & Đồ Nướng BBQ', 'Buffet trưa với quầy hải sản tươi sống, thịt nướng thơm ngon và các món ăn nhiệt đới.', 'DINING', 0.0000, 'USD', 550, 'ACTIVE', NOW(), NOW()),
  ('f1000000-0000-0000-0000-000000000003', 'SRV-DN-01', 'Bữa Tối Fine Dining Hải Sản Hoàng Gia', 'Thực đơn 5 món phong cách Châu Âu phục vụ tại bàn với hải sản cao cấp và rượu vang.', 'DINING', 45.0000, 'USD', 200, 'ACTIVE', NOW(), NOW()),
  ('f1000000-0000-0000-0000-000000000004', 'SRV-BAR-01', 'Quầy Bar Sky Lounge & Nhạc Sống Hoàng Hôn', 'Khung giờ phục vụ cocktail nhiệt đới, bia tươi thủ công và ngắm hoàng hôn trên biển.', 'BEVERAGE', 12.0000, 'USD', 150, 'ACTIVE', NOW(), NOW()),
  ('f1000000-0000-0000-0000-000000000005', 'SRV-FIT-01', 'Phòng Gym & Trung Tâm Thể Thao Toàn Cảnh', 'Trang thiết bị tập luyện Technogym hiện đại hướng biển và huấn luyện viên hướng dẫn.', 'WELLNESS', 0.0000, 'USD', 40, 'ACTIVE', NOW(), NOW())
ON CONFLICT (service_code) DO UPDATE
SET service_name = EXCLUDED.service_name,
    description = EXCLUDED.description,
    price = EXCLUDED.price,
    status = EXCLUDED.status;
