-- V35__add_activity_category_and_location_refinement.sql
-- 1. Schema Updates: Thêm room_name cho Cabin và category cho OnboardActivity & TripPlanEntry
ALTER TABLE "Cabin" ADD COLUMN IF NOT EXISTS "room_name" VARCHAR(150);
ALTER TABLE "OnboardActivity" ADD COLUMN IF NOT EXISTS "category" VARCHAR(50);
ALTER TABLE "TripPlanEntry" ADD COLUMN IF NOT EXISTS "category" VARCHAR(50);

-- 2. Cập nhật tên công năng chi tiết (room_name) cho các phòng chức năng trên tàu
UPDATE "Cabin" SET room_name = 'Phòng Trị Liệu Thảo Dược Lotus' WHERE cabin_number = '201';
UPDATE "Cabin" SET room_name = 'Phòng Massage Đá Nóng VIP' WHERE cabin_number = '202';
UPDATE "Cabin" SET room_name = 'Phòng Xông Hơi Thủy Lực' WHERE cabin_number = '203';
UPDATE "Cabin" SET room_name = 'Phòng Chăm Sóc Sắc Đẹp & Trị Liệu' WHERE cabin_number = '204';
UPDATE "Cabin" SET room_name = 'Sảnh Buffet Grand Horizon Dining' WHERE cabin_number = '301';
UPDATE "Cabin" SET room_name = 'Phòng Tiệc VIP Private Horizon' WHERE cabin_number = '302';
UPDATE "Cabin" SET room_name = 'Studio Ẩm Thực & Bếp Thực Hành' WHERE cabin_number = '401';
UPDATE "Cabin" SET room_name = 'Phòng Thử Rượu Vang & Bartender VIP' WHERE cabin_number = '402';
UPDATE "Cabin" SET room_name = 'Khán Phòng Chính Starlight Theater' WHERE cabin_number = '501';
UPDATE "Cabin" SET room_name = 'Tầng Lửng VIP Starlight Theater' WHERE cabin_number = '502';
UPDATE "Cabin" SET room_name = 'Đấu Trường Công Nghệ VR Cyber Arena' WHERE cabin_number = '601';
UPDATE "Cabin" SET room_name = 'Phòng Trà Chiều & Thư Viện Hoàng Gia' WHERE cabin_number = '701';
UPDATE "Cabin" SET room_name = 'Sân Khấu Moonlight Jazz & Wine Club' WHERE cabin_number = '801';
UPDATE "Cabin" SET room_name = 'Quầy Bar & Bể Bơi Panorama DJ' WHERE cabin_number = '1001';

-- 3. Phân loại 4 nhóm danh mục cho các hoạt động mẫu hiện hữu
UPDATE "OnboardActivity" SET category = 'SPORTS_WELLNESS' WHERE activity_code = 'AC-YOGA-01';
UPDATE "OnboardActivity" SET category = 'ENTERTAINMENT' WHERE activity_code = 'AC-MAGIC-01';
UPDATE "OnboardActivity" SET category = 'ENTERTAINMENT' WHERE activity_code = 'AC-TEA-01';
UPDATE "OnboardActivity" SET category = 'ENTERTAINMENT' WHERE activity_code = 'AC-COOK-01';
UPDATE "OnboardActivity" SET category = 'ENTERTAINMENT' WHERE activity_code = 'AC-JAZZ-01';
UPDATE "OnboardActivity" SET category = 'SPORTS_WELLNESS' WHERE activity_code = 'AC-SPA-01';
UPDATE "OnboardActivity" SET category = 'FAMILY_KIDS' WHERE activity_code = 'AC-VR-01';
UPDATE "OnboardActivity" SET category = 'ENTERTAINMENT' WHERE activity_code = 'AC-POOL-01';

-- 4. Bổ sung hoạt động cho nhóm SPECIAL_EVENTS và FAMILY_KIDS
INSERT INTO "OnboardActivity" (onboard_activity_id, activity_code, activity_name, category, description, start_time, end_time, location, deck_id, cabin_id, capacity, price, currency_code, status, created_at, updated_at)
VALUES
  ('d1000000-0000-0000-0000-000000000009', 'AC-GALA-01', 'Đêm Tiệc Gala Thuyền Trưởng & VIP Welcome Party', 'SPECIAL_EVENTS', 'Dạ tiệc chào mừng trang trọng cùng Thuyền trưởng, thưởng thức champagne thượng hạng và khiêu vũ giao lưu.', '2026-01-01 19:30:00+00', '2026-01-01 22:00:00+00', 'Starlight Grand Theater - Tầng 5', '85000000-0000-4000-8000-000000000215', '85000000-0000-4000-8000-000000000261', 450, 0.0000, 'USD', 'PLANNED', NOW(), NOW()),
  ('d1000000-0000-0000-0000-000000000010', 'AC-KIDS-01', 'Hành Trình Thám Hiểm Săn Tìm Kho Báu Hải Tặc', 'FAMILY_KIDS', 'Cuộc phiêu lưu truy tìm mật mã và bản đồ kho báu hải tặc quanh các boong tàu dành cho gia đình và các bé.', '2026-01-01 15:00:00+00', '2026-01-01 17:00:00+00', 'Cyber Arena & Family Deck - Tầng 6', '85000000-0000-4000-8000-000000000216', '85000000-0000-4000-8000-000000000271', 60, 0.0000, 'USD', 'PLANNED', NOW(), NOW())
ON CONFLICT (activity_code) DO UPDATE
SET activity_name = EXCLUDED.activity_name,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    location = EXCLUDED.location,
    deck_id = EXCLUDED.deck_id,
    cabin_id = EXCLUDED.cabin_id;
