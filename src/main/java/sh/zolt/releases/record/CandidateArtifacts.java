package sh.zolt.releases.record;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import sh.zolt.releases.core.ReleaseConstants;
import sh.zolt.releases.io.ProjectFiles;

public final class CandidateArtifacts {
    private final Path root;
    private final List<Path> files;
    private final Map<String, Path> archives;
    private final String version;

    private CandidateArtifacts(
            Path root, List<Path> files, Map<String, Path> archives, String version) {
        this.root = root;
        this.files = files;
        this.archives = Map.copyOf(archives);
        this.version = version;
    }

    public static CandidateArtifacts load(Path candidates, String expectedVersion) {
        Path root = candidates.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("candidate directory does not exist: " + root);
        }
        rejectSymbolicLinks(root);

        List<Path> files = ProjectFiles.walk(root);
        Map<String, Path> archives = new LinkedHashMap<>();
        Set<String> versions = new LinkedHashSet<>();
        for (Path path : files) {
            Matcher match = ReleaseRecordRules.ARCHIVE.matcher(path.getFileName().toString());
            if (!match.matches()) {
                continue;
            }
            String target = match.group("target");
            if (archives.put(target, path) != null) {
                throw new IllegalArgumentException(
                        "duplicate archive for target " + target + ": " + path);
            }
            versions.add(match.group("version"));
        }

        List<String> missing = ReleaseConstants.RELEASE_TARGETS.stream()
                .filter(target -> !archives.containsKey(target))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "missing candidate archives for: " + String.join(", ", missing));
        }
        archives.forEach(CandidateArtifacts::verifyChecksum);

        if (versions.size() != 1) {
            throw new IllegalArgumentException(
                    "candidate archives must have one version, found: " + versions);
        }
        String version = versions.iterator().next();
        if (expectedVersion != null && !version.equals(expectedVersion)) {
            throw new IllegalArgumentException(
                    "candidate version \"" + version + "\" does not match expected version \""
                            + expectedVersion + "\"");
        }
        return new CandidateArtifacts(root, files, archives, version);
    }

    private static void rejectSymbolicLinks(Path root) {
        try (var paths = Files.walk(root)) {
            Path symbolicLink = paths.filter(Files::isSymbolicLink).findFirst().orElse(null);
            if (symbolicLink != null) {
                throw new IllegalArgumentException(
                        "candidate directory must not contain symbolic links: " + symbolicLink);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not inspect candidate directory: " + exception.getMessage(), exception);
        }
    }

    public Path root() {
        return root;
    }

    public List<Path> files() {
        return files;
    }

    public Map<String, Path> archives() {
        return archives;
    }

    public String version() {
        return version;
    }

    public List<Map<String, Object>> entries() {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        for (Path path : files) {
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("name", ProjectFiles.relative(root, path));
            artifact.put("sha256", FileDigests.sha256(path));
            artifact.put("size", FileDigests.size(path));
            artifacts.add(artifact);
        }
        return List.copyOf(artifacts);
    }

    private static void verifyChecksum(String target, Path archive) {
        Path checksum = archive.resolveSibling(archive.getFileName() + ".sha256");
        if (!Files.isRegularFile(checksum)) {
            throw new IllegalArgumentException(
                    "missing SHA-256 sidecar for " + target + ": " + checksum);
        }
        try {
            String[] fields = Files.readString(checksum).trim().split("\\s+");
            String expected = fields.length == 0 ? "" : fields[0];
            if (!ReleaseRecordRules.SHA256.matcher(expected).matches()) {
                throw new IllegalArgumentException(
                        "invalid SHA-256 sidecar for " + target + ": " + checksum);
            }
            String actual = FileDigests.sha256(archive);
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "SHA-256 sidecar mismatch for " + target + ": expected " + expected
                                + ", found " + actual);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read " + checksum + ": " + exception.getMessage(), exception);
        }
    }
}
