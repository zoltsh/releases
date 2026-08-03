package sh.zolt.releases.signing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseFileSignerTest {
    @Test
    void signsAndVerifiesExactBytes(@TempDir Path root)
            throws IOException, NoSuchAlgorithmException {
        KeyPair pair = keyPair();
        ReleaseFileSigner signer = signer(pair);
        ReleaseSignatureVerifier verifier = verifier(pair);
        Path input = Files.writeString(root.resolve("zap.json"), "{\"channel\":\"zap\"}\n");
        Path signature = root.resolve("zap.json.sig");

        signer.sign(input, signature, privatePem(pair));
        verifier.verify(input, signature);

        Files.writeString(input, "{\"channel\":\"stable\"}\n");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(input, signature));
    }

    @Test
    void refusesPrivateKeyThatDoesNotMatchTrustedPublicKey(@TempDir Path root)
            throws IOException, NoSuchAlgorithmException {
        KeyPair trusted = keyPair();
        KeyPair wrong = keyPair();
        ReleaseFileSigner signer = signer(trusted);
        Path input = Files.writeString(root.resolve("zap.json"), "{}\n");
        Path signature = root.resolve("zap.json.sig");

        assertThrows(
                IllegalArgumentException.class,
                () -> signer.sign(input, signature, privatePem(wrong)));

        assertFalse(Files.exists(signature));
    }

    private static KeyPair keyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static ReleaseFileSigner signer(KeyPair pair) {
        return new ReleaseFileSigner("test-release-key", publicKey(pair));
    }

    private static ReleaseSignatureVerifier verifier(KeyPair pair) {
        return new ReleaseSignatureVerifier("test-release-key", publicKey(pair));
    }

    private static String publicKey(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private static String privatePem(KeyPair pair) {
        return "-----BEGIN " + "PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END " + "PRIVATE KEY-----\n";
    }
}
