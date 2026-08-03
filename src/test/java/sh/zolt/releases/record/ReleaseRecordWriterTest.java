package sh.zolt.releases.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sh.zolt.releases.core.ReleaseConstants;

final class ReleaseRecordWriterTest {
    private static final String VERSION = "0.1.0-zap.20260728.aaaaaaaaaaaa";
    private static final ReleaseRecordWriter WRITER = new ReleaseRecordWriter();

    @TempDir
    Path temporary;

    private Path candidates;
    private Path evidence;

    @BeforeEach
    void prepare() throws IOException {
        candidates = Files.createDirectory(temporary.resolve("candidates"));
        evidence = Files.writeString(temporary.resolve("source-run.json"), "{}\n");
    }

    @Test
    void writesCompleteFourTargetRecord() throws IOException {
        writeAllArchives();

        Map<String, Object> record = WRITER.build(request(VERSION));

        assertEquals(VERSION, record.get("version"));
        assertEquals(8, ((List<?>) record.get("artifacts")).size());
        assertEquals("candidate", record.get("state"));
    }

    @Test
    void rejectsMissingTarget() throws IOException {
        for (String target : ReleaseConstants.RELEASE_TARGETS) {
            if (!target.equals("macos-x64")) {
                writeArchive(VERSION, target);
            }
        }

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> WRITER.build(request(VERSION)));

        assertTrue(error.getMessage().contains("macos-x64"));
    }

    @Test
    void rejectsUnexpectedVersion() throws IOException {
        writeAllArchives();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WRITER.build(request("0.1.0-zap.20260729.aaaaaaaaaaaa")));

        assertTrue(error.getMessage().contains("does not match expected"));
    }

    @Test
    void rejectsChecksumMismatch() throws IOException {
        writeAllArchives();
        Files.writeString(
                candidates.resolve("zolt-" + VERSION + "-linux-x64.tar.gz"), "tampered");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> WRITER.build(request(VERSION)));

        assertTrue(error.getMessage().contains("sidecar mismatch"));
    }

    @Test
    void rejectsSymbolicLinksInCandidateDownloads() throws IOException {
        writeAllArchives();
        Files.createSymbolicLink(
                candidates.resolve("candidate-link"),
                candidates.resolve("zolt-" + VERSION + "-linux-x64.tar.gz"));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> WRITER.build(request(VERSION)));

        assertTrue(error.getMessage().contains("symbolic links"));
    }

    @Test
    void rejectsChannelOutsideSchema() throws IOException {
        writeAllArchives();

        ReleaseRecordRequest valid = request(VERSION);
        ReleaseRecordRequest invalid = new ReleaseRecordRequest(
                "nightly",
                valid.sourceRepository(),
                valid.sourceSha(),
                valid.sourceRunId(),
                valid.sourceTag(),
                valid.expectedVersion(),
                valid.sourceEvidence(),
                valid.controllerSha(),
                valid.controllerRunId(),
                valid.candidates());

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> WRITER.build(invalid));

        assertTrue(error.getMessage().contains("release record does not match"));
    }

    @Test
    void rejectsMalformedCommitOutsideSchema() throws IOException {
        writeAllArchives();

        ReleaseRecordRequest valid = request(VERSION);
        ReleaseRecordRequest invalid = new ReleaseRecordRequest(
                valid.channel(),
                valid.sourceRepository(),
                "not-a-commit",
                valid.sourceRunId(),
                valid.sourceTag(),
                valid.expectedVersion(),
                valid.sourceEvidence(),
                valid.controllerSha(),
                valid.controllerRunId(),
                valid.candidates());

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> WRITER.build(invalid));

        assertTrue(error.getMessage().contains("release record does not match"));
    }

    private ReleaseRecordRequest request(String expectedVersion) {
        return new ReleaseRecordRequest(
                "zap",
                "zoltsh/zolt",
                "a".repeat(40),
                "123",
                null,
                expectedVersion,
                evidence,
                "b".repeat(40),
                "456",
                candidates);
    }

    private void writeAllArchives() throws IOException {
        for (String target : ReleaseConstants.RELEASE_TARGETS) {
            writeArchive(VERSION, target);
        }
    }

    private void writeArchive(String version, String target) throws IOException {
        Path archive = Files.writeString(
                candidates.resolve("zolt-" + version + "-" + target + ".tar.gz"), target);
        String digest = FileDigests.sha256(archive);
        Files.writeString(
                archive.resolveSibling(archive.getFileName() + ".sha256"),
                digest + "  " + archive.getFileName() + "\n");
    }
}
