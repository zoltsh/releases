package sh.zolt.releases.repository;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import sh.zolt.releases.io.JsonSupport;
import sh.zolt.releases.io.ProjectFiles;

final class RepositoryContentCheck implements RepositoryCheck {
    @Override
    public void validate(Path root, List<String> errors) {
        validateJsonSchemas(root, errors);
        validateNoCommittedSecrets(root, errors);
        validateJavaOnly(root, errors);
    }

    private static void validateJsonSchemas(Path root, List<String> errors) {
        for (Path path : ProjectFiles.walk(root.resolve("schemas"))) {
            if (!path.toString().endsWith(".json")) {
                continue;
            }
            try {
                JsonNode document = JsonSupport.read(path);
                if (!document.isObject()) {
                    errors.add(
                            "schema root must be an object: "
                                    + ProjectFiles.relative(root, path));
                    continue;
                }
                if (!"https://json-schema.org/draft/2020-12/schema"
                        .equals(document.path("$schema").asText())) {
                    errors.add(
                            "schema must use JSON Schema 2020-12: "
                                    + ProjectFiles.relative(root, path));
                }
                if (!"object".equals(document.path("type").asText())) {
                    errors.add(
                            "schema root must be an object: "
                                    + ProjectFiles.relative(root, path));
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
    }

    private static void validateNoCommittedSecrets(Path root, List<String> errors) {
        for (Path path : ProjectFiles.walk(root, RepositoryRules.IGNORED_DIRECTORIES)) {
            if (RepositoryRules.BINARY_EXTENSIONS.contains(ProjectFiles.extension(path))
                    || isSkippedFile(path)) {
                continue;
            }
            String text = RepositoryFiles.read(path);
            for (String marker : RepositoryRules.SECRET_MARKERS) {
                if (text.contains(marker)) {
                    errors.add(
                            "possible committed secret marker \"" + marker + "\" in "
                                    + ProjectFiles.relative(root, path));
                }
            }
        }
    }

    private static void validateJavaOnly(Path root, List<String> errors) {
        for (Path path : ProjectFiles.walk(root, RepositoryRules.IGNORED_DIRECTORIES)) {
            String extension = ProjectFiles.extension(path);
            if (RepositoryRules.NON_JAVA_EXTENSIONS.contains(extension)) {
                errors.add(
                        "non-Java controller file is not allowed: "
                                + ProjectFiles.relative(root, path));
                continue;
            }
            if (isSkippedFile(path) || !RepositoryRules.TEXT_EXTENSIONS.contains(extension)) {
                continue;
            }
            if (RepositoryRules.OTHER_TOOLCHAIN.matcher(RepositoryFiles.read(path)).find()) {
                errors.add(
                        "non-Java toolchain reference is not allowed: "
                                + ProjectFiles.relative(root, path));
            }
        }
    }

    private static boolean isSkippedFile(Path path) {
        String name = path.getFileName().toString();
        return ".DS_Store".equals(name) || "RepositoryRules.java".equals(name);
    }
}
