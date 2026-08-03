package sh.zolt.releases.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class PublicationSchemaValidator {
    private final Schema channel;
    private final Schema index;

    PublicationSchemaValidator() {
        this(
                load(Path.of("schemas/channel-manifest-v1.schema.json")),
                load(Path.of("schemas/release-index-v1.schema.json")));
    }

    PublicationSchemaValidator(Schema channel, Schema index) {
        this.channel = channel;
        this.index = index;
    }

    void validateChannel(JsonNode document) {
        validate(channel, document, "channel manifest");
    }

    void validateIndex(JsonNode document) {
        validate(index, document, "release index");
    }

    private static Schema load(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("publication schema does not exist: " + normalized);
        }
        try {
            SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
            return registry.getSchema(Files.readString(normalized), InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read publication schema: " + normalized, exception);
        }
    }

    private static void validate(Schema schema, JsonNode document, String description) {
        List<com.networknt.schema.Error> errors = schema.validate(
                document.toString(),
                InputFormat.JSON,
                context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
        if (errors.isEmpty()) {
            return;
        }
        String details = errors.stream()
                .sorted(Comparator.comparing(Object::toString))
                .map(Object::toString)
                .reduce((left, right) -> left + "\n- " + right)
                .orElse("unknown schema error");
        throw new IllegalArgumentException(
                description + " does not match its v1 schema:\n- " + details);
    }
}
