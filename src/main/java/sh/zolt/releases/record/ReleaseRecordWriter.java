package sh.zolt.releases.record;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import sh.zolt.releases.core.ReleaseConstants;

public final class ReleaseRecordWriter {
    private final ReleaseRecordValidator validator;

    public ReleaseRecordWriter() {
        this(new ReleaseRecordValidator(Path.of(ReleaseRecordRules.SCHEMA_FILE)));
    }

    ReleaseRecordWriter(ReleaseRecordValidator validator) {
        this.validator = validator;
    }

    public Map<String, Object> build(ReleaseRecordRequest request) {
        Path evidence = request.sourceEvidence().toAbsolutePath().normalize();
        if (!Files.isRegularFile(evidence)) {
            throw new IllegalArgumentException(
                    "source CI evidence does not exist: " + evidence);
        }
        CandidateArtifacts candidates =
                CandidateArtifacts.load(request.candidates(), request.expectedVersion());

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", request.sourceRepository());
        source.put("commit", request.sourceSha());
        source.put("workflowRunId", request.sourceRunId());
        source.put("tag", request.sourceTag());

        Map<String, Object> sourceEvidence = new LinkedHashMap<>();
        sourceEvidence.put("name", evidence.getFileName().toString());
        sourceEvidence.put("sha256", FileDigests.sha256(evidence));
        sourceEvidence.put("size", FileDigests.size(evidence));

        Map<String, Object> controller = new LinkedHashMap<>();
        controller.put("repository", ReleaseConstants.CONTROLLER_REPOSITORY);
        controller.put("commit", request.controllerSha());
        controller.put("workflowRunId", request.controllerRunId());

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", ReleaseConstants.RELEASE_RECORD_SCHEMA_VERSION);
        record.put("channel", request.channel());
        record.put("state", ReleaseConstants.CANDIDATE_STATE);
        record.put("version", candidates.version());
        record.put("createdAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        record.put("source", source);
        record.put("sourceEvidence", sourceEvidence);
        record.put("controller", controller);
        record.put("artifacts", candidates.entries());
        validator.validate(record);
        return record;
    }
}
