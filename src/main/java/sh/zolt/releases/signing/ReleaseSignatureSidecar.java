package sh.zolt.releases.signing;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

record ReleaseSignatureSidecar(String keyId, byte[] signature) {
    static final String FORMAT_VERSION = "zolt-ed25519-v1";

    static ReleaseSignatureSidecar parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("release signature sidecar is empty");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String raw : text.lines().toList()) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 1) {
                throw new IllegalArgumentException(
                        "release signature sidecar contains malformed line \"" + line + "\"");
            }
            String name = line.substring(0, separator).strip();
            String value = line.substring(separator + 1).strip();
            if (fields.put(name, value) != null) {
                throw new IllegalArgumentException(
                        "release signature sidecar repeats field \"" + name + "\"");
            }
        }
        if (!fields.keySet().equals(Set.of("version", "keyId", "signature"))) {
            throw new IllegalArgumentException(
                    "release signature sidecar must contain exactly version, keyId, and signature");
        }
        if (!FORMAT_VERSION.equals(fields.get("version"))) {
            throw new IllegalArgumentException(
                    "unsupported release signature version \"" + fields.get("version") + "\"");
        }
        String keyId = fields.get("keyId");
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("release signature keyId is empty");
        }
        try {
            byte[] signature = Base64.getDecoder().decode(fields.get("signature"));
            if (signature.length != 64) {
                throw new IllegalArgumentException(
                        "release Ed25519 signature must be exactly 64 bytes");
            }
            return new ReleaseSignatureSidecar(keyId, signature);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("release Ed25519")) {
                throw exception;
            }
            throw new IllegalArgumentException(
                    "release signature is not valid Base64", exception);
        }
    }

    String format() {
        return "version: " + FORMAT_VERSION + "\n"
                + "keyId: " + keyId + "\n"
                + "signature: " + Base64.getEncoder().encodeToString(signature) + "\n";
    }
}
