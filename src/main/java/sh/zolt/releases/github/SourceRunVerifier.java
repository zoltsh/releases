package sh.zolt.releases.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceRunVerifier {
    private final SourceRunGateway gateway;

    public SourceRunVerifier(SourceRunGateway gateway) {
        this.gateway = gateway;
    }

    public Map<String, Object> verify(String repository, String sourceSha, String runId) {
        return verifyEvidence(
                repository,
                sourceSha,
                runId,
                gateway.fetchRun(repository, runId),
                gateway.fetchJobs(repository, runId));
    }

    static Map<String, Object> verifyEvidence(
            String repository,
            String sourceSha,
            String runId,
            JsonNode run,
            List<JsonNode> jobs) {
        Map<String, Object> expected = Map.of(
                "repository", repository,
                "workflow", SourceCiConstants.WORKFLOW_NAME,
                "workflowPath", SourceCiConstants.WORKFLOW_PATH,
                "event", SourceCiConstants.EVENT,
                "branch", SourceCiConstants.BRANCH,
                "sha", sourceSha,
                "status", SourceCiConstants.COMPLETED_STATUS,
                "conclusion", SourceCiConstants.SUCCESS_CONCLUSION);
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("repository", text(run.path("repository"), "full_name"));
        actual.put("workflow", text(run, "name"));
        actual.put("workflowPath", text(run, "path"));
        actual.put("event", text(run, "event"));
        actual.put("branch", text(run, "head_branch"));
        actual.put("sha", text(run, "head_sha"));
        actual.put("status", text(run, "status"));
        actual.put("conclusion", text(run, "conclusion"));

        List<String> errors = new ArrayList<>();
        expected.forEach((field, value) -> {
            if (!value.equals(actual.get(field))) {
                errors.add(
                        field + ": expected \"" + value + "\", found "
                                + display(actual.get(field)));
            }
        });

        Map<String, List<String>> jobEvidence = new LinkedHashMap<>();
        SourceCiConstants.EXPECTED_JOB_GROUPS.forEach((prefix, count) -> {
            List<JsonNode> matching = jobs.stream()
                    .filter(job -> text(job, "name") != null
                            && text(job, "name").startsWith(prefix))
                    .toList();
            jobEvidence.put(prefix, matching.stream().map(job -> text(job, "name")).toList());
            if (matching.size() != count) {
                errors.add(
                        "job group \"" + prefix + "\": expected " + count
                                + ", found " + matching.size());
                return;
            }
            List<String> failed = matching.stream()
                    .filter(job -> !"success".equals(text(job, "conclusion")))
                    .map(job -> text(job, "name") + "=" + text(job, "conclusion"))
                    .toList();
            if (!failed.isEmpty()) {
                errors.add("job group \"" + prefix + "\": " + String.join(", ", failed));
            }
        });

        String createdAt = text(run, "created_at");
        if (createdAt == null || createdAt.isBlank()) {
            errors.add("source workflow run is missing created_at");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "source CI evidence is not eligible for zap:\n- "
                            + String.join("\n- ", errors));
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("repository", repository);
        evidence.put("workflowRunId", runId);
        evidence.put("workflow", actual.get("workflow"));
        evidence.put("workflowPath", actual.get("workflowPath"));
        evidence.put("event", actual.get("event"));
        evidence.put("branch", actual.get("branch"));
        evidence.put("sourceCommit", actual.get("sha"));
        evidence.put("runAttempt", scalar(run.get("run_attempt")));
        evidence.put("runConclusion", actual.get("conclusion"));
        evidence.put("runUrl", scalar(run.get("html_url")));
        evidence.put("runCreatedAt", createdAt);
        evidence.put("runStartedAt", scalar(run.get("run_started_at")));
        evidence.put("jobs", jobEvidence);
        return evidence;
    }

    private static Object scalar(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String display(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
