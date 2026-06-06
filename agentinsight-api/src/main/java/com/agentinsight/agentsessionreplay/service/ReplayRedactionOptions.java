package com.agentinsight.agentsessionreplay.service;

public record ReplayRedactionOptions(
    boolean showPrompts,
    boolean showOutputs,
    boolean showCommandOutput,
    boolean showFilePaths
) {
    public static ReplayRedactionOptions defaults() {
        return new ReplayRedactionOptions(false, false, false, false);
    }
}
