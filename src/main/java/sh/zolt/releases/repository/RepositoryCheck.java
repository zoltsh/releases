package sh.zolt.releases.repository;

import java.nio.file.Path;
import java.util.List;

interface RepositoryCheck {
    void validate(Path root, List<String> errors);
}
