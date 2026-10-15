package com.mcverse.jobify.common.exception;

import java.util.Map;

public class ResourceNotFoundException extends RuntimeException {

    /** What the front end shows to people, so the wording is plain: "We could not find that job." */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("JobPost", "job"), Map.entry("Seeker", "seeker profile"),
            Map.entry("Employer", "employer profile"), Map.entry("SeekerSkill", "skill"),
            Map.entry("ProfessionalExperience", "experience entry"), Map.entry("Education", "education entry"),
            Map.entry("Certification", "certification"), Map.entry("Deletion request", "deletion request"),
            Map.entry("Member", "team member"), Map.entry("Account", "account"));

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** {@code id} is left out of the message (people cannot use it); the logs still see the exception's resource. */
    public ResourceNotFoundException(String resource, Object id) {
        super("We could not find that " + label(resource) + ".");
    }

    private static String label(String resource) {
        String known = LABELS.get(resource);
        if (known != null) {
            return known;
        }
        // "Notification" -> "notification", "SomeThing" -> "some thing"
        return resource.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
    }
}
