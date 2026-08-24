package sh.zolt.releases.publication;

import java.nio.file.Path;

public record PreviewPublicationRequest(
        Path releaseRecord,
        Path sourceEvidence,
        Path candidates,
        Path previousChannel,
        Path previousIndex,
        String expectedControllerSha,
        String expectedControllerRunId,
        Path output) {}
