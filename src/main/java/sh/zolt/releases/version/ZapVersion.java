package sh.zolt.releases.version;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import sh.zolt.releases.io.TomlSupport;

public final class ZapVersion {
    private ZapVersion() {}

    public static String readBaseVersion(Path projectFile) {
        TomlParseResult document = TomlSupport.parse(projectFile);
        if (document.hasErrors()) {
            throw new IllegalArgumentException(
                    "invalid TOML in " + projectFile + ": " + document.errors().getFirst());
        }
        TomlTable project = document.getTable("project");
        TomlTable workspace = document.getTable("workspace");
        TomlTable workspaceProject = workspace == null ? null : workspace.getTable("project");
        String projectVersion = version(project);
        String workspaceProjectVersion = version(workspaceProject);
        if (projectVersion != null
                && workspaceProjectVersion != null
                && !projectVersion.equals(workspaceProjectVersion)) {
            throw new IllegalArgumentException(
                    projectFile
                            + " contains conflicting [project].version and [workspace.project].version");
        }
        String raw = workspaceProjectVersion == null ? projectVersion : workspaceProjectVersion;
        if (raw == null) {
            throw new IllegalArgumentException(
                    projectFile
                            + " does not contain [project].version or [workspace.project].version");
        }
        String base = raw.endsWith("-SNAPSHOT")
                ? raw.substring(0, raw.length() - "-SNAPSHOT".length())
                : raw;
        if (!ZapVersionRules.BASE_VERSION.matcher(base).matches()) {
            throw new IllegalArgumentException(
                    "Zolt project version must be a semantic core version or -SNAPSHOT form, found \""
                            + raw + "\"");
        }
        return base;
    }

    private static String version(TomlTable table) {
        if (table == null) {
            return null;
        }
        String value = table.getString("version");
        return value == null || value.isBlank() ? null : value;
    }

    public static Instant parseSourceTimestamp(String value) {
        if (!ZapVersionRules.OFFSET.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "source workflow timestamp must include a UTC offset");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "invalid source workflow timestamp \"" + value + "\"", exception);
        }
    }

    public static String compute(String baseVersion, String sourceSha, Instant sourceCreatedAt) {
        String normalizedSha = sourceSha.toLowerCase(Locale.ROOT);
        if (!ZapVersionRules.SHA.matcher(normalizedSha).matches()) {
            throw new IllegalArgumentException(
                    "source SHA must be exactly 40 hexadecimal characters");
        }
        String stamp = ZapVersionRules.DATE.format(sourceCreatedAt);
        return baseVersion + "-zap." + stamp + "." + normalizedSha.substring(0, 12);
    }
}
