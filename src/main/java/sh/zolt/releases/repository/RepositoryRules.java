package sh.zolt.releases.repository;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class RepositoryRules {
    static final Pattern PINNED_ACTION =
            Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$");
    static final Pattern USES =
            Pattern.compile("^\\s*-?\\s*uses:\\s*['\"]?([^'\"\\s#]+)", Pattern.MULTILINE);
    static final Pattern TOP_LEVEL_PERMISSIONS =
            Pattern.compile("^permissions:\\s*(?:\\{\\}|$)", Pattern.MULTILINE);
    static final Pattern PINNED_ZOLT_ARCHIVE = Pattern.compile(
            "ZOLT_ARCHIVE_URL:\\s*https://dist\\.zolt\\.sh/artifacts/zap/"
                    + "[^/\\s]+/zolt-[^/\\s]+-linux-x64\\.tar\\.gz");
    static final Pattern PINNED_ZOLT_CHECKSUM =
            Pattern.compile("ZOLT_ARCHIVE_SHA256:\\s*[0-9a-f]{64}");
    static final Pattern OTHER_TOOLCHAIN = Pattern.compile(
            "\\b(?:python3?|node|npm|typescript)\\b|\\.py\\b|\\.ts\\b|\\bunittest\\b",
            Pattern.CASE_INSENSITIVE);

    static final Set<String> IGNORED_DIRECTORIES =
            Set.of(".git", ".zolt", "candidate", "node_modules", "out", "target");
    static final Set<String> TEXT_EXTENSIONS =
            Set.of("", ".json", ".md", ".sh", ".toml", ".yaml", ".yml");
    static final Set<String> BINARY_EXTENSIONS =
            Set.of(".bundle", ".class", ".gz", ".jar", ".jpg", ".png", ".woff2", ".zip");
    static final Set<String> NON_JAVA_EXTENSIONS = Set.of(".js", ".py", ".pyc", ".ts");
    static final List<String> SECRET_MARKERS = List.of(
            "BEGIN PRIVATE KEY",
            "BEGIN OPENSSH PRIVATE KEY",
            "BEGIN RSA PRIVATE KEY",
            "AWS_SECRET_ACCESS_KEY=",
            "ZOLT_CHANNEL_PRIVATE_KEY=");
    static final List<String> REQUIRED_FILES = List.of(
            "README.md",
            "SECURITY.md",
            ".github/CODEOWNERS",
            ".github/actions/setup-zolt/action.yml",
            ".github/dependabot.yml",
            ".github/workflows/validate.yml",
            ".github/workflows/zap-candidate.yml",
            ".gitattributes",
            "docs/ARCHITECTURE.md",
            "docs/RUNBOOKS.md",
            "docs/SETUP.md",
            "policy/channels.toml",
            "policy/repository-settings.toml",
            "schemas/channel-envelope-v1.schema.json",
            "schemas/release-record-v1.schema.json",
            "scripts/bootstrap.sh",
            "scripts/check",
            "source-integration/CODEOWNERS",
            "source-integration/dispatch-zap.yml",
            "src/main/java/sh/zolt/releases/cli/ReleaseController.java",
            "src/main/java/sh/zolt/releases/core/ReleaseConstants.java",
            "src/main/java/sh/zolt/releases/repository/RepositoryValidator.java",
            "zolt.lock",
            "zolt.toml");
    static final List<String> CANDIDATE_WORKFLOW_FILES = List.of(
            ".github/actions/setup-zolt/action.yml",
            ".github/workflows/zap-candidate.yml",
            "scripts/bootstrap.sh",
            "source-integration/CODEOWNERS",
            "source-integration/dispatch-zap.yml");
    static final List<String> TRUSTED_CONTROLLER_WORKFLOWS = List.of(
            ".github/workflows/preview.yml",
            ".github/workflows/stable.yml",
            ".github/workflows/zap-candidate.yml");
    static final String ZAP_CANDIDATE_WORKFLOW = ".github/workflows/zap-candidate.yml";
    static final String TRUSTED_ZOLT_SETUP = "uses: ./.github/actions/setup-zolt";
    static final String LOCKED_ZOLT_RESOLVE =
            "zolt resolve --locked --quiet --no-progress --color never";
    static final String TRUSTED_ZOLT_RUN = "zolt run ";
    static final String CANDIDATE_BUILD_JOB = "\n  build:\n";
    static final String SOURCE_CHECKOUT = "git -C source checkout --detach";
    static final String SOURCE_TOOLCHAIN_SYNC =
            "scripts/bootstrap-zolt-jvm --no-progress toolchain sync";
    static final String SOURCE_ZAP_DISTRIBUTION = "scripts/zap-distribution --target";
    static final List<String> CANDIDATE_WORKFLOW_FRAGMENTS = List.of(
            "verify-source-run",
            "compute-zap-version",
            "ZOLT_RELEASE_VERSION:",
            "--expected-version",
            "--source-evidence",
            "./.github/actions/setup-zolt");
    static final List<String> FORBIDDEN_DISPATCHER_FRAGMENTS = List.of(
            "permission-contents: write",
            "DO_SPACES_",
            "PRIVATE_KEY: ${{ secrets.ZOLT_CHANNEL");
    static final List<String> SENSITIVE_SOURCE_PATHS =
            List.of("/.github/workflows/", "/.github/CODEOWNERS", "/scripts/*release*");

    private RepositoryRules() {}
}
