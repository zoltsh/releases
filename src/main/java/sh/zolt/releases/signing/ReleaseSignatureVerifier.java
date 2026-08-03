package sh.zolt.releases.signing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import sh.zolt.releases.core.ReleaseConstants;

public final class ReleaseSignatureVerifier {
    private final String keyId;
    private final PublicKey publicKey;

    public ReleaseSignatureVerifier() {
        this(ReleaseConstants.ZAP_SIGNING_KEY_ID, ReleaseConstants.ZAP_SIGNING_PUBLIC_KEY);
    }

    ReleaseSignatureVerifier(String keyId, String x509PublicKeyBase64) {
        this.keyId = keyId;
        this.publicKey = decodePublicKey(x509PublicKeyBase64);
    }

    public void verify(Path input, Path signature) {
        Path normalizedInput = input.toAbsolutePath().normalize();
        Path normalizedSignature = signature.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedInput)) {
            throw new IllegalArgumentException(
                    "release file does not exist: " + normalizedInput);
        }
        if (!Files.isRegularFile(normalizedSignature)) {
            throw new IllegalArgumentException(
                    "release signature does not exist: " + normalizedSignature);
        }
        try {
            verify(Files.readAllBytes(normalizedInput), Files.readString(normalizedSignature));
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "could not read release file or signature: " + exception.getMessage(), exception);
        }
    }

    void verify(byte[] payload, String sidecarText) {
        ReleaseSignatureSidecar sidecar = ReleaseSignatureSidecar.parse(sidecarText);
        if (!keyId.equals(sidecar.keyId())) {
            throw new IllegalArgumentException(
                    "release signature uses untrusted key \"" + sidecar.keyId() + "\"");
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(sidecar.signature())) {
                throw new IllegalArgumentException(
                        "release signature is invalid for key \"" + keyId + "\"");
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException(
                    "could not verify release signature for key \"" + keyId + "\"", exception);
        }
    }

    private static PublicKey decodePublicKey(String value) {
        try {
            byte[] encoded = Base64.getDecoder().decode(value);
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid trusted Ed25519 public key", exception);
        }
    }
}
