package sh.zolt.releases.repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RepositoryValidator {
    private static final List<RepositoryCheck> CHECKS = List.of(
            new RequiredFilesCheck(),
            new RepositoryPolicyCheck(),
            new RepositoryAutomationCheck(),
            new RepositoryContentCheck());

    private RepositoryValidator() {}

    public static List<String> validate(Path root) {
        List<String> errors = new ArrayList<>();
        CHECKS.forEach(check -> check.validate(root, errors));
        return List.copyOf(errors);
    }
}
