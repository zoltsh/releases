package sh.zolt.releases.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import sh.zolt.releases.io.ProjectFiles;

final class RepositoryAutomationCheck implements RepositoryCheck {
    @Override
    public void validate(Path root, List<String> errors) {
        validateActionPins(root, errors);
        validateZoltSetup(root, errors);
        validateWorkflowPermissions(root, errors);
        validateBootstrapSecurity(root, errors);
        validateTrustedControllerResolution(root, errors);
        validateCandidateToolchainSync(root, errors);
        validateCandidateWorkflow(root, errors);
        validatePublishWorkflow(root, errors);
        validateRecoveryWorkflow(root, errors);
        validatePublicationBackends(root, errors);
    }

    private static void validateZoltSetup(Path root, List<String> errors) {
        Path path = root.resolve(RepositoryRules.ZOLT_SETUP_ACTION_FILE);
        if (!Files.isRegularFile(path)) {
            return;
        }
        String setup = RepositoryFiles.read(path);
        if (RepositoryRules.SETUP_ZOLT_REFERENCE.matcher(setup).results().count() != 1
                || RepositoryRules.PINNED_ZOLT_SETUP_STEP.matcher(setup).results().count() != 1
                || RepositoryRules.HANDWRITTEN_ZOLT_SETUP.matcher(setup).find()) {
            errors.add(
                    "Zolt setup must use one full-SHA setup-zolt action with channel zap, an exact version, and SHA-256");
        }
        if (RepositoryRules.PINNED_JAVA_SETUP_STEP.matcher(setup).results().count() != 1) {
            errors.add(
                    "Zolt setup must use one full-SHA setup-java action with Temurin Java 21");
        }
    }

    private static void validateActionPins(Path root, List<String> errors) {
        for (Path path : RepositoryFiles.workflowFiles(root)) {
            Matcher matcher = RepositoryRules.USES.matcher(RepositoryFiles.read(path));
            while (matcher.find()) {
                String reference = matcher.group(1);
                if (!reference.startsWith("./")
                        && !RepositoryRules.PINNED_ACTION.matcher(reference).matches()) {
                    errors.add(
                            "external action is not pinned to a full SHA in "
                                    + ProjectFiles.relative(root, path) + ": " + reference);
                }
            }
        }
    }

    private static void validateWorkflowPermissions(Path root, List<String> errors) {
        for (Path path : ProjectFiles.walk(root.resolve(".github/workflows"))) {
            if (path.toString().endsWith(".yml")
                    && !RepositoryRules.TOP_LEVEL_PERMISSIONS
                            .matcher(RepositoryFiles.read(path))
                            .find()) {
                errors.add(
                        "workflow must declare top-level permissions: "
                                + ProjectFiles.relative(root, path));
            }
        }
    }

    private static void validateBootstrapSecurity(Path root, List<String> errors) {
        Path path = root.resolve("scripts/bootstrap.sh");
        if (!Files.isRegularFile(path)) {
            return;
        }
        String bootstrap = RepositoryFiles.read(path);
        for (String fragment : RepositoryRules.BOOTSTRAP_SECURITY_FRAGMENTS) {
            if (!bootstrap.contains(fragment)) {
                errors.add("bootstrap is missing required repository control: " + fragment);
            }
        }
    }

