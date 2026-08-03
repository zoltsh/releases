package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.publication.ZapPublicationPreparer;
import sh.zolt.releases.publication.ZapPublicationRequest;

public final class PrepareZapPublicationCommand implements ReleaseCommand {
    private final ZapPublicationPreparer preparer;

    public PrepareZapPublicationCommand() {
        this(new ZapPublicationPreparer());
    }

    PrepareZapPublicationCommand(ZapPublicationPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(
                CliConstants.RELEASE_RECORD,
                CliConstants.SOURCE_EVIDENCE,
                CliConstants.CANDIDATES,
                CliConstants.PREVIOUS_CHANNEL,
                CliConstants.PREVIOUS_INDEX,
                CliConstants.EXPECTED_CONTROLLER_SHA,
                CliConstants.EXPECTED_CONTROLLER_RUN_ID,
                CliConstants.OUTPUT);
        Path output = Path.of(args.require(CliConstants.OUTPUT));
        String version = preparer.prepare(new ZapPublicationRequest(
                Path.of(args.require(CliConstants.RELEASE_RECORD)),
                Path.of(args.require(CliConstants.SOURCE_EVIDENCE)),
                Path.of(args.require(CliConstants.CANDIDATES)),
                Path.of(args.require(CliConstants.PREVIOUS_CHANNEL)),
                Path.of(args.require(CliConstants.PREVIOUS_INDEX)),
                args.require(CliConstants.EXPECTED_CONTROLLER_SHA),
                args.require(CliConstants.EXPECTED_CONTROLLER_RUN_ID),
                output));
        System.out.println(version);
    }
}
