package com.mcverse.jobify.common.model;

public enum EmploymentType {
    FULL_TIME("Full-time"),
    PART_TIME("Part-time"),
    CONTRACT("Contract"),
    TEMPORARY("Temporary"),
    INTERNSHIP("Internship"),
    FREELANCE("Freelance"),
    B2B("B2B");

    private final String displayName;

    EmploymentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
