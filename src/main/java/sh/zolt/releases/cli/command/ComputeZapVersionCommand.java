package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.version.ZapVersion;

public final class ComputeZapVersionCommand implements ReleaseCommand {
    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(
                CliConstants.PROJECT,
                CliConstants.SOURCE_SHA,
                CliConstants.SOURCE_CREATED_AT);
        String version = ZapVersion.compute(
                ZapVersion.readBaseVersion(Path.of(args.require(CliConstants.PROJECT))),
                args.require(CliConstants.SOURCE_SHA),
                ZapVersion.parseSourceTimestamp(args.require(CliConstants.SOURCE_CREATED_AT)));
        System.out.println(version);
    }
}
