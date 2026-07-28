package sh.zolt.releases.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RepositoryValidatorTest {
    @Test
    void repositorySatisfiesItsReleasePolicy() {
        assertEquals(
                List.of(),
                RepositoryValidator.validate(Path.of(".").toAbsolutePath().normalize()));
    }
}
