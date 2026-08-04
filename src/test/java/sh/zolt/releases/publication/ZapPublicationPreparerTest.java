package sh.zolt.releases.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

final class ZapPublicationPreparerTest {
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String CONTROLLER_SHA = "b".repeat(40);
    private static final String SOURCE_RUN_ID = "123";
    private static final String CONTROLLER_RUN_ID = "456";
    private static final String VERSION = "0.1.0-zap.20260803." + "a".repeat(12);

    @TempDir
    Path temporary;

    private Path candidates;
    private Path evidence;
    private Path record;
    private Path previousChannel;
    private Path previousIndex;

    @BeforeEach
    void prepare() throws IOException {
        candidates = Files.createDirectory(temporary.resolve("candidate"));
        evidence = temporary.resolve("source-run.json");
        writeEvidence();
        for (String target : ReleaseConstants.RELEASE_TARGETS) {
            writeCandidate(target);
        }
        record = temporary.resolve("release-record.json");
        Map<String, Object> releaseRecord = new ReleaseRecordWriter().build(new ReleaseRecordRequest(
                "zap",
                ReleaseConstants.SOURCE_REPOSITORY,
                SOURCE_SHA,
                SOURCE_RUN_ID,
                null,
                VERSION,
                evidence,
                CONTROLLER_SHA,
                CONTROLLER_RUN_ID,
                candidates));
        JsonSupport.write(record, releaseRecord);
        previousChannel = temporary.resolve("previous-channel.json");
        previousIndex = temporary.resolve("previous-index.json");
        writePrevious();
    }

    @Test
    void stagesDeterministicCompletePublication() throws IOException {
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");

        assertEquals(VERSION, new ZapPublicationPreparer().prepare(request(first)));
        assertEquals(VERSION, new ZapPublicationPreparer().prepare(request(second)));

        assertTreesEqual(first, second);
        assertEquals(
                SOURCE_SHA,
                JsonSupport.read(first.resolve("channels/zap.json")).path("commit").asText());
        String archiveUrl = JsonSupport.read(first.resolve("channels/zap.json"))
                .path("artifacts")
                .get(0)
                .path("archiveUrl")
                .asText();
        assertTrue(archiveUrl.startsWith(
                "https://github.com/zoltsh/releases/releases/download/zolt-zap-" + VERSION + "/"));
        assertEquals(
                1,
                JsonSupport.read(first.resolve("releases/zap.json"))
                        .path("versions")
                        .size(),
                "legacy Spaces-backed index entries must be pruned");
        assertEquals(
                SOURCE_SHA,
                JsonSupport.read(first.resolve("artifacts/zap")
                                .resolve(VERSION)
                                .resolve("release-manifest.json"))
                        .path("builder")
                        .path("commit")
                        .asText());
        assertTrue(Files.isRegularFile(first.resolve("artifacts/zap")
                .resolve(VERSION)
                .resolve("release-record.json")));
        assertTrue(Files.isRegularFile(first.resolve("artifacts/zap")
                .resolve(VERSION)
                .resolve("source-run.json")));
    }

    @Test
    void rejectsCandidateTamperingAfterReleaseRecord() throws IOException {
        Path manifest = candidates.resolve("zolt-zap-linux-x64/release-manifest.json");
        Files.writeString(manifest, Files.readString(manifest) + " ");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ZapPublicationPreparer().prepare(request(temporary.resolve("output"))));

