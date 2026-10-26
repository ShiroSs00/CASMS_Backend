package com.casms.config.flyway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Computes the checksum Flyway 10 stores for an SQL migration: CRC32 over every line read as UTF-8,
 * ignoring line terminators and a leading BOM. The result matches {@code flyway_schema_history.checksum},
 * so CRLF/LF checkouts of the same file produce the same value.
 */
public final class FlywaySqlChecksum {

    private FlywaySqlChecksum() {
    }

    public static int of(InputStream content) throws IOException {
        CRC32 crc = new CRC32();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1);
            }
            while (line != null) {
                crc.update(line.getBytes(StandardCharsets.UTF_8));
                line = reader.readLine();
            }
        }
        return (int) crc.getValue();
    }
}
