package com.mcverse.jobify.application.model;

import java.util.EnumSet;
import java.util.Set;

/** Where an application stands in the hiring pipeline. */
public enum ApplicationStatus {
    APPLIED,
    IN_REVIEW,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN;

    /**
     * Stages an employer may move an application to from this one. APPLIED and WITHDRAWN are never a target:
     * an application starts as APPLIED, and withdrawing is the applicant's own action. REJECTED and WITHDRAWN are
     * final.
     */
    public Set<ApplicationStatus> employerTargets() {
        return switch (this) {
            case APPLIED -> EnumSet.of(IN_REVIEW, REJECTED);
            case IN_REVIEW -> EnumSet.of(INTERVIEW, REJECTED);
            case INTERVIEW -> EnumSet.of(OFFER, REJECTED);
            case OFFER -> EnumSet.of(REJECTED);
            case REJECTED, WITHDRAWN -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }
}
