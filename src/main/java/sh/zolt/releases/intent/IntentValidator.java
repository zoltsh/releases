package sh.zolt.releases.intent;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;
import sh.zolt.releases.policy.ChannelPolicy;
import sh.zolt.releases.policy.ReleaseChannel;

public final class IntentValidator {
    public ReleaseIntent validate(IntentRequest request) {
        ChannelPolicy channelPolicy = request.policy().channels().get(request.channel());
        if ("disabled".equals(channelPolicy.status()) && !request.allowDisabled()) {
            throw new IllegalArgumentException(
                    "channel \"" + request.channel().id() + "\" is disabled by policy");
        }

        String repository = validateRepository(request);
        String commit = validateSha(request.sourceSha());
        String runId = null;
        String tag = null;
        if (request.channel() == ReleaseChannel.ZAP) {
            runId = validateRunId(request.sourceRunId());
            if (request.sourceTag() != null) {
                throw new IllegalArgumentException(
                        "zap must be identified by commit, not a release tag");
            }
        } else {
            tag = validateTag(request.channel(), request.sourceTag());
            if (request.sourceRunId() != null) {
                throw new IllegalArgumentException(
                        request.channel().id()
                                + " intent must not rely on a source workflow run ID");
            }
        }
        return new ReleaseIntent(
                request.channel(), repository, commit, runId, tag, channelPolicy.status());
    }

    private static String validateRepository(IntentRequest request) {
        String value = request.sourceRepository();
        if (!IntentRules.REPOSITORY.matcher(value).matches()) {
            throw new IllegalArgumentException("source repository must use owner/name syntax");
        }
        if (!request.policy().allowedSources().contains(value)) {
            throw new IllegalArgumentException(
                    "source repository \"" + value + "\" is not allowlisted");
        }
        return value;
    }

    private static String validateSha(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!IntentRules.SHA.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "source SHA must be exactly 40 lowercase hexadecimal characters");
        }
        return normalized;
    }

    private static String validateRunId(String value) {
        if (value == null || !value.matches("^[0-9]+$")) {
            throw new IllegalArgumentException(
                    "zap source workflow run ID must be a positive integer");
        }
        try {
            if (new BigInteger(value).signum() <= 0) {
                throw new IllegalArgumentException(
                        "zap source workflow run ID must be a positive integer");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "zap source workflow run ID must be a positive integer", exception);
        }
        return value;
    }

    private static String validateTag(ReleaseChannel channel, String value) {
        if (value == null) {
            throw new IllegalArgumentException(channel.id() + " requires a source tag");
        }
        Pattern pattern =
                channel == ReleaseChannel.STABLE ? IntentRules.STABLE_TAG : IntentRules.PREVIEW_TAG;
        if (!pattern.matcher(value).matches()) {
            String example = channel == ReleaseChannel.STABLE ? "v1.2.3" : "v1.2.3-rc.1";
            throw new IllegalArgumentException(
                    "invalid " + channel.id() + " tag \"" + value
                            + "\"; expected a form like " + example);
        }
        return value;
    }
}
