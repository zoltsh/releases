package sh.zolt.releases.record;

import java.util.regex.Pattern;
import sh.zolt.releases.core.ReleaseConstants;

final class ReleaseRecordRules {
    static final String SCHEMA_FILE = "schemas/release-record-v1.schema.json";
    static final Pattern ARCHIVE = Pattern.compile(
            "^zolt-(?<version>.+)-(?<target>"
                    + String.join("|", ReleaseConstants.RELEASE_TARGETS)
                    + ")\\.tar\\.gz$");
    static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private ReleaseRecordRules() {}
}
