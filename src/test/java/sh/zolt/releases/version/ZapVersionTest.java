package sh.zolt.releases.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZapVersionTest {
    @TempDir
    Path temporary;

    @Test
    void readsSnapshotBaseAndComputesSourceRunDatedVersion() throws IOException {
        Path project = temporary.resolve("zolt.toml");
        Files.writeString(project, """
                [project]
                name = "zolt"
                version = "0.2.0-SNAPSHOT"

                [dependencies]
                """);

        String version = ZapVersion.compute(
                ZapVersion.readBaseVersion(project),
                "a".repeat(40),
                ZapVersion.parseSourceTimestamp("2026-07-29T00:00:00Z"));

        assertEquals("0.2.0-zap.20260729.aaaaaaaaaaaa", version);
    }

    @Test
    void normalizesTimestampOffsetToUtc() {
        String version = ZapVersion.compute(
                "1.2.3",
                "b".repeat(40),
                ZapVersion.parseSourceTimestamp("2026-07-28T20:00:00-05:00"));

        assertEquals("1.2.3-zap.20260729.bbbbbbbbbbbb", version);
    }

    @Test
    void rejectsNonSemanticProjectVersion() throws IOException {
        Path project = temporary.resolve("zolt.toml");
        Files.writeString(project, "[project]\nversion = \"latest\"\n");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ZapVersion.readBaseVersion(project));

        assertTrue(error.getMessage().contains("semantic core"));
    }
}
