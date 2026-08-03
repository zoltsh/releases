package sh.zolt.releases.core;

import java.util.List;

public final class ReleaseConstants {
    public static final String CONTROLLER_REPOSITORY = "zoltsh/releases";
    public static final String SOURCE_REPOSITORY = "zoltsh/zolt";
    public static final String DEFAULT_POLICY_FILE = "policy/channels.toml";
    public static final String DEFAULT_SOURCE_TOKEN_ENV = "ZOLT_SOURCE_READ_TOKEN";
    public static final String ZAP_CHANNEL = "zap";
    public static final String ZAP_DISTRIBUTION_ORIGIN = "https://dist.zolt.sh";
    public static final String ZAP_DISTRIBUTION_BUCKET = "zolt-dist";
    public static final String ZAP_SIGNING_KEY_ID = "zolt-release-2026";
    public static final String ZAP_SIGNING_PUBLIC_KEY =
            "MCowBQYDK2VwAyEAn6cIrOCATTABSbWHl34vlZlP6xu/sFN8rxKga+/M/ZU=";
    public static final int RELEASE_RECORD_SCHEMA_VERSION = 1;
    public static final String CANDIDATE_STATE = "candidate";
    public static final List<String> RELEASE_TARGETS =
            List.of("linux-x64", "linux-arm64", "macos-arm64", "macos-x64");

    private ReleaseConstants() {}
}
