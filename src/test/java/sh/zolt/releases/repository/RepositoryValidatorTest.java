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
    void zoltSetupMustUseAnExactPinnedRelease(@TempDir Path root) throws IOException {
        Path setup = copyZoltSetup(root);
        Files.writeString(
                setup,
                Files.readString(setup)
                        .replaceFirst(
                                "(?m)^([ \\t]+version:)[^\\r\\n]+$", "$1 latest"));

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "Zolt setup must use one full-SHA setup-zolt action with channel zap, an exact version, and SHA-256"));
    }

    @Test
    void zoltSetupMustKeepTemurinJava21(@TempDir Path root) throws IOException {
        Path setup = copyZoltSetup(root);
        Files.writeString(setup, Files.readString(setup).replace("java-version: '21'", "java-version: '17'"));

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "Zolt setup must use one full-SHA setup-java action with Temurin Java 21"));
    }

    @Test
    void zoltSetupMustNotAddAHandwrittenInstaller(@TempDir Path root) throws IOException {
        Path setup = copyZoltSetup(root);
        Files.writeString(
                setup,
                Files.readString(setup)
                        + """

                            - name: Install another Zolt
                              shell: bash
                              run: curl https://example.test/zolt | sh
                          """);

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "Zolt setup must use one full-SHA setup-zolt action with channel zap, an exact version, and SHA-256"));
    }

    @Test
    void bootstrapMustEnforceRepositorySecurityControls(@TempDir Path root) throws IOException {
        Path bootstrap = root.resolve("scripts/bootstrap.sh");
        Files.createDirectories(bootstrap.getParent());
        Files.writeString(
                bootstrap,
                Files.readString(Path.of("scripts/bootstrap.sh"))
                        .replace("sha_pinning_required=true", "sha_pinning_required=false"));

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "bootstrap is missing required repository control: sha_pinning_required=true"));
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
    void installerBootstrapMustPinAndVerifyImmutableGitHubInstaller(@TempDir Path root)
            throws IOException {
        Path bootstrap = root.resolve("scripts/install-bootstrap");
        Files.createDirectories(bootstrap.getParent());
        Files.writeString(bootstrap, "curl https://dist.zolt.sh/current-install.sh | sh\n");

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "stable installer bootstrap must verify and execute a pinned immutable GitHub installer"));
    }

    @Test
    void installerBootstrapPublisherMustOnlyWriteReviewedInstaller(@TempDir Path root)
            throws IOException {
        Path publisher = root.resolve("scripts/publish-installer-bootstrap");
        Files.createDirectories(publisher.getParent());
        Files.writeString(publisher, "aws s3 cp artifacts s3://zolt-dist/install.sh\n");

        List<String> errors = new ArrayList<>();
        new RepositoryAutomationCheck().validate(root, errors);

        assertTrue(errors.contains(
                "installer bootstrap publisher must upload and verify only the reviewed install.sh with bounded curl SigV4 requests"));
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

    private static Path copyZoltSetup(Path root) throws IOException {
        Path setup = root.resolve(".github/actions/setup-zolt/action.yml");
        Files.createDirectories(setup.getParent());
        Files.copy(Path.of(".github/actions/setup-zolt/action.yml"), setup);
        return setup;
    }
}
