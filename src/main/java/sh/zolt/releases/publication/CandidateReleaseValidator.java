package sh.zolt.releases.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.github.SourceCiConstants;
import sh.zolt.releases.io.JsonSupport;
import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.record.CandidateArtifacts;
import sh.zolt.releases.record.FileDigests;
import sh.zolt.releases.record.ReleaseRecordValidator;

final class CandidateReleaseValidator {
    private static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final Pattern ZAP_VERSION = Pattern.compile(
            "^(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"
                    + "-zap\\.[0-9]{8}\\.[0-9a-f]{12}$");
    private static final Pattern PREVIEW_VERSION = Pattern.compile(
            "^(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"
                    + "-(?:alpha|beta|rc)\\.(?:0|[1-9][0-9]*)$");

    private final ReleaseRecordValidator releaseRecordValidator;

    CandidateReleaseValidator() {
        this(new ReleaseRecordValidator(Path.of("schemas/release-record-v1.schema.json")));
    }

    CandidateReleaseValidator(ReleaseRecordValidator releaseRecordValidator) {
        this.releaseRecordValidator = releaseRecordValidator;
    }

    ValidatedCandidateRelease validate(ReleasePublicationRequest request) {
        Path recordPath = regularFile(request.releaseRecord(), "release record");
        Path evidencePath = regularFile(request.sourceEvidence(), "source CI evidence");
        JsonNode record = JsonSupport.read(recordPath);
        releaseRecordValidator.validate(record);

        requireText(record, "channel", request.channel().id(), "release record channel");
        requireText(record, "state", ReleaseConstants.CANDIDATE_STATE, "release record state");
        String version = text(record, "version", "release record");
        if (!versionPattern(request.channel()).matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "invalid " + request.channel().id() + " version in release record: " + version);
        }
        String createdAt = text(record, "createdAt", "release record");
        parseInstant(createdAt, "release record createdAt");

        JsonNode source = object(record, "source", "release record");
        requireText(
                source,
                "repository",
                ReleaseConstants.SOURCE_REPOSITORY,
                "release record source repository");
        String sourceSha = text(source, "commit", "release record source");
        if (!SHA.matcher(sourceSha).matches()) {
            throw new IllegalArgumentException("invalid source commit in release record");
        }
        String sourceRunId = text(source, "workflowRunId", "release record source");
        validateSourceIdentity(request.channel(), source, version, sourceSha);

        JsonNode controller = object(record, "controller", "release record");
        requireText(
                controller,
                "repository",
                ReleaseConstants.CONTROLLER_REPOSITORY,
                "release record controller repository");
        requireText(
                controller,
                "commit",
                request.expectedControllerSha(),
                "release record controller commit");
        requireText(
                controller,
                "workflowRunId",
                request.expectedControllerRunId(),
                "release record controller workflow run");

        CandidateArtifacts candidates = CandidateArtifacts.load(request.candidates(), version);
        if (!artifactIdentities(candidates.entries())
                .equals(artifactIdentities(record.path("artifacts")))) {
            throw new IllegalArgumentException(
                    "downloaded candidate file identities do not match the release record");
        }
        validateCandidateFileSet(candidates);
        validateEvidence(record, evidencePath, sourceSha, sourceRunId);

