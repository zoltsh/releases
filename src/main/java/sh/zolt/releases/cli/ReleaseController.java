package sh.zolt.releases.cli;

import java.util.Map;
import sh.zolt.releases.cli.command.ComputeZapVersionCommand;
import sh.zolt.releases.cli.command.ValidateIntentCommand;
import sh.zolt.releases.cli.command.ValidateRepositoryCommand;
import sh.zolt.releases.cli.command.VerifySourceRunCommand;
import sh.zolt.releases.cli.command.WriteReleaseRecordCommand;

public final class ReleaseController {
    private static final Map<String, ReleaseCommand> COMMANDS = Map.of(
            CliConstants.COMPUTE_ZAP_VERSION, new ComputeZapVersionCommand(),
            CliConstants.VALIDATE_INTENT, new ValidateIntentCommand(),
            CliConstants.VERIFY_SOURCE_RUN, new VerifySourceRunCommand(),
            CliConstants.WRITE_RELEASE_RECORD, new WriteReleaseRecordCommand(),
            CliConstants.VALIDATE_REPOSITORY, new ValidateRepositoryCommand());

    private ReleaseController() {}

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("error: " + exception.getMessage());
            System.exit(2);
        }
    }

    static void run(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("a command is required");
        }
        ReleaseCommand command = COMMANDS.get(args[0]);
        if (command == null) {
            throw new IllegalArgumentException("unknown command \"" + args[0] + "\"");
        }
        command.execute(args);
    }
}
