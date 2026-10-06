package com.mcverse.jobify.job.model;

public enum WorkMode {
    REMOTE("Remote"),
    HYBRID("Hybrid"),
    ONSITE("On-site");

    private final String displayName;

    WorkMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
