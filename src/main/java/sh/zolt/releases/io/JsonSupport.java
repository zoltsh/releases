package sh.zolt.releases.io;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonSupport {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private JsonSupport() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode read(Path path) {
        try {
            return MAPPER.readTree(path.toFile());
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read JSON from " + path + ": " + exception.getMessage(), exception);
        }
    }

    public static JsonNode read(String source) {
        try {
            return MAPPER.readTree(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("could not parse JSON: " + exception.getMessage(), exception);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n";
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("could not write JSON: " + exception.getMessage(), exception);
        }
    }

    public static void write(Path path, Object value) {
        try {
            Path parent = path.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, write(value));
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not write JSON to " + path + ": " + exception.getMessage(), exception);
        }
    }
}
