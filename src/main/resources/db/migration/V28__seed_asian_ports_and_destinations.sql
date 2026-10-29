-- V28: Seed popular Asian cruise destinations and ports with standard IANA timezones and UN/LOCODE codes

-- 1. Insert Destinations
INSERT INTO "Destination" (destination_id, destination_name, country_code, description, status, created_at, updated_at)
VALUES 
  ('a1000000-0000-0000-0000-000000000001', 'Tokyo Bay & Kanto', 'JP', 'Khu vực vịnh Tokyo và vùng Kanto, Nhật Bản', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000002', 'Kansai Region', 'JP', 'Khu vực vịnh Osaka, Kobe và vùng Kansai, Nhật Bản', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000003', 'Kyushu Island', 'JP', 'Khu vực đảo Kyushu (Nagasaki, Fukuoka), Nhật Bản', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000004', 'Vịnh Hạ Long & Bắc Bộ', 'VN', 'Khu vực di sản Vịnh Hạ Long và các cảng biển miền Bắc Việt Nam', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000005', 'Đà Nẵng & Miền Trung', 'VN', 'Cửa ngõ du lịch biển miền Trung Việt Nam', 'ACTIVE', NOW(), NOW()),
  ('a1000000-0000-0000-0000-000000000006', 'Singapore Marina Hub', 'SG', 'Trung tâm trung chuyển du thuyền quốc tế Singapore', 'ACTIVE', NOW(), NOW())
ON CONFLICT (destination_id) DO UPDATE 
SET destination_name = EXCLUDED.destination_name,
    country_code = EXCLUDED.country_code,
    description = EXCLUDED.description;

-- 2. Insert Ports linked to Destinations with accurate IANA timezones
INSERT INTO "Port" (port_id, port_code, port_name, country_code, time_zone_id, latitude, longitude, status, destination_id, description, created_at, updated_at)
VALUES 
  -- Nhật Bản: Tokyo Bay & Kanto (Asia/Tokyo - UTC+9)
  ('b1000000-0000-0000-0000-000000000001', 'JPYOK', 'Yokohama Osanbashi Terminal', 'JP', 'Asia/Tokyo', 35.4522, 139.6486, 'ACTIVE', 'a1000000-0000-0000-0000-000000000001', 'Bến tàu du lịch quốc tế Osanbashi, Yokohama, Nhật Bản', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000002', 'JPTYO', 'Tokyo International Cruise Terminal', 'JP', 'Asia/Tokyo', 35.6194, 139.7753, 'ACTIVE', 'a1000000-0000-0000-0000-000000000001', 'Bến cảng du thuyền quốc tế Odaiba, Tokyo, Nhật Bản', NOW(), NOW()),

  -- Nhật Bản: Kansai (Asia/Tokyo - UTC+9)
  ('b1000000-0000-0000-0000-000000000003', 'JPKOB', 'Kobe Port Terminal', 'JP', 'Asia/Tokyo', 34.6853, 135.1970, 'ACTIVE', 'a1000000-0000-0000-0000-000000000002', 'Bến cảng du thuyền Naka Pier, Kobe, Nhật Bản', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000004', 'JPOSA', 'Osaka Tempozan Cruise Wharf', 'JP', 'Asia/Tokyo', 34.6547, 135.4289, 'ACTIVE', 'a1000000-0000-0000-0000-000000000002', 'Cảng du thuyền Tempozan, Osaka, Nhật Bản', NOW(), NOW()),

  -- Nhật Bản: Kyushu (Asia/Tokyo - UTC+9)
  ('b1000000-0000-0000-0000-000000000005', 'JPNGS', 'Nagasaki Matsugae Wharf', 'JP', 'Asia/Tokyo', 32.7369, 129.8697, 'ACTIVE', 'a1000000-0000-0000-0000-000000000003', 'Cảng quốc tế Matsugae, Nagasaki, Nhật Bản', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000006', 'JPHKT', 'Hakata International Cruise Port', 'JP', 'Asia/Tokyo', 33.6067, 130.3994, 'ACTIVE', 'a1000000-0000-0000-0000-000000000003', 'Bến du thuyền quốc tế Hakata, Fukuoka, Nhật Bản', NOW(), NOW()),

  -- Việt Nam: Vịnh Hạ Long & Bắc Bộ (Asia/Ho_Chi_Minh - UTC+7)
  ('b1000000-0000-0000-0000-000000000007', 'VNHLG', 'Ha Long International Cruise Port', 'VN', 'Asia/Ho_Chi_Minh', 20.9500, 107.0500, 'ACTIVE', 'a1000000-0000-0000-0000-000000000004', 'Cảng tàu khách quốc tế Hạ Long, Quảng Ninh, Việt Nam', NOW(), NOW()),
  ('b1000000-0000-0000-0000-000000000008', 'VNHPH', 'Hai Phong Port', 'VN', 'Asia/Ho_Chi_Minh', 20.8449, 106.6881, 'ACTIVE', 'a1000000-0000-0000-0000-000000000004', 'Cảng biển quốc tế Hải Phòng, Việt Nam', NOW(), NOW()),

  -- Việt Nam: Đà Nẵng (Asia/Ho_Chi_Minh - UTC+7)
  ('b1000000-0000-0000-0000-000000000009', 'VNDAD', 'Tien Sa Port Da Nang', 'VN', 'Asia/Ho_Chi_Minh', 16.1215, 108.2238, 'ACTIVE', 'a1000000-0000-0000-0000-000000000005', 'Cảng Tiên Sa, Đà Nẵng, Việt Nam', NOW(), NOW()),

  -- Singapore (Asia/Singapore - UTC+8)
  ('b1000000-0000-0000-0000-000000000010', 'SGSIN', 'Marina Bay Cruise Centre', 'SG', 'Asia/Singapore', 1.2707, 103.8606, 'ACTIVE', 'a1000000-0000-0000-0000-000000000006', 'Trung tâm du thuyền quốc tế Marina Bay, Singapore', NOW(), NOW())
ON CONFLICT (port_code) DO UPDATE 
SET port_name = EXCLUDED.port_name,
    country_code = EXCLUDED.country_code,
    time_zone_id = EXCLUDED.time_zone_id,
    destination_id = EXCLUDED.destination_id,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    description = EXCLUDED.description;
