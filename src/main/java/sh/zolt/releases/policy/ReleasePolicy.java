package sh.zolt.releases.policy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import sh.zolt.releases.io.TomlSupport;

public record ReleasePolicy(
        List<String> allowedSources, Map<ReleaseChannel, ChannelPolicy> channels) {
    public ReleasePolicy {
        allowedSources = List.copyOf(allowedSources);
        channels = Map.copyOf(channels);
    }

    public static ReleasePolicy load(Path path) {
        TomlParseResult document = TomlSupport.parse(path);
        if (document.hasErrors()) {
            throw new IllegalArgumentException(
                    "invalid TOML in " + path + ": " + document.errors().getFirst());
        }

        TomlTable repositories = requireTable(document, "repositories");
        TomlArray allowed = repositories.getArray("allowed_sources");
        if (allowed == null) {
            throw new IllegalArgumentException(
                    "policy repositories.allowed_sources must be a string array");
        }
        List<String> sources = new ArrayList<>();
        for (int index = 0; index < allowed.size(); index++) {
            String source = allowed.getString(index);
            if (source == null) {
                throw new IllegalArgumentException(
                        "policy repositories.allowed_sources must be a string array");
            }
            sources.add(source);
        }

        TomlTable channelTable = requireTable(document, "channel");
        Map<ReleaseChannel, ChannelPolicy> channels = new EnumMap<>(ReleaseChannel.class);
        for (ReleaseChannel channel : ReleaseChannel.values()) {
            TomlTable table = requireTable(channelTable, channel.id());
            String status = table.getString("status");
            if (status == null) {
                throw new IllegalArgumentException(
                        "policy channel." + channel.id() + ".status must be a string");
            }
            channels.put(channel, new ChannelPolicy(status));
        }
        return new ReleasePolicy(sources, channels);
    }

    private static TomlTable requireTable(TomlTable table, String name) {
        TomlTable value = table.getTable(name);
        if (value == null) {
            throw new IllegalArgumentException("missing TOML table " + name);
        }
        return value;
    }
}
