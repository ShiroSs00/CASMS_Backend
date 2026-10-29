-- V36__standardize_geography_domestic_region.sql
-- 1. Thêm cột domestic_region vào Destination và Port
ALTER TABLE "Destination" ADD COLUMN IF NOT EXISTS "domestic_region" VARCHAR(20);
ALTER TABLE "Port" ADD COLUMN IF NOT EXISTS "domestic_region" VARCHAR(20);

-- 2. Chuẩn hóa miền cho các Destination đã có
UPDATE "Destination" SET "domestic_region" = 'BAC_BO' WHERE "destination_id" = 'a1000000-0000-0000-0000-000000000004';
UPDATE "Destination" SET "domestic_region" = 'TRUNG_BO' WHERE "destination_id" = 'a1000000-0000-0000-0000-000000000005';
UPDATE "Destination" SET "domestic_region" = 'NAM_BO' WHERE "destination_id" = 'a1000000-0000-0000-0000-000000000007';

-- 3. Chuẩn hóa miền cho các Cảng hiện có
UPDATE "Port" SET "domestic_region" = 'BAC_BO' WHERE "port_code" IN ('VNHLG', 'VNHPH');
UPDATE "Port" SET "domestic_region" = 'TRUNG_BO' WHERE "port_code" IN ('VNDAD', 'VNNHA', 'VNHUE');
UPDATE "Port" SET "domestic_region" = 'NAM_BO' WHERE "port_code" IN ('VNSGN', 'VNVUT', 'VNPQC');

-- 4. Bổ sung các cảng du thuyền và cảng biển quan trọng tại Việt Nam
INSERT INTO "Port" (port_id, port_code, port_name, country_code, time_zone_id, latitude, longitude, status, destination_id, domestic_region, description, created_at, updated_at)
VALUES
  -- Bắc Bộ bổ sung
  ('b1000000-0000-0000-0000-000000000019', 'VNCAM', 'Cam Pha International Port', 'VN', 'Asia/Ho_Chi_Minh', 21.0167, 107.3000, 'ACTIVE', 'a1000000-0000-0000-0000-000000000004', 'BAC_BO', 'Cảng biển quốc tế Cẩm Phả, Quảng Ninh', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000020', 'VNQNH', 'Cai Lan Port Quang Ninh', 'VN', 'Asia/Ho_Chi_Minh', 20.9700, 107.0300, 'ACTIVE', 'a1000000-0000-0000-0000-000000000004', 'BAC_BO', 'Cảng nước sâu Cái Lân, TP. Hạ Long, Quảng Ninh', NOW(), NOW()),

  -- Trung Bộ bổ sung
  ('b1000000-0000-0000-0000-000000000021', 'VNUIH', 'Quy Nhon Port Binh Dinh', 'VN', 'Asia/Ho_Chi_Minh', 13.7667, 109.2333, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'TRUNG_BO', 'Cảng biển quốc tế Quy Nhơn, Bình Định', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000022', 'VNCNA', 'Cam Ranh International Cruise Port', 'VN', 'Asia/Ho_Chi_Minh', 11.9167, 109.1500, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'TRUNG_BO', 'Cảng quốc tế Cam Ranh, Khánh Hòa', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000023', 'VNDON', 'Hon La Port Quang Binh', 'VN', 'Asia/Ho_Chi_Minh', 17.9167, 106.5167, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'TRUNG_BO', 'Cảng biển Hòn La, Quảng Bình (gần Động Phong Nha)', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000024', 'VNCAN', 'Cua Lo Port Nghe An', 'VN', 'Asia/Ho_Chi_Minh', 18.8000, 105.7167, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'TRUNG_BO', 'Cảng biển Cửa Lò, Nghệ An', NOW(), NOW()),

  -- Nam Bộ bổ sung
  ('b1000000-0000-0000-0000-000000000025', 'VNCTO', 'Can Tho Passenger Port', 'VN', 'Asia/Ho_Chi_Minh', 10.0333, 105.7833, 'ACTIVE', 'a1000000-0000-0000-0000-000000000007', 'NAM_BO', 'Bến tàu khách quốc tế Cần Thơ, Đồng bằng sông Cửu Long', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000026', 'VNCMU', 'Nam Can Cruise Port Ca Mau', 'VN', 'Asia/Ho_Chi_Minh', 8.7500, 104.9833, 'ACTIVE', 'a1000000-0000-0000-0000-000000000007', 'NAM_BO', 'Cảng Năm Căn / Mũi Cà Mau, cực Nam Việt Nam', NOW(), NOW())
ON CONFLICT (port_code) DO UPDATE 
SET port_name = EXCLUDED.port_name,
    country_code = EXCLUDED.country_code,
    time_zone_id = EXCLUDED.time_zone_id,
    destination_id = EXCLUDED.destination_id,
    domestic_region = EXCLUDED.domestic_region,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    description = EXCLUDED.description;
