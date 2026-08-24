package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.publication.PreviewPublicationPreparer;
import sh.zolt.releases.publication.PreviewPublicationRequest;

public final class PreparePreviewPublicationCommand implements ReleaseCommand {
    private final PreviewPublicationPreparer preparer;

    public PreparePreviewPublicationCommand() {
        this(new PreviewPublicationPreparer());
    }

    PreparePreviewPublicationCommand(PreviewPublicationPreparer preparer) {
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
        String version = preparer.prepare(new PreviewPublicationRequest(
                Path.of(args.require(CliConstants.RELEASE_RECORD)),
                Path.of(args.require(CliConstants.SOURCE_EVIDENCE)),
                Path.of(args.require(CliConstants.CANDIDATES)),
                optionalPath(args, CliConstants.PREVIOUS_CHANNEL),
                optionalPath(args, CliConstants.PREVIOUS_INDEX),
                args.require(CliConstants.EXPECTED_CONTROLLER_SHA),
                args.require(CliConstants.EXPECTED_CONTROLLER_RUN_ID),
                output));
        System.out.println(version);
    }

    private static Path optionalPath(CliArguments args, String name) {
        String value = args.optional(name);
        return value == null ? null : Path.of(value);
    }
}
