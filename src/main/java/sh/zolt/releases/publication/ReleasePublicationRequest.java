package sh.zolt.releases.publication;

import java.nio.file.Path;
import sh.zolt.releases.policy.ReleaseChannel;

record ReleasePublicationRequest(
        ReleaseChannel channel,
        Path releaseRecord,
        Path sourceEvidence,
        Path candidates,
        Path previousChannel,
        Path previousIndex,
        String expectedControllerSha,
        String expectedControllerRunId,
        Path output) {}
