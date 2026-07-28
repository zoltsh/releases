package sh.zolt.releases.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class ProjectFiles {
    private ProjectFiles() {}

    public static List<Path> walk(Path root) {
        return walk(root, Set.of());
    }

    public static List<Path> walk(Path root, Set<String> ignoredDirectories) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !containsIgnoredDirectory(root, path, ignoredDirectories))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not walk " + root + ": " + exception.getMessage(), exception);
        }
    }

    public static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoredDirectory(Path root, Path path, Set<String> ignored) {
        Path relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        for (Path part : relative) {
            if (ignored.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
