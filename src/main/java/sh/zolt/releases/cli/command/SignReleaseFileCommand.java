package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.signing.ReleaseFileSigner;

public final class SignReleaseFileCommand implements ReleaseCommand {
    private static final String SIGNING_KEY_ENV = "ZOLT_RELEASE_ED25519_PRIVATE_KEY";
    public SignReleaseFileCommand() {}

    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(
                CliConstants.CHANNEL,
                CliConstants.INPUT,
                CliConstants.SIGNATURE,
                CliConstants.PRIVATE_KEY_ENV);
        String environmentName = args.require(CliConstants.PRIVATE_KEY_ENV);
        if (!SIGNING_KEY_ENV.equals(environmentName)) {
            throw new IllegalArgumentException(
                    CliConstants.PRIVATE_KEY_ENV + " must be " + SIGNING_KEY_ENV);
        }
        String privateKey = System.getenv(environmentName);
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalArgumentException(environmentName + " is not set");
        }
        Path signature = Path.of(args.require(CliConstants.SIGNATURE));
        String channelId = args.optional(CliConstants.CHANNEL);
        ReleaseChannel channel = ReleaseChannel.parse(
                channelId == null ? ReleaseChannel.ZAP.id() : channelId);
        ReleaseFileSigner signer = new ReleaseFileSigner(channel);
        signer.sign(Path.of(args.require(CliConstants.INPUT)), signature, privateKey);
        System.out.println(signature);
    }
}
