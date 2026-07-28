package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import java.util.Map;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.github.GitHubActionsClient;
import sh.zolt.releases.github.SourceRunVerifier;
import sh.zolt.releases.io.JsonSupport;

public final class VerifySourceRunCommand implements ReleaseCommand {
    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(
                CliConstants.REPOSITORY,
                CliConstants.SOURCE_SHA,
                CliConstants.RUN_ID,
                CliConstants.OUTPUT,
                CliConstants.TOKEN_ENV);
        String tokenName = args.optional(
                CliConstants.TOKEN_ENV, ReleaseConstants.DEFAULT_SOURCE_TOKEN_ENV);
        SourceRunVerifier verifier =
                new SourceRunVerifier(new GitHubActionsClient(System.getenv(tokenName)));
        Map<String, Object> evidence = verifier.verify(
                args.require(CliConstants.REPOSITORY),
                args.require(CliConstants.SOURCE_SHA),
                args.require(CliConstants.RUN_ID));
        Path output = Path.of(args.require(CliConstants.OUTPUT));
        JsonSupport.write(output, evidence);
        System.out.println(output);
    }
}
