package sh.zolt.releases.intent;

import java.util.LinkedHashMap;
import java.util.Map;
import sh.zolt.releases.policy.ReleaseChannel;

public record ReleaseIntent(
        ReleaseChannel channel,
        String sourceRepository,
        String sourceCommit,
        String sourceWorkflowRunId,
        String sourceTag,
        String policyStatus) {
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", channel.id());
        result.put("sourceRepository", sourceRepository);
        result.put("sourceCommit", sourceCommit);
        result.put("sourceWorkflowRunId", sourceWorkflowRunId);
        result.put("sourceTag", sourceTag);
        result.put("policyStatus", policyStatus);
        return result;
    }
}