        Map<String, ValidatedCandidateRelease.Archive> archives = new TreeMap<>();
        ObjectNode combinedBuilder = null;
        for (Map.Entry<String, Path> entry : new TreeMap<>(candidates.archives()).entrySet()) {
            String target = entry.getKey();
            Path archive = entry.getValue();
            Path checksum = archive.resolveSibling(archive.getFileName() + ".sha256");
            String sha256 = FileDigests.sha256(archive);
            ObjectNode builder = validateTargetManifest(
                    archive.resolveSibling("release-manifest.json"),
                    target,
                    archive.getFileName().toString(),
                    version,
                    sourceSha,
                    sha256);
            if (combinedBuilder == null) {
                combinedBuilder = builder.deepCopy();
                combinedBuilder.put("commit", sourceSha);
            }
            archives.put(
                    target,
                    new ValidatedCandidateRelease.Archive(
                            target, archive, checksum, sha256));
        }
        if (combinedBuilder == null) {
            throw new IllegalArgumentException("candidate release has no builder metadata");
        }
        return new ValidatedCandidateRelease(
                request.channel(),
                version,
                sourceSha,
                createdAt,
                recordPath,
                evidencePath,
                combinedBuilder,
                Map.copyOf(archives));
    }

    private static Pattern versionPattern(ReleaseChannel channel) {
        return switch (channel) {
            case ZAP -> ZAP_VERSION;
            case PREVIEW -> PREVIEW_VERSION;
            case STABLE -> throw new IllegalArgumentException("stable publication is not enabled");
        };
    }

    private static void validateSourceIdentity(
            ReleaseChannel channel, JsonNode source, String version, String sourceSha) {
        switch (channel) {
            case ZAP -> {
                if (!version.endsWith("." + sourceSha.substring(0, 12))) {
                    throw new IllegalArgumentException(
                            "zap version does not end with the release record source commit prefix");
                }
                requireNull(source, "tag", "zap release record source");
            }
            case PREVIEW -> requireText(
                    source,
                    "tag",
                    "v" + version,
                    "preview release record source tag");
            case STABLE -> throw new IllegalArgumentException("stable publication is not enabled");
        }
    }

    private static Map<String, ArtifactIdentity> artifactIdentities(
            List<Map<String, Object>> entries) {
        Map<String, ArtifactIdentity> identities = new LinkedHashMap<>();
        for (Map<String, Object> entry : entries) {
            String name = String.valueOf(entry.get("name"));
            String sha256 = String.valueOf(entry.get("sha256"));
            Object size = entry.get("size");
            long length = size instanceof Number number ? number.longValue() : -1;
            if (identities.put(name, new ArtifactIdentity(sha256, length)) != null) {
                throw new IllegalArgumentException("candidate artifacts repeat file " + name);
            }
        }
        return identities;
    }

    private static Map<String, ArtifactIdentity> artifactIdentities(JsonNode entries) {
        if (!entries.isArray()) {
            throw new IllegalArgumentException("release record artifacts must be an array");
        }
        Map<String, ArtifactIdentity> identities = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            String name = text(entry, "name", "release record artifact");
            String sha256 = text(entry, "sha256", "release record artifact");
            JsonNode size = entry.get("size");
            long length = size != null && size.isIntegralNumber() ? size.longValue() : -1;
            if (identities.put(name, new ArtifactIdentity(sha256, length)) != null) {
                throw new IllegalArgumentException("release record repeats artifact " + name);
            }
        }
        return identities;
    }

    private static void validateCandidateFileSet(CandidateArtifacts candidates) {
        Set<Path> expected = new LinkedHashSet<>();
        for (Path archive : candidates.archives().values()) {
            expected.add(archive.toAbsolutePath().normalize());
            expected.add(archive.resolveSibling(archive.getFileName() + ".sha256")
                    .toAbsolutePath()
                    .normalize());
            expected.add(archive.resolveSibling("release-manifest.json")
                    .toAbsolutePath()
                    .normalize());
        }
        Set<Path> actual = candidates.files().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "candidate downloads must contain exactly one archive, checksum, and release manifest per target");
        }
    }

    private static void validateEvidence(
            JsonNode record, Path evidencePath, String sourceSha, String sourceRunId) {
        JsonNode identity = object(record, "sourceEvidence", "release record");
        requireText(
                identity,
                "name",
                evidencePath.getFileName().toString(),
                "source evidence filename");
        requireText(
                identity,
                "sha256",
                FileDigests.sha256(evidencePath),
                "source evidence digest");
        if (identity.path("size").asLong(-1) != FileDigests.size(evidencePath)) {
            throw new IllegalArgumentException("source evidence size does not match release record");
        }

        JsonNode evidence = JsonSupport.read(evidencePath);
        requireText(
                evidence,
                "repository",
                ReleaseConstants.SOURCE_REPOSITORY,
                "source evidence repository");
        requireText(evidence, "workflowRunId", sourceRunId, "source evidence workflow run");
        requireText(evidence, "sourceCommit", sourceSha, "source evidence commit");
        requireText(evidence, "workflow", SourceCiConstants.WORKFLOW_NAME, "source evidence workflow");
        requireText(
                evidence,
                "workflowPath",
                SourceCiConstants.WORKFLOW_PATH,
                "source evidence workflow path");
        requireText(evidence, "event", SourceCiConstants.EVENT, "source evidence event");
        requireText(evidence, "branch", SourceCiConstants.BRANCH, "source evidence branch");
        requireText(
                evidence,
                "runConclusion",
                SourceCiConstants.SUCCESS_CONCLUSION,
                "source evidence conclusion");
        parseInstant(text(evidence, "runCreatedAt", "source evidence"), "source evidence runCreatedAt");
    }

    private static ObjectNode validateTargetManifest(
            Path manifestPath,
            String target,
            String archiveName,
            String version,
            String sourceSha,
            String sha256) {
        Path normalized = regularFile(manifestPath, "candidate release manifest");
        JsonNode manifest = JsonSupport.read(normalized);
        requireText(manifest, "name", "zolt", "candidate manifest project");
        requireText(manifest, "version", version, "candidate manifest version");
        JsonNode builder = object(manifest, "builder", "candidate manifest");
        requireText(builder, "name", "zolt", "candidate builder name");
        requireText(builder, "version", version, "candidate builder version");
        text(builder, "jdkVersion", "candidate builder");
        text(builder, "jdkVendor", "candidate builder");
        parseInstant(text(builder, "builtAt", "candidate builder"), "candidate builder builtAt");
        JsonNode commit = builder.get("commit");
        if (commit != null && !commit.isNull() && !sourceSha.equals(commit.asText())) {
            throw new IllegalArgumentException(
                    "candidate builder commit does not match release record source commit");
        }

        JsonNode archives = manifest.path("archives");
        if (!archives.isArray() || archives.size() != 1) {
            throw new IllegalArgumentException(
                    "candidate release manifest must contain exactly one archive");
        }
        JsonNode archive = archives.get(0);
        requireText(archive, "archive", archiveName, "candidate manifest archive");
        requireText(archive, "target", target, "candidate manifest target");
        requireText(archive, "version", version, "candidate manifest archive version");
        requireText(archive, "format", "tar.gz", "candidate manifest format");
        requireText(archive, "sha256", sha256, "candidate manifest archive digest");
        return ((ObjectNode) builder).deepCopy();
    }

    private static Path regularFile(Path path, String name) {
        if (path == null) {
            throw new IllegalArgumentException(name + " path is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized) || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(name + " does not exist: " + normalized);
        }
        return normalized;
    }

    private static JsonNode object(JsonNode parent, String field, String description) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) {
            throw new IllegalArgumentException(description + " is missing object \"" + field + "\"");
        }
        return value;
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
                    description + " must be \"" + expected + "\", found \"" + actual + "\"");
        }
    }

    private static void requireNull(JsonNode parent, String field, String description) {
        JsonNode value = parent.get(field);
        if (value != null && !value.isNull()) {
            throw new IllegalArgumentException(description + " must not set " + field);
        }
    }

    private static void parseInstant(String value, String description) {
        try {
            Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(description + " is not a UTC instant", exception);
        }
    }

    private record ArtifactIdentity(String sha256, long size) {}
}
