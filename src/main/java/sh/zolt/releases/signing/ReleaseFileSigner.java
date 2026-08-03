package sh.zolt.releases.signing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import sh.zolt.releases.core.ReleaseConstants;

public final class ReleaseFileSigner {
    private static final String PEM_HEADER = "-----BEGIN " + "PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END " + "PRIVATE KEY-----";

    private final String keyId;
    private final ReleaseSignatureVerifier verifier;

    public ReleaseFileSigner() {
        this(
                ReleaseConstants.ZAP_SIGNING_KEY_ID,
                ReleaseConstants.ZAP_SIGNING_PUBLIC_KEY);
    }

    ReleaseFileSigner(String keyId, String x509PublicKeyBase64) {
        this.keyId = keyId;
        this.verifier = new ReleaseSignatureVerifier(keyId, x509PublicKeyBase64);
    }

    public void sign(Path input, Path signature, String privateKeyPem) {
        Path normalizedInput = input.toAbsolutePath().normalize();
        Path normalizedSignature = signature.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedInput)) {
            throw new IllegalArgumentException(
                    "release file does not exist: " + normalizedInput);
        }
        if (normalizedInput.equals(normalizedSignature)) {
            throw new IllegalArgumentException("release file and signature path must differ");
        }
        try {
            byte[] payload = Files.readAllBytes(normalizedInput);
            byte[] signatureBytes = sign(payload, decodePrivateKey(privateKeyPem));
            String sidecar = new ReleaseSignatureSidecar(keyId, signatureBytes).format();
            verifier.verify(payload, sidecar);
            Path parent = normalizedSignature.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(normalizedSignature, sidecar);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not write release signature: " + exception.getMessage(), exception);
        }
    }

    private static byte[] sign(byte[] payload, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload);
            return signer.sign();
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("could not sign release file with Ed25519", exception);
        }
    }

    private static PrivateKey decodePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("release signing private key is empty");
        }
        String stripped = pem.strip();
        if (!stripped.startsWith(PEM_HEADER) || !stripped.endsWith(PEM_FOOTER)) {
            throw new IllegalArgumentException(
                    "release signing private key must be an unencrypted PKCS#8 PEM");
        }
        String encoded = stripped.substring(
                        PEM_HEADER.length(), stripped.length() - PEM_FOOTER.length())
                .replaceAll("\\s", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "release signing private key is not valid Ed25519 PKCS#8", exception);
        }
    }
}