        assertTrue(error.getMessage().contains("file identities"));
    }

    @Test
    void rejectsReleaseRecordDigestMismatch() {
        ObjectNode document = (ObjectNode) JsonSupport.read(record);
        ((ObjectNode) document.path("artifacts").get(0)).put("sha256", "f".repeat(64));
        JsonSupport.write(record, document);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ZapPublicationPreparer().prepare(request(temporary.resolve("output"))));

        assertTrue(error.getMessage().contains("file identities"));
    }

    @Test
    void rejectsControllerIdentityMismatch() {
        ZapPublicationRequest request = request(temporary.resolve("output"));
        ZapPublicationRequest wrong = new ZapPublicationRequest(
                request.releaseRecord(),
                request.sourceEvidence(),
                request.candidates(),
                request.previousChannel(),
                request.previousIndex(),
                "c".repeat(40),
                request.expectedControllerRunId(),
                request.output());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ZapPublicationPreparer().prepare(wrong));

        assertTrue(error.getMessage().contains("controller commit"));
    }

    private ZapPublicationRequest request(Path output) {
        return new ZapPublicationRequest(
                record,
                evidence,
                candidates,
                previousChannel,
                previousIndex,
                CONTROLLER_SHA,
                CONTROLLER_RUN_ID,
                output);
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
        document.put("runCreatedAt", "2026-08-03T12:00:00Z");
        document.put("runStartedAt", "2026-08-03T12:00:01Z");
        document.putObject("jobs");
        JsonSupport.write(evidence, document);
    }

    private void writeCandidate(String target) throws IOException {
        Path directory = Files.createDirectory(candidates.resolve("zolt-zap-" + target));
        String archiveName = "zolt-" + VERSION + "-" + target + ".tar.gz";
        Path archive = Files.writeString(directory.resolve(archiveName), "archive-" + target);
        String digest = FileDigests.sha256(archive);
        Files.writeString(
                directory.resolve(archiveName + ".sha256"), digest + "  " + archiveName + "\n");

        ObjectNode manifest = JsonSupport.mapper().createObjectNode();
        manifest.put("name", "zolt");
        manifest.put("version", VERSION);
        ObjectNode builder = manifest.putObject("builder");
        builder.put("name", "zolt");
        builder.put("version", VERSION);
        builder.put("jdkVersion", "21.0.11");
        builder.put("jdkVendor", "GraalVM Community");
        builder.put("builtAt", "2026-08-03T12:10:00Z");
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

    private void writePrevious() {
        String version = "0.1.0-zap.20260728." + "d".repeat(12);
        String commit = "d".repeat(40);
        ObjectNode channel = JsonSupport.mapper().createObjectNode();
        channel.put("schemaVersion", 1);
        channel.put("channel", "zap");
        channel.put("version", version);
        channel.put("commit", commit);
        channel.put("createdAt", "2026-07-28T11:04:26Z");
        ArrayNode artifacts = channel.putArray("artifacts");
        ObjectNode artifact = artifacts.addObject();
        String archive = "zolt-" + version + "-linux-x64.tar.gz";
        String url = "https://dist.zolt.sh/artifacts/zap/" + version + "/" + archive;
        artifact.put("target", "linux-x64");
        artifact.put("archive", archive);
        artifact.put("archiveUrl", url);
        artifact.put("checksumUrl", url + ".sha256");
        artifact.put("sha256", "e".repeat(64));
        artifact.put("format", "tar.gz");
        artifact.put("binaryName", "zolt");
        JsonSupport.write(previousChannel, channel);

        ObjectNode index = JsonSupport.mapper().createObjectNode();
        index.put("schemaVersion", 1);
        index.put("channel", "zap");
        index.put("updatedAt", "2026-07-28T11:04:26Z");
        ObjectNode entry = index.putArray("versions").addObject();
        entry.put("version", version);
        entry.put("commit", commit);
        entry.put("createdAt", "2026-07-28T11:04:26Z");
        entry.set("artifacts", artifacts.deepCopy());
        JsonSupport.write(previousIndex, index);
    }

    private static void assertTreesEqual(Path first, Path second) throws IOException {
        List<Path> firstFiles;
        try (var paths = Files.walk(first)) {
            firstFiles = paths.filter(Files::isRegularFile)
                    .map(first::relativize)
                    .sorted()
                    .toList();
        }
        List<Path> secondFiles;
        try (var paths = Files.walk(second)) {
            secondFiles = paths.filter(Files::isRegularFile)
                    .map(second::relativize)
                    .sorted()
                    .toList();
        }
        assertEquals(firstFiles, secondFiles);
        for (Path relative : firstFiles) {
            assertEquals(
                    -1,
                    java.util.Arrays.mismatch(
                            Files.readAllBytes(first.resolve(relative)),
                            Files.readAllBytes(second.resolve(relative))));
        }
    }
}
