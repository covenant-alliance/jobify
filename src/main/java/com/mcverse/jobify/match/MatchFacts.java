package com.mcverse.jobify.match;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.job.model.ExperienceLevel;
import com.mcverse.jobify.job.model.WorkMode;
import com.mcverse.jobify.user.model.ProficiencyLevel;

import java.util.List;

/**
 * What the matching engine is told, in Jobify's own plain types: no JPA entities and no gRPC types. Built inside a
 * short transaction, so the (slow, remote) call afterwards never holds a database connection. Pay figures are already
 * yearly amounts in one currency.
 */
public final class MatchFacts {

    private MatchFacts() {}

    public record SkillRef(String id, String name) {}

    public record SeekerSkillFact(String skillId, ProficiencyLevel level, double years) {}

    public record PreferenceFacts(List<String> countryCodes, List<WorkMode> workModes,
                                  List<EmploymentType> employmentTypes, Double minAnnualRate, Double maxAnnualRate,
                                  ExperienceLevel experienceLevel) {}

    /** {@code preferences} is null when the seeker saved none. */
    public record SeekerFacts(String seekerId, List<SeekerSkillFact> skills, PreferenceFacts preferences) {}

    public record JobFacts(int jobId, String title, List<SkillRef> requiredSkills, String countryCode,
                           WorkMode workMode, EmploymentType employmentType, Double annualRate,
                           ExperienceLevel experienceLevel, long postedAtEpochSeconds) {}

    public record PartFact(String key, double weight, double value, boolean counted) {}

    public record MatchResult(int score, List<String> matchedSkillIds, List<String> missingSkillIds,
                              List<PartFact> parts) {}

    /** One job of a ranking, best first. */
    public record RankedJob(int jobId, MatchResult result) {}
}
