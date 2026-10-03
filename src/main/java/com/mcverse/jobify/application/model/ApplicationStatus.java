package com.mcverse.jobify.application.model;

/** Where an application stands in the hiring pipeline. */
public enum ApplicationStatus {
    APPLIED,
    IN_REVIEW,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN
}
