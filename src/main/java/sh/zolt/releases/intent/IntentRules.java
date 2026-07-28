package sh.zolt.releases.intent;

import java.util.regex.Pattern;

final class IntentRules {
    static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");
    static final Pattern REPOSITORY =
            Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    static final Pattern STABLE_TAG =
            Pattern.compile("^v(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)$");
    static final Pattern PREVIEW_TAG = Pattern.compile(
            "^v(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)-(?:alpha|beta|rc)\\.(?:0|[1-9][0-9]*)$");

    private IntentRules() {}
}
