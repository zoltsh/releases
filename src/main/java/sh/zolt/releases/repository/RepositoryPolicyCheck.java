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
        requireString(
                release,
                "large_artifact_backend",
                "github-releases",
                "all release assets must use immutable GitHub Releases",
                errors);
        requireString(
                release,
                "metadata_backend",
                "s3-compatible",
                "signed moving metadata must use the existing S3-compatible origin",
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
        List<TomlTable> allChannels = List.of(stable, preview, zap);
        for (TomlTable configuredChannel : allChannels) {
            requireString(
                    configuredChannel,
                    "origin",
                    "https://dist.zolt.sh",
                    "all channels must use the shared signed metadata origin",
                    errors);
            requireString(
                    configuredChannel,
                    "bucket",
                    "zolt-dist",
                    "all channels must use the shared bootstrap and metadata zolt-dist Space",
                    errors);
        }
        distinct(
                allChannels,
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
        requireBoolean(
                mainRules,
                "pull_request_required",
                true,
                "main changes must use pull requests",
                errors);
        Long approvals = mainRules.getLong("required_approvals");
        if (approvals == null || approvals < 2) {
            errors.add("release-controller changes must require at least two approvals");
        }
        requireBoolean(
                mainRules,
                "code_owner_review_required",
                true,
                "main changes must require CODEOWNER review",
                errors);
        requireBoolean(
                mainRules,
                "latest_push_approval_required",
                true,
                "main changes must require approval of the latest push",
                errors);
        requireBoolean(
                mainRules,
                "dismiss_stale_reviews_on_push",
                true,
                "new pushes must dismiss stale reviews",
                errors);
        requireBoolean(
                mainRules,
                "conversations_resolved",
                true,
                "main changes must resolve review conversations",
                errors);
        requireString(
                mainRules,
                "required_status_check",
                "repository",
                "main must require the repository validation check",
                errors);
        requireLong(
                mainRules,
                "required_status_check_app_id",
                15368L,
                "the repository check must come from GitHub Actions",
                errors);
        requireBoolean(
                mainRules,
                "strict_status_checks",
                true,
                "main must require checks against the latest base",
                errors);
        requireBoolean(
                mainRules,
                "force_push_allowed",
                false,
                "main must reject force pushes",
                errors);
        requireBoolean(
                mainRules,
                "deletion_allowed",
                false,
                "main must reject deletion",
                errors);
        requireBoolean(
                mainRules,
                "normal_bypass_allowed",
                false,
                "main must not allow a normal bypass",
                errors);
        TomlTable actions = table(settings, "actions", errors);
        requireBoolean(
                actions,
                "full_sha_pinning_required",
                true,
                "external actions must be pinned to full commit SHAs",
                errors);
        requireBoolean(
                actions,
                "github_owned_allowed",
                true,
                "GitHub-owned actions must remain available",
                errors);
        requireBoolean(
                actions,
                "verified_creators_allowed",
                false,
                "verified creators must not be allowed without review",
                errors);
        if (!stringArray(actions, "allowed_patterns")
                .equals(List.of("graalvm/setup-graalvm@*", "zoltsh/setup-zolt@*"))) {
            errors.add("the external action allowlist must contain only reviewed dependencies");
        }
        requireBoolean(
                actions,
                "self_hosted_privileged_release_runners",
                false,
                "publication credentials must not run on self-hosted runners",
                errors);
        TomlTable environments = table(settings, "environments", errors);
        TomlTable zapEnvironment = table(environments, "channel_zap", errors);
        TomlTable previewEnvironment = table(environments, "channel_preview", errors);
        TomlTable stableEnvironment = table(environments, "channel_stable", errors);
        requireLong(
                zapEnvironment,
                "reviewers",
                0L,
                "channel-zap must not require a reviewer",
                errors);
        requireString(
                zapEnvironment,
                "deployment_ref_type",
                "branch",
                "channel-zap deployments must use a branch policy",
                errors);
        requireString(
                zapEnvironment,
                "deployment_ref_pattern",
                "main",
                "channel-zap deployments must be restricted to main",
                errors);
        requireString(
                previewEnvironment,
                "deployment_ref_type",
                "tag",
                "channel-preview deployments must use a tag policy",
                errors);
        requireString(
                previewEnvironment,
                "deployment_ref_pattern",
                "zolt-preview-*",
                "channel-preview deployments must use protected preview tags",
                errors);
        requireString(
                stableEnvironment,
                "deployment_ref_type",
                "tag",
                "channel-stable deployments must use a tag policy",
                errors);
        requireString(
                stableEnvironment,
                "deployment_ref_pattern",
                "zolt-v*",
                "channel-stable deployments must use protected stable tags",
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
