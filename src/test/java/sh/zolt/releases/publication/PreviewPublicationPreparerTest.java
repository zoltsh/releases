package sh.zolt.releases.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.io.JsonSupport;
import sh.zolt.releases.record.FileDigests;
import sh.zolt.releases.record.ReleaseRecordRequest;
import sh.zolt.releases.record.ReleaseRecordWriter;

final class PreviewPublicationPreparerTest {
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String CONTROLLER_SHA = "b".repeat(40);
    private static final String SOURCE_RUN_ID = "123";
    private static final String CONTROLLER_RUN_ID = "456";
    private static final String VERSION = "0.1.0-alpha.1";

    @TempDir
    Path temporary;

    private Path candidates;
    private Path evidence;
    private Path record;

    @BeforeEach
    void prepare() throws IOException {
        candidates = Files.createDirectory(temporary.resolve("candidate"));
        evidence = temporary.resolve("source-run.json");
        writeEvidence();
        for (String target : ReleaseConstants.RELEASE_TARGETS) {
            writeCandidate(target);
        }
        record = temporary.resolve("release-record.json");
        Map<String, Object> releaseRecord = new ReleaseRecordWriter().build(
                new ReleaseRecordRequest(
                        "preview",
                        ReleaseConstants.SOURCE_REPOSITORY,
                        SOURCE_SHA,
                        SOURCE_RUN_ID,
                        "v" + VERSION,
                        VERSION,
                        evidence,
                        CONTROLLER_SHA,
                        CONTROLLER_RUN_ID,
                        candidates));
        JsonSupport.write(record, releaseRecord);
    }

    @Test
    void stagesFirstPreviewWithoutExistingChannel() {
        Path output = temporary.resolve("publication");

        assertEquals(VERSION, new PreviewPublicationPreparer().prepare(request(output)));

        ObjectNode channel = (ObjectNode) JsonSupport.read(output.resolve("channels/preview.json"));
        assertEquals("preview", channel.path("channel").asText());
        assertEquals(SOURCE_SHA, channel.path("commit").asText());
        assertEquals(4, channel.path("artifacts").size());
        assertTrue(channel.path("artifacts")
                .get(0)
                .path("archiveUrl")
                .asText()
                .startsWith("https://github.com/zoltsh/releases/releases/download/"
                        + "zolt-preview-v"
                        + VERSION
                        + "/"));
        assertEquals(
                1,
                JsonSupport.read(output.resolve("releases/preview.json"))
                        .path("versions")
                        .size());
        assertTrue(Files.isRegularFile(output.resolve("artifacts/preview")
                .resolve(VERSION)
                .resolve("source-run.json")));
    }

    @Test
    void rejectsSourceTagThatDoesNotMatchPreviewVersion() {
        ObjectNode document = (ObjectNode) JsonSupport.read(record);
        ((ObjectNode) document.path("source")).put("tag", "v0.1.0-beta.1");
        JsonSupport.write(record, document);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PreviewPublicationPreparer()
                        .prepare(request(temporary.resolve("publication"))));

