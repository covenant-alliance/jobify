package com.mcverse.matching.score;

import com.mcverse.matching.v1.EmploymentType;
import com.mcverse.matching.v1.ExperienceLevel;
import com.mcverse.matching.v1.JobProfile;
import com.mcverse.matching.v1.Match;
import com.mcverse.matching.v1.Preferences;
import com.mcverse.matching.v1.Proficiency;
import com.mcverse.matching.v1.RequiredSkill;
import com.mcverse.matching.v1.ScorePart;
import com.mcverse.matching.v1.SeekerProfile;
import com.mcverse.matching.v1.SeekerSkill;
import com.mcverse.matching.v1.WorkMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores one seeker against one job. A pure function: the same input gives the same output, with no
 * I/O, clock or randomness. The rules are in docs/MATCHING_SERVICE.md, section 5.
 *
 * <p>A part that cannot be judged is returned with {@code counted = false} and its weight is
 * dropped, so missing data never lowers a score.
 */
public final class Scorer {

    static final String SKILLS = "skills";
    static final String LOCATION = "location";
    static final String WORK_MODE = "work_mode";
    static final String EMPLOYMENT_TYPE = "employment_type";
    static final String RATE = "rate";
    static final String EXPERIENCE = "experience";

    /** A rate this far outside the seeker's range (as a share of the bound) is worth nothing. */
    static final double RATE_FALLOFF = 0.30;
    private static final double YEARS_BONUS_PER_YEAR = 0.1;
    private static final double YEARS_BONUS_AFTER = 2.0;

    private final ScoringWeights weights;

    public Scorer(ScoringWeights weights) {
        this.weights = weights;
    }

    public Match score(SeekerProfile seeker, JobProfile job) {
        Preferences prefs = seeker.hasPreferences() ? seeker.getPreferences() : Preferences.getDefaultInstance();

        Match.Builder match = Match.newBuilder();
        Part skills = skillsPart(seeker, job, match);
        List<Part> parts = List.of(
                skills,
                locationPart(prefs, job),
                workModePart(prefs, job),
                employmentTypePart(prefs, job),
                ratePart(prefs, job),
                experiencePart(prefs, job));

        double counted = 0;
        double earned = 0;
        for (Part part : parts) {
            match.addParts(ScorePart.newBuilder()
                    .setKey(part.key)
                    .setWeight(part.weight)
                    .setValue(part.counted ? part.value : 0)
                    .setCounted(part.counted));
            if (part.counted) {
                counted += part.weight;
                earned += part.weight * part.value;
            }
        }
        match.setScore(counted <= 0 ? 0 : (int) Math.round(earned / counted * 100));
        return match.build();
    }

    // ---- parts ----

    private Part skillsPart(SeekerProfile seeker, JobProfile job, Match.Builder match) {
        List<RequiredSkill> required = job.getRequiredSkillsList();
        if (required.isEmpty()) {
            return Part.notCounted(SKILLS, weights.skills());
        }
        Map<String, SeekerSkill> owned = new HashMap<>();
        for (SeekerSkill skill : seeker.getSkillsList()) {
            owned.merge(skill.getSkillId(), skill, (a, b) -> credit(a) >= credit(b) ? a : b);
        }
        double total = 0;
        for (RequiredSkill need : required) {
            SeekerSkill have = owned.get(need.getSkillId());
            if (have == null) {
                match.addMissingSkillIds(need.getSkillId());
            } else {
                match.addMatchedSkillIds(need.getSkillId());
                total += credit(have);
            }
        }
        return Part.counted(SKILLS, weights.skills(), total / required.size());
    }

    private Part locationPart(Preferences prefs, JobProfile job) {
        boolean seekerAcceptsRemote = prefs.getWorkModesList().contains(WorkMode.WORK_MODE_REMOTE);
        if (job.getWorkMode() == WorkMode.WORK_MODE_REMOTE && seekerAcceptsRemote) {
            return Part.counted(LOCATION, weights.location(), 1);
        }
        if (prefs.getCountryCodesCount() == 0 || job.getCountryCode().isBlank()) {
            return Part.notCounted(LOCATION, weights.location());
        }
        boolean inside = prefs.getCountryCodesList().stream()
                .anyMatch(code -> code.equalsIgnoreCase(job.getCountryCode()));
        return Part.counted(LOCATION, weights.location(), inside ? 1 : 0);
    }

