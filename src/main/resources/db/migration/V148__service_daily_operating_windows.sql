ALTER TABLE "Service"
    ADD COLUMN operating_windows jsonb,
    ADD CONSTRAINT service_operating_windows_array
        CHECK (operating_windows IS NULL OR jsonb_typeof(operating_windows) = 'array');
