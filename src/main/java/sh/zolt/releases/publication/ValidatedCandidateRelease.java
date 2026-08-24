package sh.zolt.releases.publication;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Map;
import sh.zolt.releases.policy.ReleaseChannel;

record ValidatedCandidateRelease(
        ReleaseChannel channel,
        String version,
        String sourceSha,
        String createdAt,
        Path releaseRecord,
        Path sourceEvidence,
        ObjectNode builder,
        Map<String, Archive> archives) {
    record Archive(String target, Path archive, Path checksum, String sha256) {}
}
