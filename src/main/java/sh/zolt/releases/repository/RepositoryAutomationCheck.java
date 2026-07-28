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
        validateCandidateWorkflow(root, errors);
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

    private static void validateCandidateWorkflow(Path root, List<String> errors) {
        if (!RepositoryRules.CANDIDATE_WORKFLOW_FILES.stream()
                .allMatch(file -> Files.isRegularFile(root.resolve(file)))) {
            return;
        }
        String workflow =
                RepositoryFiles.read(root.resolve(".github/workflows/zap-candidate.yml"));
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
}
