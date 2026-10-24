-- V30__create_business_code_rules_schema.sql
-- Table storing business code rules per entity type
CREATE TABLE IF NOT EXISTS business_code_rule (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL UNIQUE,
    pattern VARCHAR(100) NOT NULL,
    scope_strategy VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table storing concurrency-safe atomic sequence counters per partition
CREATE TABLE IF NOT EXISTS code_sequence_tracker (
    sequence_key VARCHAR(100) PRIMARY KEY,
    current_value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial rules for TRIP, SHIP, CRUISE, EXCURSION, BOOKING
INSERT INTO business_code_rule (entity_type, pattern, scope_strategy, description)
VALUES 
    ('TRIP', '{ENTITY}-{SCOPE}/{TYPE}-{SEQ:4}', 'BY_TYPE_AND_YEAR', 'Mã kế hoạch Trip theo phạm vi, loại chuyến và năm'),
    ('SHIP', 'SH-{SHIP_TYPE}-{SEQ:3}', 'GLOBAL', 'Mã định danh tàu theo loại tàu'),
    ('CRUISE', 'CR-{YYYY}-{SHIP}-{SEQ:3}', 'BY_YEAR', 'Mã chuyến Cruise thực tế theo năm và tàu'),
    ('EXCURSION', 'EX-{PORT}-{SEQ:3}', 'GLOBAL', 'Mã tour tham quan trên bờ theo cảng'),
    ('BOOKING', 'BK-{YYYY}-{SEQ:5}', 'BY_YEAR', 'Mã đơn đặt chỗ theo năm')
ON CONFLICT (entity_type) DO NOTHING;
