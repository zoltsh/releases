package sh.zolt.releases.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.policy.ReleasePolicy;

final class IntentValidatorTest {
    private static final ReleasePolicy POLICY =
            ReleasePolicy.load(Path.of("policy/channels.toml"));
    private static final IntentValidator VALIDATOR = new IntentValidator();

    @Test
    void acceptsZapFromCanonicalSource() {
        ReleaseIntent intent = validate(
                ReleaseChannel.ZAP, "zoltsh/zolt", "a".repeat(40), "123", null, false);

        assertEquals(ReleaseChannel.ZAP, intent.channel());
        assertEquals("123", intent.sourceWorkflowRunId());
    }

    @Test
    void rejectsUntrustedSource() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validate(
                        ReleaseChannel.ZAP,
                        "attacker/zolt",
                        "c".repeat(40),
                        "1",
                        null,
                        false));

        assertTrue(error.getMessage().contains("not allowlisted"));
    }

    @Test
    void rejectsShortSha() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validate(
                        ReleaseChannel.ZAP,
                        "zoltsh/zolt",
                        "abc123",
                        "1",
                        null,
                        false));

        assertTrue(error.getMessage().contains("exactly 40"));
    }

    @Test
    void requiresPrereleaseTagForPreview() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validate(
                        ReleaseChannel.PREVIEW,
                        "zoltsh/zolt",
                        "d".repeat(40),
                        null,
                        "v1.2.3",
                        true));

        assertTrue(error.getMessage().contains("invalid preview tag"));
    }

    @Test
    void allowsStableChecksWhileStableIsDisabled() {
        ReleaseIntent intent = validate(
                ReleaseChannel.STABLE,
                "zoltsh/zolt",
                "e".repeat(40),
                null,
                "v1.2.3",
                true);

        assertEquals("disabled", intent.policyStatus());
    }

    private static ReleaseIntent validate(
            ReleaseChannel channel,
            String repository,
            String sha,
            String runId,
            String tag,
            boolean allowDisabled) {
        return VALIDATOR.validate(new IntentRequest(
                POLICY, channel, repository, sha, runId, tag, allowDisabled));
    }
}
