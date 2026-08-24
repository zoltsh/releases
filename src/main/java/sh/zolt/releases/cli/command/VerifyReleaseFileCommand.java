package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.signing.ReleaseSignatureVerifier;

public final class VerifyReleaseFileCommand implements ReleaseCommand {
    public VerifyReleaseFileCommand() {}

    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(CliConstants.CHANNEL, CliConstants.INPUT, CliConstants.SIGNATURE);
        Path input = Path.of(args.require(CliConstants.INPUT));
        String channelId = args.optional(CliConstants.CHANNEL);
        ReleaseChannel channel = ReleaseChannel.parse(
                channelId == null ? ReleaseChannel.ZAP.id() : channelId);
        ReleaseSignatureVerifier verifier = new ReleaseSignatureVerifier(channel);
        verifier.verify(input, Path.of(args.require(CliConstants.SIGNATURE)));
        System.out.println(input);
    }
}
