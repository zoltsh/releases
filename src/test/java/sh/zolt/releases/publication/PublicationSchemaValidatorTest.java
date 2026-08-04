package sh.zolt.releases.publication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import sh.zolt.releases.io.JsonSupport;

final class PublicationSchemaValidatorTest {
    @Test
    void rejectsLegacySpacesArtifactUrls() {
        ChannelCase channel = new ChannelCase(
                "zap",
                "0.1.0-zap.20260803.aaaaaaaaaaaa",
                "zolt-zap-0.1.0-zap.20260803.aaaaaaaaaaaa");
        ObjectNode document = manifest(channel);
        ObjectNode artifact = (ObjectNode) document.path("artifacts").get(0);
        String legacy = "https://dist.zolt.sh/artifacts/zap/" + channel.version() + "/";
        artifact.put("archiveUrl", legacy + artifact.path("archive").asText());
        artifact.put("checksumUrl", legacy + artifact.path("archive").asText() + ".sha256");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PublicationSchemaValidator().validateChannel(document));

        assertTrue(error.getMessage().contains("archiveUrl"));
    }

    @Test
    void allChannelsUseTheSameGitHubReleaseAssetContract() {
        PublicationSchemaValidator validator = new PublicationSchemaValidator();
        List<ChannelCase> channels = List.of(
                new ChannelCase("zap", "0.1.0-zap.20260803.aaaaaaaaaaaa", "zolt-zap-0.1.0-zap.20260803.aaaaaaaaaaaa"),
                new ChannelCase("preview", "0.2.0-rc.1", "zolt-preview-v0.2.0-rc.1"),
                new ChannelCase("stable", "0.2.0", "zolt-v0.2.0"));

        for (ChannelCase channel : channels) {
            ObjectNode manifest = manifest(channel);
            ObjectNode index = JsonSupport.mapper().createObjectNode();
            index.put("schemaVersion", 1);
            index.put("channel", channel.name());
            index.put("updatedAt", "2026-08-03T20:16:50Z");
            ObjectNode version = index.putArray("versions").addObject();
            version.put("version", channel.version());
            version.put("commit", "a".repeat(40));
            version.put("createdAt", "2026-08-03T20:16:50Z");
            version.set("artifacts", manifest.path("artifacts").deepCopy());

            assertDoesNotThrow(() -> validator.validateChannel(manifest), channel.name());
            assertDoesNotThrow(() -> validator.validateIndex(index), channel.name());
        }
    }

    @Test
    void rejectsVersionsThatDoNotBelongToTheirChannel() {
        PublicationSchemaValidator validator = new PublicationSchemaValidator();
        ChannelCase mismatched =
                new ChannelCase("stable", "0.2.0-rc.1", "zolt-preview-v0.2.0-rc.1");
        ObjectNode manifest = manifest(mismatched);
        ObjectNode index = JsonSupport.mapper().createObjectNode();
        index.put("schemaVersion", 1);
        index.put("channel", mismatched.name());
        index.put("updatedAt", "2026-08-03T20:16:50Z");
        ObjectNode version = index.putArray("versions").addObject();
        version.put("version", mismatched.version());
        version.put("commit", "a".repeat(40));
        version.put("createdAt", "2026-08-03T20:16:50Z");
        version.set("artifacts", manifest.path("artifacts").deepCopy());

        assertThrows(IllegalArgumentException.class, () -> validator.validateChannel(manifest));
        assertThrows(IllegalArgumentException.class, () -> validator.validateIndex(index));
    }

    private static ObjectNode manifest(ChannelCase channel) {
        ObjectNode document = JsonSupport.mapper().createObjectNode();
        document.put("schemaVersion", 1);
        document.put("channel", channel.name());
        document.put("version", channel.version());
        document.put("commit", "a".repeat(40));
        document.put("createdAt", "2026-08-03T20:16:50Z");
        ArrayNode artifacts = document.putArray("artifacts");
        ObjectNode artifact = artifacts.addObject();
        String archive = "zolt-" + channel.version() + "-linux-x64.tar.gz";
        String origin = "https://github.com/zoltsh/releases/releases/download/"
                + channel.tag()
                + "/";
        artifact.put("target", "linux-x64");
        artifact.put("archive", archive);
        artifact.put("archiveUrl", origin + archive);
        artifact.put("checksumUrl", origin + archive + ".sha256");
        artifact.put("sha256", "b".repeat(64));
        artifact.put("format", "tar.gz");
        artifact.put("binaryName", "zolt");
        return document;
    }

    private record ChannelCase(String name, String version, String tag) {}
}
