package lt.satsyuk.api.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class TestTime {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T12:00:00Z");
    public static final OffsetDateTime FIXED_OFFSET_DATE_TIME = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    private TestTime() {
    }
}
