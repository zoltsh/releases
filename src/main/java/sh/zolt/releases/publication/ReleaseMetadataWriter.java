package sh.zolt.releases.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.io.JsonSupport;

final class ReleaseMetadataWriter {
    private static final int RELEASE_INDEX_LIMIT = 200;
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
        JsonNode previousChannel = readPrevious(previousChannelPath, "current zap channel");
        JsonNode previousIndex = readPrevious(previousIndexPath, "current zap release index");
        schemaValidator.validateChannel(previousChannel);
        schemaValidator.validateIndex(previousIndex);
        validatePrevious(previousChannel, previousIndex);
        validateProgression(release, previousChannel);

        ObjectNode channel = channel(release);
        ObjectNode index = index(release, previousIndex);
        ObjectNode manifest = releaseManifest(release);
        schemaValidator.validateChannel(channel);
        schemaValidator.validateIndex(index);
        Path root = emptyOutput(output);
        Path artifactRoot = root.resolve("artifacts/zap").resolve(release.version());
        try {
            Files.createDirectories(artifactRoot);
            for (ValidatedCandidateRelease.Archive artifact : release.archives().values().stream()
                    .sorted(java.util.Comparator.comparing(ValidatedCandidateRelease.Archive::target))
                    .toList()) {
                Files.copy(artifact.archive(), artifactRoot.resolve(artifact.archive().getFileName()));
                Files.copy(
                        artifact.checksum(),
                        artifactRoot.resolve(artifact.checksum().getFileName()));
            }
            Files.copy(release.releaseRecord(), artifactRoot.resolve("release-record.json"));
            Files.copy(release.sourceEvidence(), artifactRoot.resolve("source-run.json"));
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not stage immutable zap files: " + exception.getMessage(), exception);
        }
        JsonSupport.write(artifactRoot.resolve("release-manifest.json"), manifest);
        JsonSupport.write(root.resolve("channels/zap.json"), channel);
        JsonSupport.write(root.resolve("releases/zap.json"), index);
    }

    private static ObjectNode channel(ValidatedCandidateRelease release) {
        ObjectNode channel = JsonSupport.mapper().createObjectNode();
        channel.put("schemaVersion", 1);
        channel.put("channel", ReleaseConstants.ZAP_CHANNEL);
        channel.put("version", release.version());
        channel.put("commit", release.sourceSha());
        channel.put("createdAt", release.createdAt());
        ArrayNode artifacts = channel.putArray("artifacts");
        for (ValidatedCandidateRelease.Archive artifact : release.archives().values().stream()
                .sorted(java.util.Comparator.comparing(ValidatedCandidateRelease.Archive::target))
                .toList()) {
            ObjectNode entry = artifacts.addObject();
            String archive = artifact.archive().getFileName().toString();
            String archiveUrl = ReleaseConstants.ZAP_DISTRIBUTION_ORIGIN
                    + "/artifacts/zap/"
                    + release.version()
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
        for (ValidatedCandidateRelease.Archive artifact : release.archives().values().stream()
                .sorted(java.util.Comparator.comparing(ValidatedCandidateRelease.Archive::target))
                .toList()) {
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
        index.put("channel", ReleaseConstants.ZAP_CHANNEL);
        index.put("updatedAt", release.createdAt());
        ArrayNode versions = index.putArray("versions");
        versions.add(versionEntry(channel(release)));
        for (JsonNode previous : previousIndex.path("versions")) {
            if (versions.size() >= RELEASE_INDEX_LIMIT) {
                break;
            }
            if (!release.version().equals(previous.path("version").asText())) {
                versions.add(previous.deepCopy());
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
            throw new IllegalArgumentException(description + " path is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(description + " does not exist: " + normalized);
        }
        return JsonSupport.read(normalized);
    }

    private static void validatePrevious(JsonNode channel, JsonNode index) {
        validateChannel(channel);
        requireInt(index, "schemaVersion", 1, "zap release index");
        requireText(index, "channel", ReleaseConstants.ZAP_CHANNEL, "zap release index");
        instant(text(index, "updatedAt", "zap release index"), "zap release index updatedAt");
        JsonNode versions = index.path("versions");
        if (!versions.isArray() || versions.isEmpty() || versions.size() > RELEASE_INDEX_LIMIT) {
            throw new IllegalArgumentException(
                    "zap release index must contain between 1 and " + RELEASE_INDEX_LIMIT + " versions");
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode version : versions) {
            validateVersion(version, "zap release index version");
            if (!seen.add(version.path("version").asText())) {
                throw new IllegalArgumentException("zap release index repeats a version");
            }
        }
        if (!versionEntry(channel).equals(versions.get(0))) {
            throw new IllegalArgumentException(
                    "current zap channel does not match the first release index version");
        }
    }

    private static void validateChannel(JsonNode channel) {
        requireInt(channel, "schemaVersion", 1, "zap channel");
        requireText(channel, "channel", ReleaseConstants.ZAP_CHANNEL, "zap channel");
        validateVersion(channel, "zap channel");
        for (JsonNode artifact : channel.path("artifacts")) {
            String version = channel.path("version").asText();
            String expectedPrefix = ReleaseConstants.ZAP_DISTRIBUTION_ORIGIN
                    + "/artifacts/zap/"
                    + version
                    + "/";
            if (!artifact.path("archiveUrl").asText().startsWith(expectedPrefix)
                    || !artifact.path("checksumUrl").asText().startsWith(expectedPrefix)) {
                throw new IllegalArgumentException(
                        "current zap channel contains an artifact outside dist.zolt.sh");
            }
        }
    }

    private static void validateVersion(JsonNode version, String description) {
        text(version, "version", description);
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

    private static void validateProgression(
            ValidatedCandidateRelease release, JsonNode previousChannel) {
        String previousVersion = previousChannel.path("version").asText();
        String previousCreatedAt = previousChannel.path("createdAt").asText();
        if (release.version().equals(previousVersion)) {
            if (!channel(release).equals(previousChannel)) {
                throw new IllegalArgumentException(
                        "an existing zap version cannot be republished with different metadata");
            }
            return;
        }
        if (!Instant.parse(release.createdAt()).isAfter(Instant.parse(previousCreatedAt))) {
            throw new IllegalArgumentException(
                    "new zap candidate must be newer than the current channel metadata");
        }
    }

    private static Path emptyOutput(Path output) {
        if (output == null) {
            throw new IllegalArgumentException("publication output path is required");
        }
        Path normalized = output.toAbsolutePath().normalize();
        try {
            if (Files.isRegularFile(normalized)) {
                throw new IllegalArgumentException(
                        "publication output is a file: " + normalized);
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
        try {
            Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(description + " is not a UTC instant", exception);
        }
    }
}
