package sh.zolt.releases.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepositoryValidatorTest {
    @Test
    void repositorySatisfiesItsReleasePolicy() {
        assertEquals(
                List.of(),
                RepositoryValidator.validate(Path.of(".").toAbsolutePath().normalize()));
    }

    @Test
    void trustedControllerMustResolveBeforeRunning(@TempDir Path root) throws IOException {
        Path workflow = root.resolve(".github/workflows/zap-candidate.yml");
        Files.createDirectories(workflow.getParent());
        Files.writeString(
                workflow,
                """
                permissions: {}
                jobs:
                  validate:
                    steps:
                      - uses: ./.github/actions/setup-zolt
                      - run: zolt run --quiet -- validate-intent
                """);

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "trusted controller must resolve locked dependencies before zolt run in "
                        + ".github/workflows/zap-candidate.yml"));
    }
}
