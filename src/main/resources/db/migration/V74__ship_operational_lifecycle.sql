-- Retain existing identities; migrate legacy status names to the approved lifecycle.
-- VARCHAR works with JPA EnumType.STRING on both original enum and legacy varchar schemas.
ALTER TABLE "Ship" ALTER COLUMN status DROP DEFAULT;
ALTER TABLE "Ship" ALTER COLUMN status TYPE varchar(32) USING status::text;
DO $$ DECLARE c record; BEGIN
    FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='"Ship"'::regclass
        AND contype='c' AND array_length(conkey,1)=1 AND pg_get_constraintdef(oid) ~* 'status' LOOP
        EXECUTE format('ALTER TABLE "Ship" DROP CONSTRAINT %I',c.conname);
    END LOOP;
END $$;
UPDATE "Ship" SET status=CASE status WHEN 'ACTIVE' THEN 'READY' WHEN 'INACTIVE' THEN 'SUSPENDED' ELSE status END;
ALTER TABLE "Ship" ADD CONSTRAINT ck_ship_lifecycle_status
    CHECK (status IN ('DRAFT','READY','IN_SERVICE','MAINTENANCE','SUSPENDED','RETIRED'));
