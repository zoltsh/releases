package sh.zolt.releases.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.io.JsonSupport;
import sh.zolt.releases.policy.ReleaseChannel;

final class ReleaseMetadataWriter {
    private static final int RELEASE_INDEX_LIMIT = 200;
    private static final String CORE_VERSION =
            "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)";
    private static final Pattern ZAP_VERSION = Pattern.compile(
            "^" + CORE_VERSION + "-zap\\.[0-9]{8}\\.[0-9a-f]{12}$");
    private static final Pattern PREVIEW_VERSION = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "-(alpha|beta|rc)\\.(0|[1-9][0-9]*)$");
    private final PublicationSchemaValidator schemaValidator;

    ReleaseMetadataWriter() {
        this(new PublicationSchemaValidator());
    }

    ReleaseMetadataWriter(PublicationSchemaValidator schemaValidator) {
        this.schemaValidator = schemaValidator;
    }

    void write(
            ValidatedCandidateRelease release,
            Path previousChannelPath,
            Path previousIndexPath,
            Path output) {
        JsonNode previousChannel = readPrevious(previousChannelPath, "current channel");
        JsonNode previousIndex = readPrevious(previousIndexPath, "current release index");
        if ((previousChannel == null) != (previousIndex == null)) {
            throw new IllegalArgumentException(
                    "current channel and release index must either both exist or both be absent");
        }
        if (previousChannel == null && release.channel() != ReleaseChannel.PREVIEW) {
            throw new IllegalArgumentException(
                    release.channel().id() + " publication requires current signed metadata");
        }
        if (previousChannel != null) {
            validatePrevious(release.channel(), previousChannel, previousIndex);
            validateProgression(release, previousChannel);
        }

        ObjectNode channel = channel(release);
        ObjectNode index = index(release, previousIndex);
        ObjectNode manifest = releaseManifest(release);
        schemaValidator.validateChannel(channel);
        schemaValidator.validateIndex(index);
        Path root = emptyOutput(output);
        Path artifactRoot = root.resolve("artifacts")
                .resolve(release.channel().id())
                .resolve(release.version());
        try {
            Files.createDirectories(artifactRoot);
            for (ValidatedCandidateRelease.Archive artifact : sortedArchives(release)) {
                Files.copy(artifact.archive(), artifactRoot.resolve(artifact.archive().getFileName()));
                Files.copy(
                        artifact.checksum(),
                        artifactRoot.resolve(artifact.checksum().getFileName()));
            }
            Files.copy(release.releaseRecord(), artifactRoot.resolve("release-record.json"));
            Files.copy(release.sourceEvidence(), artifactRoot.resolve("source-run.json"));
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not stage immutable " + release.channel().id() + " files: "
                            + exception.getMessage(),
                    exception);
        }
        JsonSupport.write(artifactRoot.resolve("release-manifest.json"), manifest);
        JsonSupport.write(
                root.resolve("channels").resolve(release.channel().id() + ".json"), channel);
        JsonSupport.write(
                root.resolve("releases").resolve(release.channel().id() + ".json"), index);
    }

    private static ObjectNode channel(ValidatedCandidateRelease release) {
        ObjectNode channel = JsonSupport.mapper().createObjectNode();
        channel.put("schemaVersion", 1);
        channel.put("channel", release.channel().id());
        channel.put("version", release.version());
        channel.put("commit", release.sourceSha());
        channel.put("createdAt", release.createdAt());
        ArrayNode artifacts = channel.putArray("artifacts");
        for (ValidatedCandidateRelease.Archive artifact : sortedArchives(release)) {
            ObjectNode entry = artifacts.addObject();
            String archive = artifact.archive().getFileName().toString();
            String archiveUrl = ReleaseConstants.RELEASE_ASSET_ORIGIN
                    + "/"
                    + releaseTag(release.channel(), release.version())
                    + "/"
                    + archive;
            entry.put("target", artifact.target());
            entry.put("archive", archive);
            entry.put("archiveUrl", archiveUrl);
            entry.put("checksumUrl", archiveUrl + ".sha256");
            entry.put("sha256", artifact.sha256());
            entry.put("format", "tar.gz");
            entry.put("binaryName", "zolt");
        }
        return channel;
    }

    private static ObjectNode releaseManifest(ValidatedCandidateRelease release) {
        ObjectNode manifest = JsonSupport.mapper().createObjectNode();
        manifest.put("name", "zolt");
        manifest.put("version", release.version());
        manifest.set("builder", release.builder());
        ArrayNode archives = manifest.putArray("archives");
        for (ValidatedCandidateRelease.Archive artifact : sortedArchives(release)) {
            ObjectNode entry = archives.addObject();
            entry.put("archive", artifact.archive().getFileName().toString());
            entry.put("target", artifact.target());
            entry.put("version", release.version());
            entry.put("format", "tar.gz");
            entry.put("sha256", artifact.sha256());
        }
        return manifest;
    }

    private static ObjectNode index(
            ValidatedCandidateRelease release, JsonNode previousIndex) {
        ObjectNode index = JsonSupport.mapper().createObjectNode();
        index.put("schemaVersion", 1);
        index.put("channel", release.channel().id());
        index.put("updatedAt", release.createdAt());
        ArrayNode versions = index.putArray("versions");
        versions.add(versionEntry(channel(release)));
        if (previousIndex != null) {
            for (JsonNode previous : previousIndex.path("versions")) {
                if (versions.size() >= RELEASE_INDEX_LIMIT) {
                    break;
                }
                if (!release.version().equals(previous.path("version").asText())
                        && usesImmutableGitHubAssets(release.channel(), previous)) {
                    versions.add(previous.deepCopy());
                }
            }
        }
        return index;
    }

    private static ObjectNode versionEntry(JsonNode channel) {
        ObjectNode version = JsonSupport.mapper().createObjectNode();
        version.put("version", channel.path("version").asText());
        version.put("commit", channel.path("commit").asText());
        version.put("createdAt", channel.path("createdAt").asText());
        version.set("artifacts", channel.path("artifacts").deepCopy());
        return version;
    }

    private static JsonNode readPrevious(Path path, String description) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(description + " does not exist: " + normalized);
        }
        return JsonSupport.read(normalized);
    }

    private static void validatePrevious(
            ReleaseChannel releaseChannel, JsonNode channel, JsonNode index) {
        validateChannel(releaseChannel, channel);
        String description = releaseChannel.id() + " release index";
        requireInt(index, "schemaVersion", 1, description);
        requireText(index, "channel", releaseChannel.id(), description);
        instant(text(index, "updatedAt", description), description + " updatedAt");
        JsonNode versions = index.path("versions");
        if (!versions.isArray() || versions.isEmpty() || versions.size() > RELEASE_INDEX_LIMIT) {
            throw new IllegalArgumentException(
                    description + " must contain between 1 and " + RELEASE_INDEX_LIMIT + " versions");
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode version : versions) {
            validateVersion(releaseChannel, version, description + " version");
            validateArtifactLocations(releaseChannel, version, releaseChannel == ReleaseChannel.ZAP);
            if (!seen.add(version.path("version").asText())) {
                throw new IllegalArgumentException(description + " repeats a version");
            }
        }
        if (!versionEntry(channel).equals(versions.get(0))) {
            throw new IllegalArgumentException(
                    "current " + releaseChannel.id()
                            + " channel does not match the first release index version");
        }
    }

    private static void validateChannel(ReleaseChannel releaseChannel, JsonNode channel) {
        String description = releaseChannel.id() + " channel";
        requireInt(channel, "schemaVersion", 1, description);
        requireText(channel, "channel", releaseChannel.id(), description);
        validateVersion(releaseChannel, channel, description);
        validateArtifactLocations(releaseChannel, channel, releaseChannel == ReleaseChannel.ZAP);
    }

    private static void validateArtifactLocations(
            ReleaseChannel channel, JsonNode versionNode, boolean allowLegacy) {
        String version = versionNode.path("version").asText();
        String githubPrefix = ReleaseConstants.RELEASE_ASSET_ORIGIN
                + "/"
                + releaseTag(channel, version)
                + "/";
        String legacyPrefix =
                "https://dist.zolt.sh/artifacts/" + channel.id() + "/" + version + "/";
        for (JsonNode artifact : versionNode.path("artifacts")) {
            String target = artifact.path("target").asText();
            String archive = artifact.path("archive").asText();
            String expectedArchive = "zolt-" + version + "-" + target + ".tar.gz";
            if (!archive.equals(expectedArchive)) {
                throw new IllegalArgumentException(
                        "current " + channel.id()
                                + " metadata archive does not match its version and target");
            }
            String archiveUrl = artifact.path("archiveUrl").asText();
            String checksumUrl = artifact.path("checksumUrl").asText();
            boolean github = archiveUrl.equals(githubPrefix + archive)
                    && checksumUrl.equals(githubPrefix + archive + ".sha256");
            boolean legacy = allowLegacy
                    && archiveUrl.equals(legacyPrefix + archive)
                    && checksumUrl.equals(legacyPrefix + archive + ".sha256");
            if (!github && !legacy) {
                throw new IllegalArgumentException(
                        "current " + channel.id()
                                + " metadata contains an artifact outside its exact release location");
            }
        }
    }

    private static boolean usesImmutableGitHubAssets(
            ReleaseChannel channel, JsonNode versionNode) {
        try {
            validateArtifactLocations(channel, versionNode, false);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String releaseTag(ReleaseChannel channel, String version) {
        return switch (channel) {
            case ZAP -> "zolt-zap-" + version;
            case PREVIEW -> "zolt-preview-v" + version;
            case STABLE -> "zolt-v" + version;
        };
    }

    private static void validateVersion(
            ReleaseChannel channel, JsonNode version, String description) {
        String releaseVersion = text(version, "version", description);
        if (!versionPattern(channel).matcher(releaseVersion).matches()) {
            throw new IllegalArgumentException(
                    description + " has an invalid " + channel.id() + " version");
        }
        String commit = text(version, "commit", description);
        if (!commit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(description + " has an invalid commit");
        }
        instant(text(version, "createdAt", description), description + " createdAt");
        JsonNode artifacts = version.path("artifacts");
        if (!artifacts.isArray() || artifacts.isEmpty()) {
            throw new IllegalArgumentException(description + " must contain artifacts");
        }
        Set<String> targets = new HashSet<>();
        for (JsonNode artifact : artifacts) {
            String target = text(artifact, "target", description + " artifact");
            if (!targets.add(target)) {
                throw new IllegalArgumentException(description + " repeats target " + target);
            }
            text(artifact, "archive", description + " artifact");
            text(artifact, "archiveUrl", description + " artifact");
            text(artifact, "checksumUrl", description + " artifact");
            if (!text(artifact, "sha256", description + " artifact").matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(description + " artifact has invalid SHA-256");
            }
            requireText(artifact, "format", "tar.gz", description + " artifact");
            requireText(artifact, "binaryName", "zolt", description + " artifact");
        }
    }

    private static Pattern versionPattern(ReleaseChannel channel) {
        return switch (channel) {
            case ZAP -> ZAP_VERSION;
            case PREVIEW -> PREVIEW_VERSION;
            case STABLE -> throw new IllegalArgumentException("stable publication is not enabled");
        };
    }

    private static void validateProgression(
            ValidatedCandidateRelease release, JsonNode previousChannel) {
        String previousVersion = previousChannel.path("version").asText();
        if (release.version().equals(previousVersion)) {
            if (!channel(release).equals(previousChannel)) {
                throw new IllegalArgumentException(
                        "an existing " + release.channel().id()
                                + " version cannot be republished with different metadata");
            }
            return;
        }
        if (release.channel() == ReleaseChannel.PREVIEW
                && comparePreview(release.version(), previousVersion) <= 0) {
            throw new IllegalArgumentException(
                    "new preview version must be greater than the current preview version");
        }
        String previousCreatedAt = previousChannel.path("createdAt").asText();
        if (!Instant.parse(release.createdAt()).isAfter(Instant.parse(previousCreatedAt))) {
            throw new IllegalArgumentException(
                    "new " + release.channel().id()
                            + " candidate must be newer than the current channel metadata");
        }
    }

    private static int comparePreview(String left, String right) {
        Matcher leftMatch = PREVIEW_VERSION.matcher(left);
        Matcher rightMatch = PREVIEW_VERSION.matcher(right);
        if (!leftMatch.matches() || !rightMatch.matches()) {
            throw new IllegalArgumentException("cannot compare invalid preview versions");
        }
        for (int group : new int[] {1, 2, 3}) {
            int comparison = new BigInteger(leftMatch.group(group))
                    .compareTo(new BigInteger(rightMatch.group(group)));
            if (comparison != 0) {
                return comparison;
            }
        }
        int qualifier = Integer.compare(
                qualifierRank(leftMatch.group(4)), qualifierRank(rightMatch.group(4)));
        return qualifier != 0
                ? qualifier
                : new BigInteger(leftMatch.group(5))
                        .compareTo(new BigInteger(rightMatch.group(5)));
    }

    private static int qualifierRank(String qualifier) {
        return switch (qualifier) {
            case "alpha" -> 0;
            case "beta" -> 1;
            case "rc" -> 2;
            default -> throw new IllegalArgumentException("unsupported preview qualifier");
        };
    }

    private static List<ValidatedCandidateRelease.Archive> sortedArchives(
            ValidatedCandidateRelease release) {
        return release.archives().values().stream()
                .sorted(java.util.Comparator.comparing(ValidatedCandidateRelease.Archive::target))
                .toList();
    }

    private static Path emptyOutput(Path output) {
        if (output == null) {
            throw new IllegalArgumentException("publication output path is required");
        }
        Path normalized = output.toAbsolutePath().normalize();
        try {
            if (Files.isRegularFile(normalized)) {
                throw new IllegalArgumentException("publication output is a file: " + normalized);
            }
            if (Files.isDirectory(normalized)) {
                try (var entries = Files.list(normalized)) {
                    if (entries.findAny().isPresent()) {
                        throw new IllegalArgumentException(
                                "publication output must be empty: " + normalized);
                    }
                }
            } else {
                Files.createDirectories(normalized);
            }
            return normalized;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not prepare publication output: " + exception.getMessage(), exception);
        }
    }

    private static String text(JsonNode parent, String field, String description) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(description + " is missing text \"" + field + "\"");
        }
        return value.asText();
    }

    private static void requireText(
            JsonNode parent, String field, String expected, String description) {
        String actual = text(parent, field, description);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    description + " must use " + field + " \"" + expected + "\"");
        }
    }

    private static void requireInt(
            JsonNode parent, String field, int expected, String description) {
        if (!parent.path(field).isInt() || parent.path(field).asInt() != expected) {
            throw new IllegalArgumentException(
                    description + " must use " + field + " " + expected);
        }
    }

    private static void instant(String value, String description) {
        if (!value.endsWith("Z")) {
            throw new IllegalArgumentException(description + " is not a UTC instant ending in Z");
        }
        try {
            Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(description + " is not a UTC instant", exception);
        }
    }
}