        assertTrue(error.getMessage().contains("source tag"), error.getMessage());
    }

    @Test
    void stagesPreviewThatAdvancesCurrentSignedState() {
        List<Path> previous = writePrevious("0.1.0-alpha.0");
        Path output = temporary.resolve("publication");

        assertEquals(
                VERSION,
                new PreviewPublicationPreparer()
                        .prepare(request(output, previous.get(0), previous.get(1))));

        var versions = JsonSupport.read(output.resolve("releases/preview.json"))
                .path("versions");
        assertEquals(2, versions.size());
        assertEquals(VERSION, versions.get(0).path("version").asText());
        assertEquals("0.1.0-alpha.0", versions.get(1).path("version").asText());
    }

    @Test
    void rejectsPreviewThatDoesNotAdvanceCurrentVersion() {
        List<Path> previous = writePrevious("0.1.0-alpha.2");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PreviewPublicationPreparer()
                        .prepare(request(
                                temporary.resolve("publication"),
                                previous.get(0),
                                previous.get(1))));

        assertTrue(error.getMessage().contains("must be greater"), error.getMessage());
    }

    @Test
    void rejectsPartiallyPresentCurrentMetadata() {
        List<Path> previous = writePrevious("0.1.0-alpha.0");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PreviewPublicationPreparer()
                        .prepare(request(
                                temporary.resolve("publication"), previous.get(0), null)));

        assertTrue(error.getMessage().contains("both exist or both be absent"), error.getMessage());
    }

    private PreviewPublicationRequest request(Path output) {
        return request(output, null, null);
    }

    private PreviewPublicationRequest request(
            Path output, Path previousChannel, Path previousIndex) {
        return new PreviewPublicationRequest(
                record,
                evidence,
                candidates,
                previousChannel,
                previousIndex,
                CONTROLLER_SHA,
                CONTROLLER_RUN_ID,
                output);
    }

    private List<Path> writePrevious(String version) {
        String commit = "c".repeat(40);
        String createdAt = "2026-08-23T12:00:00Z";
        ObjectNode channel = JsonSupport.mapper().createObjectNode();
        channel.put("schemaVersion", 1);
        channel.put("channel", "preview");
        channel.put("version", version);
        channel.put("commit", commit);
        channel.put("createdAt", createdAt);
        ObjectNode artifact = channel.putArray("artifacts").addObject();
        String target = "linux-x64";
        String archive = "zolt-" + version + "-" + target + ".tar.gz";
        String origin = "https://github.com/zoltsh/releases/releases/download/"
                + "zolt-preview-v"
                + version
                + "/";
        artifact.put("target", target);
        artifact.put("archive", archive);
        artifact.put("archiveUrl", origin + archive);
        artifact.put("checksumUrl", origin + archive + ".sha256");
        artifact.put("sha256", "d".repeat(64));
        artifact.put("format", "tar.gz");
        artifact.put("binaryName", "zolt");
        Path channelPath = temporary.resolve("previous-channel-" + version + ".json");
        JsonSupport.write(channelPath, channel);

        ObjectNode index = JsonSupport.mapper().createObjectNode();
        index.put("schemaVersion", 1);
        index.put("channel", "preview");
        index.put("updatedAt", createdAt);
        ObjectNode entry = index.putArray("versions").addObject();
        entry.put("version", version);
        entry.put("commit", commit);
        entry.put("createdAt", createdAt);
        entry.set("artifacts", channel.path("artifacts").deepCopy());
        Path indexPath = temporary.resolve("previous-index-" + version + ".json");
        JsonSupport.write(indexPath, index);
        return List.of(channelPath, indexPath);
    }

    private void writeEvidence() {
        ObjectNode document = JsonSupport.mapper().createObjectNode();
        document.put("repository", ReleaseConstants.SOURCE_REPOSITORY);
        document.put("workflowRunId", SOURCE_RUN_ID);
        document.put("workflow", "ci");
        document.put("workflowPath", ".github/workflows/ci.yml");
        document.put("event", "push");
        document.put("branch", "main");
        document.put("sourceCommit", SOURCE_SHA);
        document.put("runAttempt", 1);
        document.put("runConclusion", "success");
        document.put("runUrl", "https://github.com/zoltsh/zolt/actions/runs/123");
        document.put("runCreatedAt", "2026-08-24T12:00:00Z");
        document.put("runStartedAt", "2026-08-24T12:00:01Z");
        document.putObject("jobs");
        JsonSupport.write(evidence, document);
    }

    private void writeCandidate(String target) throws IOException {
        Path directory = Files.createDirectory(candidates.resolve("zolt-preview-" + target));
        String archiveName = "zolt-" + VERSION + "-" + target + ".tar.gz";
        Path archive = Files.writeString(directory.resolve(archiveName), "archive-" + target);
        String digest = FileDigests.sha256(archive);
        Files.writeString(
                directory.resolve(archiveName + ".sha256"),
                digest + "  " + archiveName + "\n");

        ObjectNode manifest = JsonSupport.mapper().createObjectNode();
        manifest.put("name", "zolt");
        manifest.put("version", VERSION);
        ObjectNode builder = manifest.putObject("builder");
        builder.put("name", "zolt");
        builder.put("version", VERSION);
        builder.put("jdkVersion", "21.0.11");
        builder.put("jdkVendor", "GraalVM Community");
        builder.put("builtAt", "2026-08-24T12:10:00Z");
        builder.putNull("commit");
        builder.putNull("resolutionFingerprint");
        ObjectNode entry = manifest.putArray("archives").addObject();
        entry.put("archive", archiveName);
        entry.put("target", target);
        entry.put("version", VERSION);
        entry.put("format", "tar.gz");
        entry.put("sha256", digest);
        JsonSupport.write(directory.resolve("release-manifest.json"), manifest);
    }
}
