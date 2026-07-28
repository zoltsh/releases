package sh.zolt.releases.cli;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class CliArguments {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Set<String> flags = new LinkedHashSet<>();

    public static CliArguments parse(String[] args, int start) {
        CliArguments parsed = new CliArguments();
        for (int index = start; index < args.length; index++) {
            String name = args[index];
            if (!name.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument " + quote(name));
            }
            if (parsed.values.containsKey(name) || parsed.flags.contains(name)) {
                throw new IllegalArgumentException("duplicate argument " + name);
            }
            if (index + 1 < args.length && !args[index + 1].startsWith("--")) {
                parsed.values.put(name, args[++index]);
            } else {
                parsed.flags.add(name);
            }
        }
        return parsed;
    }

    public void allow(String... names) {
        Set<String> allowed = Set.of(names);
        for (String name : values.keySet()) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("unknown argument " + name);
            }
        }
        for (String name : flags) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("unknown argument " + name);
            }
        }
    }

    public String require(String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public String optional(String name) {
        if (flags.contains(name)) {
            throw new IllegalArgumentException(name + " requires a value");
        }
        return values.get(name);
    }

    public String optional(String name, String fallback) {
        String value = optional(name);
        return value == null ? fallback : value;
    }

    public boolean flag(String name) {
        if (values.containsKey(name)) {
            throw new IllegalArgumentException(name + " does not accept a value");
        }
        return flags.contains(name);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
