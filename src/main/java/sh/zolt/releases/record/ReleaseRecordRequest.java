package sh.zolt.releases.record;

import java.nio.file.Path;

public record ReleaseRecordRequest(
        String channel,
        String sourceRepository,
        String sourceSha,
        String sourceRunId,
        String sourceTag,
        String expectedVersion,
        Path sourceEvidence,
        String controllerSha,
        String controllerRunId,
        Path candidates) {}
