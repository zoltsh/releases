package sh.zolt.releases.cli.command;

import java.nio.file.Path;
import java.util.List;
import sh.zolt.releases.cli.CliArguments;
import sh.zolt.releases.cli.CliConstants;
import sh.zolt.releases.cli.ReleaseCommand;
import sh.zolt.releases.repository.RepositoryValidator;

public final class ValidateRepositoryCommand implements ReleaseCommand {
    @Override
    public void execute(String[] raw) {
        CliArguments args = CliArguments.parse(raw, 1);
        args.allow(CliConstants.ROOT);
        Path root = Path.of(args.optional(CliConstants.ROOT, "."))
                .toAbsolutePath()
                .normalize();
        List<String> errors = RepositoryValidator.validate(root);
        if (!errors.isEmpty()) {
            errors.forEach(error -> System.err.println("error: " + error));
            System.exit(1);
        }
        System.out.println("release repository policy is valid");
    }
}
