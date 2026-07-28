package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import java.util.Map;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.io.JsonSupport;
import sh.zolt.releases.policy.ReleaseChannel;
import sh.zolt.releases.record.ReleaseRecordRequest;
import sh.zolt.releases.record.ReleaseRecordWriter;

public final class WriteReleaseRecordCommand implements ReleaseCommand {
    private final ReleaseRecordWriter writer;

    public WriteReleaseRecordCommand() {
        this(new ReleaseRecordWriter());
    }

    WriteReleaseRecordCommand(ReleaseRecordWriter writer) {
        this.writer = writer;
    }

    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(
                CliConstants.CHANNEL,
                CliConstants.SOURCE_REPOSITORY,
                CliConstants.SOURCE_SHA,
                CliConstants.SOURCE_RUN_ID,
                CliConstants.SOURCE_TAG,
                CliConstants.EXPECTED_VERSION,
                CliConstants.SOURCE_EVIDENCE,
                CliConstants.CONTROLLER_SHA,
                CliConstants.CONTROLLER_RUN_ID,
                CliConstants.CANDIDATES,
                CliConstants.OUTPUT);
        Path output = Path.of(args.require(CliConstants.OUTPUT));
        Map<String, Object> record = writer.build(new ReleaseRecordRequest(
                ReleaseChannel.parse(args.require(CliConstants.CHANNEL)).id(),
                args.require(CliConstants.SOURCE_REPOSITORY),
                args.require(CliConstants.SOURCE_SHA),
                args.optional(CliConstants.SOURCE_RUN_ID),
                args.optional(CliConstants.SOURCE_TAG),
                args.optional(CliConstants.EXPECTED_VERSION),
                Path.of(args.require(CliConstants.SOURCE_EVIDENCE)),
                args.require(CliConstants.CONTROLLER_SHA),
                args.require(CliConstants.CONTROLLER_RUN_ID),
                Path.of(args.require(CliConstants.CANDIDATES))));
        JsonSupport.write(output, record);
        System.out.println(output);
    }
}
