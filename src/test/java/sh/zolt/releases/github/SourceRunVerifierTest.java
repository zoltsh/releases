package sh.zolt.releases.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sh.zolt.releases.io.JsonSupport;

final class SourceRunVerifierTest {
    @Test
    void acceptsExactSuccessfulSourceEvidence() {
        Map<String, Object> evidence =
                verifier(runPayload(), successfulJobs())
                        .verify("zoltsh/zolt", "a".repeat(40), "123");

        assertEquals("123", evidence.get("workflowRunId"));
        assertEquals("a".repeat(40), evidence.get("sourceCommit"));
    }

    @Test
    void rejectsMismatchedSourceCommit() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> verifier(runPayload(), successfulJobs())
                        .verify("zoltsh/zolt", "b".repeat(40), "123"));

        assertTrue(error.getMessage().contains("source CI evidence"));
    }

    @Test
    void rejectsFailedRequiredJob() {
        List<JsonNode> jobs = successfulJobs();
        ((ObjectNode) jobs.getFirst()).put("conclusion", "failure");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> verifier(runPayload(), jobs)
                        .verify("zoltsh/zolt", "a".repeat(40), "123"));

        assertTrue(error.getMessage().contains("failure"));
    }

    @Test
    void rejectsFailedSourceRun() {
        ObjectNode run = (ObjectNode) runPayload();
        run.put("conclusion", "failure");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> verifier(run, successfulJobs())
                        .verify("zoltsh/zolt", "a".repeat(40), "123"));

        assertTrue(error.getMessage().contains("conclusion"));
    }

    private static SourceRunVerifier verifier(JsonNode run, List<JsonNode> jobs) {
        return new SourceRunVerifier(new SourceRunGateway() {
            @Override
            public JsonNode fetchRun(String repository, String runId) {
                return run;
            }

            @Override
            public List<JsonNode> fetchJobs(String repository, String runId) {
                return jobs;
            }
        });
    }

    private static List<JsonNode> successfulJobs() {
        List<JsonNode> jobs = new ArrayList<>();
        SourceCiConstants.EXPECTED_JOB_GROUPS.forEach((prefix, count) -> {
            for (int index = 0; index < count; index++) {
                ObjectNode job = JsonSupport.mapper().createObjectNode();
                job.put("name", (prefix + index).trim());
                job.put("conclusion", "success");
                jobs.add(job);
            }
        });
        return jobs;
    }

    private static JsonNode runPayload() {
        ObjectNode run = JsonSupport.mapper().createObjectNode();
        run.put("name", SourceCiConstants.WORKFLOW_NAME);
        run.put("path", SourceCiConstants.WORKFLOW_PATH);
        run.put("event", SourceCiConstants.EVENT);
        run.put("head_branch", SourceCiConstants.BRANCH);
        run.put("head_sha", "a".repeat(40));
        run.put("status", SourceCiConstants.COMPLETED_STATUS);
        run.put("conclusion", SourceCiConstants.SUCCESS_CONCLUSION);
        run.put("run_attempt", 1);
        run.put("created_at", "2026-07-28T11:59:00Z");
        run.put("run_started_at", "2026-07-28T12:00:00Z");
        run.putObject("repository").put("full_name", "zoltsh/zolt");
        return run;
    }
}
