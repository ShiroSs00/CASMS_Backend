CREATE TABLE trip_geography_catalog (
    kind varchar(16) NOT NULL CHECK (kind IN ('COUNTRY', 'REGION')),
    code varchar(20) NOT NULL,
    label varchar(100) NOT NULL,
    description varchar(250),
    display_order integer NOT NULL,
    status varchar(8) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    PRIMARY KEY (kind, code)
);

INSERT INTO trip_geography_catalog (kind, code, label, description, display_order)
VALUES
    ('COUNTRY', 'VN', 'Việt Nam', NULL, 1),
    ('COUNTRY', 'SG', 'Singapore', NULL, 2),
    ('COUNTRY', 'TH', 'Thái Lan', NULL, 3),
    ('COUNTRY', 'MY', 'Malaysia', NULL, 4),
    ('COUNTRY', 'JP', 'Nhật Bản', NULL, 5),
    ('REGION', 'BAC_BO', 'Miền Bắc (Bắc Bộ)', 'Bao gồm các cảng Vịnh Hạ Long, Hải Phòng và phụ cận', 1),
    ('REGION', 'TRUNG_BO', 'Miền Trung (Trung Bộ)', 'Bao gồm các cảng Đà Nẵng, Nha Trang, Chân Mây Huế và phụ cận', 2),
    ('REGION', 'NAM_BO', 'Miền Nam (Nam Bộ)', 'Bao gồm các cảng Sài Gòn, Cái Mép - Phú Mỹ, Phú Quốc và phụ cận', 3);
