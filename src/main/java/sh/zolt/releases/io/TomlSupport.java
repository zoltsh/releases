package sh.zolt.releases.io;

import java.io.IOException;
import java.nio.file.Path;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

public final class TomlSupport {
    private TomlSupport() {}

    public static TomlParseResult parse(Path path) {
        try {
            return Toml.parse(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read TOML from " + path + ": " + exception.getMessage(), exception);
        }
    }
}