    private static void validateTrustedControllerResolution(Path root, List<String> errors) {
        for (String file : RepositoryRules.TRUSTED_CONTROLLER_WORKFLOWS) {
            Path path = root.resolve(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            String workflow = RepositoryFiles.read(path);
            int setup = workflow.indexOf(RepositoryRules.TRUSTED_ZOLT_SETUP);
            while (setup >= 0) {
                int nextSetup = workflow.indexOf(
                        RepositoryRules.TRUSTED_ZOLT_SETUP,
                        setup + RepositoryRules.TRUSTED_ZOLT_SETUP.length());
                int run = workflow.indexOf(
                        RepositoryRules.TRUSTED_ZOLT_RUN,
                        setup + RepositoryRules.TRUSTED_ZOLT_SETUP.length());
                if (run >= 0 && (nextSetup < 0 || run < nextSetup)) {
                    int resolve = workflow.indexOf(
                            RepositoryRules.LOCKED_ZOLT_RESOLVE,
                            setup + RepositoryRules.TRUSTED_ZOLT_SETUP.length());
                    if (resolve < 0 || resolve > run || (nextSetup >= 0 && resolve > nextSetup)) {
                        errors.add(
                                "trusted controller must resolve locked dependencies before zolt run in "
                                        + file);
                    }
                }
                setup = nextSetup;
            }
        }
    }

    private static void validateCandidateToolchainSync(Path root, List<String> errors) {
        Path path = root.resolve(RepositoryRules.ZAP_CANDIDATE_WORKFLOW);
        if (!Files.isRegularFile(path)) {
            return;
        }
        String workflow = RepositoryFiles.read(path);
        int build = workflow.indexOf(RepositoryRules.CANDIDATE_BUILD_JOB);
        int checkout = workflow.indexOf(RepositoryRules.SOURCE_CHECKOUT, Math.max(0, build));
        int sync = workflow.indexOf(RepositoryRules.SOURCE_TOOLCHAIN_SYNC, Math.max(0, checkout));
        int distribution =
                workflow.indexOf(RepositoryRules.SOURCE_ZAP_DISTRIBUTION, Math.max(0, checkout));
        if (build < 0
                || checkout < build
                || sync < checkout
                || distribution < checkout
                || sync > distribution) {
            errors.add(
                    "zap candidate build must sync the source-managed Java toolchain before distribution");
        }
    }

    private static void validateCandidateWorkflow(Path root, List<String> errors) {
        if (!RepositoryRules.CANDIDATE_WORKFLOW_FILES.stream()
                .allMatch(file -> Files.isRegularFile(root.resolve(file)))) {
            return;
        }
        String workflow = RepositoryFiles.read(root.resolve(RepositoryRules.ZAP_CANDIDATE_WORKFLOW));
        for (String fragment : RepositoryRules.CANDIDATE_WORKFLOW_FRAGMENTS) {
            if (!workflow.contains(fragment)) {
                errors.add("zap candidate workflow is missing required contract: " + fragment);
            }
        }
        if (workflow.contains("environment: channel-")) {
            errors.add("zap candidate workflow must not use a publishing environment");
        }

        String dispatcher =
                RepositoryFiles.read(root.resolve("source-integration/dispatch-zap.yml"));
        if (!dispatcher.contains("permission-actions: write")) {
            errors.add(
                    "source dispatcher must request only the explicit Actions-write App permission");
        }
        for (String fragment : RepositoryRules.FORBIDDEN_DISPATCHER_FRAGMENTS) {
            if (dispatcher.contains(fragment)) {
                errors.add("source dispatcher contains forbidden release authority: " + fragment);
            }
        }

        String codeowners =
                RepositoryFiles.read(root.resolve("source-integration/CODEOWNERS"));
        for (String sensitive : RepositoryRules.SENSITIVE_SOURCE_PATHS) {
            if (!codeowners.contains(sensitive)) {
                errors.add("source CODEOWNERS template must protect " + sensitive);
            }
        }
        if (!RepositoryFiles.read(root.resolve("scripts/bootstrap.sh"))
                .contains("repos/${FULL_REPO}/immutable-releases")) {
            errors.add("bootstrap must enable immutable releases before publication");
        }
    }

    private static void validatePublishWorkflow(Path root, List<String> errors) {
        Path path = root.resolve(RepositoryRules.ZAP_PUBLISH_WORKFLOW);
        if (!Files.isRegularFile(path)) {
            return;
        }
        String workflow = RepositoryFiles.read(path);
        for (String fragment : RepositoryRules.PUBLISH_WORKFLOW_FRAGMENTS) {
            if (!workflow.contains(fragment)) {
                errors.add("zap publish workflow is missing required contract: " + fragment);
            }
        }
        for (String fragment : RepositoryRules.FORBIDDEN_PUBLISH_WORKFLOW_FRAGMENTS) {
            if (workflow.contains(fragment)) {
                errors.add("zap publish workflow contains forbidden candidate authority: " + fragment);
            }
        }
        int immutable = workflow.indexOf("scripts/publish-github-release");
        int bootstrap = workflow.indexOf("scripts/publish-installer-bootstrap");
        int metadata = workflow.indexOf("scripts/publish-channel-metadata");
        int publicVerification = workflow.indexOf("Verify public zap publication");
        if (immutable < 0
                || bootstrap < immutable
                || metadata < bootstrap
                || publicVerification < metadata) {
            errors.add(
                    "zap publish workflow must publish immutable GitHub assets, publish the stable installer bootstrap, move metadata, then verify the public result");
        }
    }

    private static void validateRecoveryWorkflow(Path root, List<String> errors) {
        Path path = root.resolve(RepositoryRules.ZAP_RECOVER_WORKFLOW);
        if (!Files.isRegularFile(path)) {
            return;
        }
        String workflow = RepositoryFiles.read(path);
        for (String fragment : RepositoryRules.RECOVER_WORKFLOW_FRAGMENTS) {
            if (!workflow.contains(fragment)) {
                errors.add("zap recovery workflow is missing required contract: " + fragment);
            }
        }
        int immutable = workflow.indexOf(".immutable == true");
        int bootstrap = workflow.indexOf("scripts/publish-installer-bootstrap");
        int metadata = workflow.indexOf("scripts/publish-channel-metadata");
        int publicVerification = workflow.indexOf("Verify public zap recovery");
        if (immutable < 0
                || bootstrap < immutable
                || metadata < bootstrap
                || publicVerification < metadata) {
            errors.add(
                    "zap recovery workflow must verify an immutable release, publish the stable installer bootstrap, move metadata, then verify the public result");
        }
    }

    private static void validatePublicationBackends(Path root, List<String> errors) {
        Path githubPublisher = root.resolve("scripts/publish-github-release");
        Path installerBootstrap = root.resolve("scripts/install-bootstrap");
        Path installerPublisher = root.resolve("scripts/publish-installer-bootstrap");
        Path metadataPublisher = root.resolve("scripts/publish-channel-metadata");
        if (Files.isRegularFile(githubPublisher)) {
            String publisher = RepositoryFiles.read(githubPublisher);
            if (!publisher.contains("release create")
                    || !publisher.contains("release upload")
                    || !publisher.contains(".immutable")) {
                errors.add("release assets must be published as verified immutable GitHub Releases");
            }
            if (publisher.contains("s3api")) {
                errors.add("GitHub Release publisher must not upload release assets to object storage");
            }
        }
        if (Files.isRegularFile(installerBootstrap)) {
            String bootstrap = RepositoryFiles.read(installerBootstrap);
            if (!RepositoryRules.PINNED_BOOTSTRAP_INSTALLER_URL.matcher(bootstrap).find()
                    || !RepositoryRules.PINNED_BOOTSTRAP_INSTALLER_SHA256.matcher(bootstrap).find()
                    || !bootstrap.contains("--proto-redir '=https'")
                    || !bootstrap.contains("sha256_file \"$installer\"")
                    || !bootstrap.contains("sh \"$installer\" \"$@\"")) {
                errors.add(
                        "stable installer bootstrap must verify and execute a pinned immutable GitHub installer");
            }
        }
        if (Files.isRegularFile(installerPublisher)) {
            String publisher = RepositoryFiles.read(installerPublisher);
            if (!publisher.contains("key=\"install.sh\"")
                    || !publisher.contains("--request PUT")
                    || !publisher.contains("--upload-file \"$bootstrap\"")
                    || !publisher.contains("--aws-sigv4 \"aws:amz:nyc3:s3\"")
                    || !publisher.contains("--connect-timeout")
                    || !publisher.contains("--max-time")
                    || !publisher.contains("cmp -s \"$bootstrap\" \"$downloaded\"")
                    || publisher.contains("artifacts/")
                    || publisher.contains("s3api")) {
                errors.add(
                        "installer bootstrap publisher must upload and verify only the reviewed install.sh with bounded curl SigV4 requests");
            }
        }
        if (Files.isRegularFile(metadataPublisher)) {
            String publisher = RepositoryFiles.read(metadataPublisher);
            if (publisher.contains("artifacts/")) {
                errors.add("channel metadata publisher must not upload release artifacts");
            }
            if (!publisher.contains("--aws-sigv4 \"aws:amz:nyc3:s3\"")
                    || !publisher.contains("--connect-timeout")
                    || !publisher.contains("--max-time")
                    || publisher.contains("s3api")) {
                errors.add(
                        "channel metadata publisher must use bounded curl SigV4 requests to Spaces");
            }
        }
    }
}
