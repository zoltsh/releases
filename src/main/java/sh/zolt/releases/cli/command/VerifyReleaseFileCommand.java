package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.signing.ReleaseSignatureVerifier;

public final class VerifyReleaseFileCommand implements ReleaseCommand {
    private final ReleaseSignatureVerifier verifier;

    public VerifyReleaseFileCommand() {
        this(new ReleaseSignatureVerifier());
    }

    VerifyReleaseFileCommand(ReleaseSignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(CliConstants.INPUT, CliConstants.SIGNATURE);
        Path input = Path.of(args.require(CliConstants.INPUT));
        verifier.verify(input, Path.of(args.require(CliConstants.SIGNATURE)));
        System.out.println(input);
    }
}
