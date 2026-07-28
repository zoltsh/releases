package sh.zolt.releases.version;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

final class ZapVersionRules {
    static final Pattern BASE_VERSION =
            Pattern.compile("^(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)$");
    static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");
    static final Pattern OFFSET = Pattern.compile("(?:Z|[+-][0-9]{2}:[0-9]{2})$");
    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private ZapVersionRules() {}
}
