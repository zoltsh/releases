package sh.zolt.releases.intent;

import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.policy.ReleasePolicy;

public record IntentRequest(
        ReleasePolicy policy,
        ReleaseChannel channel,
        String sourceRepository,
        String sourceSha,
        String sourceRunId,
        String sourceTag,
        boolean allowDisabled) {}
