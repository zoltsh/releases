package sh.zolt.releases.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.io.TomlSupport;

final class RepositoryPolicyCheck implements RepositoryCheck {
    @Override
    public void validate(Path root, List<String> errors) {
        TomlParseResult channels = parseToml(root.resolve("policy/channels.toml"), errors);
        TomlParseResult settings =
                parseToml(root.resolve("policy/repository-settings.toml"), errors);
        if (channels == null || settings == null) {
            return;
        }

        requireLong(
                channels,
                "schema_version",
                1L,
                "policy/channels.toml must use schema_version = 1",
                errors);
        TomlTable repositories = table(channels, "repositories", errors);
        requireString(
                repositories,
                "controller",
                ReleaseConstants.CONTROLLER_REPOSITORY,
                "controller repository must be " + ReleaseConstants.CONTROLLER_REPOSITORY,
                errors);
        requireString(
                repositories,
                "asset_repository",
                ReleaseConstants.CONTROLLER_REPOSITORY,
                "release assets must be hosted in " + ReleaseConstants.CONTROLLER_REPOSITORY,
                errors);
        requireString(
                repositories,
                "primary_source",
                ReleaseConstants.SOURCE_REPOSITORY,
                "primary source must be " + ReleaseConstants.SOURCE_REPOSITORY,
                errors);
        if (!stringArray(repositories, "allowed_sources")
                .equals(List.of(ReleaseConstants.SOURCE_REPOSITORY))) {
            errors.add(ReleaseConstants.SOURCE_REPOSITORY + " must be the only allowed source");
        }

        TomlTable channel = table(channels, "channel", errors);
        Set<String> channelNames = Set.of("preview", "stable", "zap");
        if (!new TreeSet<>(channel.keySet()).equals(new TreeSet<>(channelNames))) {
            errors.add("policy must define exactly stable, preview, and zap");
        }
        TomlTable stable = table(channel, "stable", errors);
        TomlTable preview = table(channel, "preview", errors);
        TomlTable zap = table(channel, "zap", errors);
        TomlTable release = table(channels, "release", errors);
        requireString(
                release,
                "default_channel",
                "stable",
                "stable must be the default channel",
                errors);
        requireBoolean(
                stable, "default", true, "stable channel must be marked default", errors);
        requireLong(zap, "approval_count", 0L, "zap must require zero release approvals", errors);
        String zapStatus = zap.getString("status");
        if (!Set.of("candidate", "enabled").contains(zapStatus)) {
            errors.add("zap must be in candidate or enabled state");
        }
        requireLong(
                stable,
                "approval_count",
                1L,
                "stable must require one protected approval",
                errors);
        distinct(List.of(stable, preview, zap), "origin", "channel origins must be distinct", errors);
        distinct(List.of(stable, preview, zap), "bucket", "channel buckets must be distinct", errors);
        distinct(
                List.of(stable, preview, zap),
                "signing_key_id",
                "channel signing key IDs must be distinct",
                errors);

        requireString(
                settings,
                "repository",
                ReleaseConstants.CONTROLLER_REPOSITORY,
                "repository settings must target " + ReleaseConstants.CONTROLLER_REPOSITORY,
                errors);
        requireString(
                settings,
                "workflow_token_default",
                "read",
                "default workflow token permission must be read",
                errors);
        requireBoolean(
                settings,
                "actions_may_approve_pull_requests",
                false,
                "GitHub Actions must not approve pull requests",
                errors);
        requireBoolean(
                settings,
                "immutable_releases",
                true,
                "release immutability must be enabled before publication",
                errors);
        TomlTable mainRules = table(settings, "main_rules", errors);
        Long approvals = mainRules.getLong("required_approvals");
        if (approvals == null || approvals < 2) {
            errors.add("release-controller changes must require at least two approvals");
        }
        TomlTable environments = table(settings, "environments", errors);
        requireLong(
                table(environments, "channel_zap", errors),
                "reviewers",
                0L,
                "channel-zap must not require a reviewer",
                errors);

        TomlTable tagRules = table(settings, "tag_rules", errors);
        if (!new TreeSet<>(tagRules.keySet()).equals(new TreeSet<>(channelNames))) {
            errors.add("repository settings must define stable, preview, and zap tag rules");
        }
        List<TomlTable> tags = List.of(
                table(tagRules, "stable", errors),
                table(tagRules, "preview", errors),
                table(tagRules, "zap", errors));
        distinct(tags, "pattern", "release tag namespaces must be distinct", errors);
        for (int index = 0; index < tags.size(); index++) {
            String name = List.of("stable", "preview", "zap").get(index);
            TomlTable rule = tags.get(index);
            requireString(
                    rule,
                    "creation",
                    "trusted-publisher-only",
                    name + " release tags must be created only by the trusted publisher",
                    errors);
            if (!Boolean.FALSE.equals(rule.getBoolean("update_allowed"))
                    || !Boolean.FALSE.equals(rule.getBoolean("deletion_allowed"))) {
                errors.add(name + " release tags must be immutable");
            }
        }
    }

    private static TomlParseResult parseToml(Path path, List<String> errors) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        TomlParseResult result;
        try {
            result = TomlSupport.parse(path);
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
            return null;
        }
        if (result.hasErrors()) {
            errors.add("invalid TOML in " + path + ": " + result.errors().getFirst());
            return null;
        }
        return result;
    }

    private static TomlTable table(TomlTable parent, String name, List<String> errors) {
        TomlTable value = parent.getTable(name);
        if (value == null) {
            errors.add("missing TOML table " + name);
            return Toml.parse("");
        }
        return value;
    }

    private static List<String> stringArray(TomlTable table, String key) {
        TomlArray array = table.getArray(key);
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String value = array.getString(index);
            if (value == null) {
                return List.of();
            }
            values.add(value);
        }
        return values;
    }

    private static void requireString(
            TomlTable table, String key, String expected, String message, List<String> errors) {
        if (!expected.equals(table.getString(key))) {
            errors.add(message);
        }
    }

    private static void requireLong(
            TomlTable table, String key, long expected, String message, List<String> errors) {
        if (!Long.valueOf(expected).equals(table.getLong(key))) {
            errors.add(message);
        }
    }

    private static void requireBoolean(
            TomlTable table, String key, boolean expected, String message, List<String> errors) {
        if (!Boolean.valueOf(expected).equals(table.getBoolean(key))) {
            errors.add(message);
        }
    }

    private static void distinct(
            List<TomlTable> tables, String key, String message, List<String> errors) {
        Set<String> values = new HashSet<>();
        for (TomlTable table : tables) {
            String value = table.getString(key);
            if (value == null || !values.add(value)) {
                errors.add(message);
                return;
            }
        }
    }
}
