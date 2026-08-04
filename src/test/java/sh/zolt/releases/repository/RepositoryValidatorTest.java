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

    @Test
    void candidateBuildMustSyncSourceToolchainBeforeDistribution(@TempDir Path root)
            throws IOException {
        Path workflow = root.resolve(".github/workflows/zap-candidate.yml");
        Files.createDirectories(workflow.getParent());
        Files.writeString(
                workflow,
                """
                permissions: {}
                jobs:
                  build:
                    steps:
                      - name: Fetch exact source commit
                        run: git -C source checkout --detach "$SOURCE_SHA"
                      - name: Build distribution
                        run: scripts/zap-distribution --target "$ZOLT_RELEASE_TARGET"
                """);

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "zap candidate build must sync the source-managed Java toolchain before distribution"));
    }

    @Test
    void zapPublisherMustUseFixedUnapprovedZapEnvironment(@TempDir Path root)
            throws IOException {
        Path workflow = root.resolve(".github/workflows/zap-publish.yml");
        Files.createDirectories(workflow.getParent());
        Files.writeString(
                workflow,
                Files.readString(Path.of(".github/workflows/zap-publish.yml"))
                        .replace("environment: channel-zap", "environment: channel-stable"));

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "zap publish workflow is missing required contract: environment: channel-zap"));
    }

    @Test
    void metadataPublisherMustNeverUploadReleaseArtifacts(@TempDir Path root) throws IOException {
        Path publisher = root.resolve("scripts/publish-channel-metadata");
        Files.createDirectories(publisher.getParent());
        Files.writeString(publisher, "aws s3 cp artifacts/zap s3://zolt-dist/artifacts/zap\n");

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains("channel metadata publisher must not upload release artifacts"));
    }

    @Test
    void metadataPublisherMustUseBoundedCurlSigV4(@TempDir Path root) throws IOException {
        Path publisher = root.resolve("scripts/publish-channel-metadata");
        Files.createDirectories(publisher.getParent());
        Files.writeString(publisher, "aws s3api get-object --bucket zolt-dist\n");

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "channel metadata publisher must use bounded curl SigV4 requests to Spaces"));
    }

    @Test
    void legacyInstallerRetirementMustOnlyDeleteTheInstaller(@TempDir Path root)
            throws IOException {
        Path retirement = root.resolve("scripts/retire-legacy-installer");
        Files.createDirectories(retirement.getParent());
        Files.writeString(
                retirement,
                "curl --upload-file installer s3://zolt-dist/install.sh\n");

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "legacy installer retirement must only delete install.sh and prove authenticated HTTP 404 with bounded curl SigV4 requests"));
    }

    @Test
    void zapRecoveryMustRequireAnImmutableRelease(@TempDir Path root) throws IOException {
        Path workflow = root.resolve(".github/workflows/zap-recover.yml");
        Files.createDirectories(workflow.getParent());
        Files.writeString(
                workflow,
                Files.readString(Path.of(".github/workflows/zap-recover.yml"))
                        .replace(".immutable == true", ".immutable == false"));

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "zap recovery workflow is missing required contract: "
                        + ".draft == false and .prerelease == true and .immutable == true"));
    }
}
