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
        validateWorkflowPermissions(root, errors);
        validateTrustedControllerResolution(root, errors);
        validateCandidateToolchainSync(root, errors);
        validateCandidateWorkflow(root, errors);
        validatePublishWorkflow(root, errors);
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

        String setup =
                RepositoryFiles.read(root.resolve(".github/actions/setup-zolt/action.yml"));
        if (!RepositoryRules.PINNED_ZOLT_ARCHIVE.matcher(setup).find()
                || !RepositoryRules.PINNED_ZOLT_CHECKSUM.matcher(setup).find()) {
            errors.add("Zolt setup must pin an exact native archive and SHA-256");
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
        int immutable = workflow.indexOf("scripts/publish-zap");
        int publicVerification = workflow.indexOf("Verify public zap publication");
        if (immutable < 0 || publicVerification < immutable) {
            errors.add("zap publish workflow must verify the public result after publication");
        }
    }
}
