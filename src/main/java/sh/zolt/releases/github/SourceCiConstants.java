package sh.zolt.releases.github;

import java.time.Duration;
import java.util.Map;

public final class SourceCiConstants {
    public static final String API_ROOT = "https://api.github.com";
    public static final String WORKFLOW_NAME = "ci";
    public static final String WORKFLOW_PATH = ".github/workflows/ci.yml";
    public static final String EVENT = "push";
    public static final String BRANCH = "main";
    public static final String COMPLETED_STATUS = "completed";
    public static final String SUCCESS_CONCLUSION = "success";
    public static final String API_VERSION = "2026-03-10";
    public static final String USER_AGENT = "zoltsh-releases-source-verifier";
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final Map<String, Integer> EXPECTED_JOB_GROUPS = Map.of(
            "test ", 5,
            "coverage floors", 1,
            "smoke packaged workflows ", 4,
            "managed Java toolchain dogfood ", 2);

    private SourceCiConstants() {}
}
