package sh.zolt.releases.record;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FileDigests {
    private FileDigests() {}

    public static String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException(
                    "could not hash " + path + ": " + exception.getMessage(), exception);
        }
    }

    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read size of " + path + ": " + exception.getMessage(), exception);
        }
    }
}
