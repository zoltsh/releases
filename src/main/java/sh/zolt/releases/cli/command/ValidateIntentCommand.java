package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.intent.IntentRequest;
import sh.zolt.releases.intent.IntentValidator;
import sh.zolt.releases.intent.ReleaseIntent;
import sh.zolt.releases.io.JsonSupport;
import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.policy.ReleasePolicy;

public final class ValidateIntentCommand implements ReleaseCommand {
    private final IntentValidator validator;

    public ValidateIntentCommand() {
        this(new IntentValidator());
    }

    ValidateIntentCommand(IntentValidator validator) {
        this.validator = validator;
    }

    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(
                CliConstants.POLICY,
                CliConstants.CHANNEL,
                CliConstants.SOURCE_REPOSITORY,
                CliConstants.SOURCE_SHA,
                CliConstants.SOURCE_RUN_ID,
                CliConstants.SOURCE_TAG,
                CliConstants.ALLOW_DISABLED);
        ReleaseIntent intent = validator.validate(new IntentRequest(
                ReleasePolicy.load(Path.of(args.optional(
                        CliConstants.POLICY, ReleaseConstants.DEFAULT_POLICY_FILE))),
                ReleaseChannel.parse(args.require(CliConstants.CHANNEL)),
                args.require(CliConstants.SOURCE_REPOSITORY),
                args.require(CliConstants.SOURCE_SHA),
                args.optional(CliConstants.SOURCE_RUN_ID),
                args.optional(CliConstants.SOURCE_TAG),
                args.flag(CliConstants.ALLOW_DISABLED)));
        System.out.print(JsonSupport.write(intent.toMap()));
    }
}
