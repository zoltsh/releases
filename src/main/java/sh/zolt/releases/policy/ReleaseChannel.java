package sh.zolt.releases.policy;

public enum ReleaseChannel {
    STABLE("stable"),
    PREVIEW("preview"),
    ZAP("zap");

    private final String id;

    ReleaseChannel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ReleaseChannel parse(String value) {
        for (ReleaseChannel channel : values()) {
            if (channel.id.equals(value)) {
                return channel;
            }
        }
        throw new IllegalArgumentException("unsupported channel \"" + value + "\"");
    }
}
