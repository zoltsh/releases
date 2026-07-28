package sh.zolt.releases.record;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import sh.zolt.releases.io.JsonSupport;

final class ReleaseRecordValidator {
    private final Schema schema;

    ReleaseRecordValidator(Path schemaFile) {
        Path normalized = schemaFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException(
                    "release record schema does not exist: " + normalized);
        }
        try {
            SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
            schema = registry.getSchema(Files.readString(normalized), InputFormat.JSON);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read release record schema: " + normalized, exception);
        }
    }

    void validate(Map<String, Object> record) {
        List<com.networknt.schema.Error> errors = schema.validate(
                JsonSupport.write(record),
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
                "release record does not match schemas/release-record-v1.schema.json:\n- "
                        + details);
    }
}
