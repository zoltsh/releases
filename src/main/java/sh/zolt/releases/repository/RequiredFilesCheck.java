package sh.zolt.releases.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class RequiredFilesCheck implements RepositoryCheck {
    @Override
    public void validate(Path root, List<String> errors) {
        for (String file : RepositoryRules.REQUIRED_FILES) {
            if (!Files.isRegularFile(root.resolve(file))) {
                errors.add("missing required file: " + file);
            }
        }
    }
}