    private Part workModePart(Preferences prefs, JobProfile job) {
        if (prefs.getWorkModesCount() == 0 || !known(job.getWorkMode())) {
            return Part.notCounted(WORK_MODE, weights.workMode());
        }
        return Part.counted(WORK_MODE, weights.workMode(), prefs.getWorkModesList().contains(job.getWorkMode()) ? 1 : 0);
    }

    private Part employmentTypePart(Preferences prefs, JobProfile job) {
        if (prefs.getEmploymentTypesCount() == 0 || !known(job.getEmploymentType())) {
            return Part.notCounted(EMPLOYMENT_TYPE, weights.employmentType());
        }
        return Part.counted(EMPLOYMENT_TYPE, weights.employmentType(),
                prefs.getEmploymentTypesList().contains(job.getEmploymentType()) ? 1 : 0);
    }

    private Part ratePart(Preferences prefs, JobProfile job) {
        boolean hasMin = prefs.hasMinAnnualRate();
        boolean hasMax = prefs.hasMaxAnnualRate();
        if (!job.hasAnnualRate() || (!hasMin && !hasMax)) {
            return Part.notCounted(RATE, weights.rate());
        }
        double rate = job.getAnnualRate();
        double outside = 0; // how far outside the range, as a share of the nearer bound
        if (hasMin && rate < prefs.getMinAnnualRate()) {
            outside = prefs.getMinAnnualRate() > 0 ? (prefs.getMinAnnualRate() - rate) / prefs.getMinAnnualRate() : 0;
        } else if (hasMax && rate > prefs.getMaxAnnualRate()) {
            outside = prefs.getMaxAnnualRate() > 0 ? (rate - prefs.getMaxAnnualRate()) / prefs.getMaxAnnualRate() : 1;
        }
        return Part.counted(RATE, weights.rate(), Math.max(0, 1 - outside / RATE_FALLOFF));
    }

    private Part experiencePart(Preferences prefs, JobProfile job) {
        if (!prefs.hasExperienceLevel() || !job.hasExperienceLevel()
                || !known(prefs.getExperienceLevel()) || !known(job.getExperienceLevel())) {
            return Part.notCounted(EXPERIENCE, weights.experience());
        }
        int apart = Math.abs(prefs.getExperienceLevel().getNumber() - job.getExperienceLevel().getNumber());
        double value = apart == 0 ? 1 : apart == 1 ? 0.5 : 0;
        return Part.counted(EXPERIENCE, weights.experience(), value);
    }

    // ---- helpers ----

    /** Credit of one owned skill: proficiency, plus 0.1 per year beyond 2, never above 1. */
    static double credit(SeekerSkill skill) {
        double base = switch (skill.getProficiency()) {
            case PROFICIENCY_INTERMEDIATE -> 0.8;
            case PROFICIENCY_ADVANCED, PROFICIENCY_EXPERT -> 1.0;
            // beginner, unspecified and a value from a newer contract all count as the lowest step
            default -> 0.5;
        };
        double bonus = Math.max(0, skill.getYears() - YEARS_BONUS_AFTER) * YEARS_BONUS_PER_YEAR;
        return Math.min(1.0, base + bonus);
    }

    private static boolean known(WorkMode value) {
        return value != WorkMode.WORK_MODE_UNSPECIFIED && value != WorkMode.UNRECOGNIZED;
    }

    private static boolean known(EmploymentType value) {
        return value != EmploymentType.EMPLOYMENT_TYPE_UNSPECIFIED && value != EmploymentType.UNRECOGNIZED;
    }

    private static boolean known(ExperienceLevel value) {
        return value != ExperienceLevel.EXPERIENCE_LEVEL_UNSPECIFIED && value != ExperienceLevel.UNRECOGNIZED;
    }

    private record Part(String key, double weight, double value, boolean counted) {
        static Part counted(String key, double weight, double value) {
            return new Part(key, weight, value, true);
        }

        static Part notCounted(String key, double weight) {
            return new Part(key, weight, 0, false);
        }
    }
}
