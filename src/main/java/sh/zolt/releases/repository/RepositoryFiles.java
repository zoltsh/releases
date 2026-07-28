package sh.zolt.releases.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import sh.zolt.releases.io.ProjectFiles;

final class RepositoryFiles {
    private RepositoryFiles() {}

    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read " + path + ": " + exception.getMessage(), exception);
        }
    }

    static List<Path> workflowFiles(Path root) {
        List<Path> files = new ArrayList<>();
        files.addAll(ProjectFiles.walk(root.resolve(".github")));
        files.addAll(ProjectFiles.walk(root.resolve("source-integration")));
        return files.stream()
                .filter(path -> Set.of(".yml", ".yaml").contains(ProjectFiles.extension(path)))
                .toList();
    }
}
