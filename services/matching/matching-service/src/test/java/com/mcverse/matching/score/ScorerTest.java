package com.mcverse.matching.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScorerTest {

    private final Scorer scorer = new Scorer(ScoringWeights.defaults());

    // ---- builders ----

    private static SeekerSkill skill(String id, Proficiency level, double years) {
        return SeekerSkill.newBuilder().setSkillId(id).setProficiency(level).setYears(years).build();
    }

    private static SeekerProfile seeker(Preferences prefs, SeekerSkill... skills) {
        SeekerProfile.Builder b = SeekerProfile.newBuilder().setSeekerId("s");
        for (SeekerSkill s : skills) {
            b.addSkills(s);
        }
        if (prefs != null) {
            b.setPreferences(prefs);
        }
        return b.build();
    }

    private static JobProfile.Builder job(String... requiredSkillIds) {
        JobProfile.Builder b = JobProfile.newBuilder().setJobId(1).setTitle("t");
        for (String id : requiredSkillIds) {
            b.addRequiredSkills(RequiredSkill.newBuilder().setSkillId(id).setName(id));
        }
        return b;
    }

    private static ScorePart part(Match m, String key) {
        return m.getPartsList().stream().filter(p -> p.getKey().equals(key)).findFirst().orElseThrow();
    }

    // ---- skills ----

    @ParameterizedTest(name = "{0} for {1} years -> credit {2}")
    @CsvSource({
            "PROFICIENCY_BEGINNER, 0, 0.5",
            "PROFICIENCY_INTERMEDIATE, 0, 0.8",
            "PROFICIENCY_ADVANCED, 0, 1.0",
            "PROFICIENCY_EXPERT, 0, 1.0",
            "PROFICIENCY_UNSPECIFIED, 0, 0.5",
            "PROFICIENCY_BEGINNER, 2, 0.5",
            "PROFICIENCY_BEGINNER, 3, 0.6",
            "PROFICIENCY_BEGINNER, 5, 0.8",
            "PROFICIENCY_INTERMEDIATE, 4, 1.0",
            "PROFICIENCY_ADVANCED, 10, 1.0",
    })
    void skillCreditFollowsProficiencyAndYears(Proficiency level, double years, double expected) {
        assertEquals(expected, Scorer.credit(skill("x", level, years)), 1e-9);
    }

    @Test
    void skillsPartIsTheAverageCreditAndListsMatchedAndMissing() {
        Match m = scorer.score(seeker(null, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)), job("a", "b").build());

        assertEquals(0.5, part(m, "skills").getValue(), 1e-9);
        assertEquals(java.util.List.of("a"), m.getMatchedSkillIdsList());
        assertEquals(java.util.List.of("b"), m.getMissingSkillIdsList());
        assertEquals(50, m.getScore()); // only skills counted
    }

    @Test
    void duplicateSeekerSkillUsesTheBetterEntry() {
        Match m = scorer.score(seeker(null,
                skill("a", Proficiency.PROFICIENCY_BEGINNER, 0), skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)),
                job("a").build());
        assertEquals(100, m.getScore());
    }

    @Test
    void unknownSkillIdsNeverMatchAndNeverFail() {
        Match m = scorer.score(seeker(null, skill("zzz", Proficiency.PROFICIENCY_ADVANCED, 9)), job("a").build());
        assertEquals(0, m.getScore());
        assertEquals(java.util.List.of("a"), m.getMissingSkillIdsList());
    }

    @Test
    void jobWithoutRequiredSkillsHasNoSkillsPart() {
        Match m = scorer.score(seeker(null, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)), job().build());
        assertFalse(part(m, "skills").getCounted());
        assertEquals(0, m.getScore()); // nothing countable at all
    }

    // ---- missing data ----

    @Test
    void seekerWithNoPreferencesIsScoredOnSkillsAlone() {
        Match m = scorer.score(seeker(null, skill("a", Proficiency.PROFICIENCY_INTERMEDIATE, 0)),
                job("a").setCountryCode("DE").setWorkMode(WorkMode.WORK_MODE_REMOTE).setAnnualRate(50000).build());

        assertEquals(80, m.getScore());
        for (String key : new String[] {"location", "work_mode", "employment_type", "rate", "experience"}) {
            assertFalse(part(m, key).getCounted(), key);
        }
    }

    @Test
    void jobWithEmptyFieldsIsNotPenalised() {
        Preferences prefs = Preferences.newBuilder()
                .addCountryCodes("DE").addWorkModes(WorkMode.WORK_MODE_REMOTE)
                .addEmploymentTypes(EmploymentType.EMPLOYMENT_TYPE_FULL_TIME)
                .setMinAnnualRate(1).setExperienceLevel(ExperienceLevel.EXPERIENCE_LEVEL_MID).build();
        Match m = scorer.score(seeker(prefs, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)), job("a").build());

        assertEquals(100, m.getScore());
        assertFalse(part(m, "location").getCounted());
        assertFalse(part(m, "rate").getCounted());
        assertFalse(part(m, "experience").getCounted());
    }

    @Test
    void anUnknownEnumValueFromANewerJobifyIsTreatedAsNoData() {
        JobProfile newer = job("a").setWorkModeValue(99).setEmploymentTypeValue(99).setExperienceLevelValue(99).build();
        Preferences prefs = Preferences.newBuilder().addWorkModes(WorkMode.WORK_MODE_ONSITE)
                .addEmploymentTypes(EmploymentType.EMPLOYMENT_TYPE_CONTRACT)
                .setExperienceLevel(ExperienceLevel.EXPERIENCE_LEVEL_LEAD).build();
        Match m = scorer.score(seeker(prefs, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)), newer);

        assertFalse(part(m, "work_mode").getCounted());
        assertFalse(part(m, "employment_type").getCounted());
        assertFalse(part(m, "experience").getCounted());
        assertEquals(100, m.getScore());
    }

    // ---- location, work mode, employment type ----

    @Test
    void locationMatchesCountryCaseInsensitively() {
        Preferences prefs = Preferences.newBuilder().addCountryCodes("de").build();
        assertEquals(1.0, part(scorer.score(seeker(prefs), job("a").setCountryCode("DE").build()), "location").getValue());
        assertEquals(0.0, part(scorer.score(seeker(prefs), job("a").setCountryCode("FR").build()), "location").getValue());
    }

    @Test
    void remoteJobFitsASeekerWhoAcceptsRemoteWhateverTheCountry() {
        Preferences prefs = Preferences.newBuilder().addCountryCodes("DE").addWorkModes(WorkMode.WORK_MODE_REMOTE).build();
        Match m = scorer.score(seeker(prefs), job("a").setCountryCode("FR").setWorkMode(WorkMode.WORK_MODE_REMOTE).build());
        assertEquals(1.0, part(m, "location").getValue());
    }

    @Test
    void workModeAndEmploymentTypeAreInOrOut() {
        Preferences prefs = Preferences.newBuilder()
                .addWorkModes(WorkMode.WORK_MODE_HYBRID).addEmploymentTypes(EmploymentType.EMPLOYMENT_TYPE_B2B).build();
        Match hit = scorer.score(seeker(prefs), job("a").setWorkMode(WorkMode.WORK_MODE_HYBRID)
                .setEmploymentType(EmploymentType.EMPLOYMENT_TYPE_B2B).build());
        Match miss = scorer.score(seeker(prefs), job("a").setWorkMode(WorkMode.WORK_MODE_ONSITE)
                .setEmploymentType(EmploymentType.EMPLOYMENT_TYPE_CONTRACT).build());

        assertEquals(1.0, part(hit, "work_mode").getValue());
        assertEquals(1.0, part(hit, "employment_type").getValue());
        assertEquals(0.0, part(miss, "work_mode").getValue());
        assertEquals(0.0, part(miss, "employment_type").getValue());
    }

    // ---- rate ----

    @ParameterizedTest(name = "rate {0} in [100, 200] -> {1}")
    @CsvSource({
            "100, 1.0",
            "150, 1.0",
            "200, 1.0",
            "90, 0.6666666666666667",   // 10% under the minimum
            "70, 0.0",                  // exactly 30% under
            "10, 0.0",                  // far under
            "220, 0.6666666666666667",  // 10% over the maximum
            "260, 0.0",                 // exactly 30% over
            "900, 0.0",
    })
    void rateFallsOffLinearlyToZeroAtThirtyPercentOutside(double rate, double expected) {
        Preferences prefs = Preferences.newBuilder().setMinAnnualRate(100).setMaxAnnualRate(200).build();
        Match m = scorer.score(seeker(prefs), job("a").setAnnualRate(rate).build());
        assertEquals(expected, part(m, "rate").getValue(), 1e-9);
        assertTrue(part(m, "rate").getCounted());
    }

    @Test
    void aRangeWithOnlyOneBoundIsOpenOnTheOtherSide() {
        Preferences onlyMin = Preferences.newBuilder().setMinAnnualRate(100).build();
        Preferences onlyMax = Preferences.newBuilder().setMaxAnnualRate(100).build();
        assertEquals(1.0, part(scorer.score(seeker(onlyMin), job("a").setAnnualRate(1e9).build()), "rate").getValue());
        assertEquals(1.0, part(scorer.score(seeker(onlyMax), job("a").setAnnualRate(1).build()), "rate").getValue());
    }

    @Test
    void zeroBoundsDoNotDivideByZero() {
        Preferences zeroMin = Preferences.newBuilder().setMinAnnualRate(0).build();
        Preferences zeroMax = Preferences.newBuilder().setMaxAnnualRate(0).build();
        assertEquals(1.0, part(scorer.score(seeker(zeroMin), job("a").setAnnualRate(5).build()), "rate").getValue());
        assertEquals(0.0, part(scorer.score(seeker(zeroMax), job("a").setAnnualRate(5).build()), "rate").getValue());
    }

    // ---- experience ----

    @ParameterizedTest(name = "{0} vs job {1} -> {2}")
    @CsvSource({
            "EXPERIENCE_LEVEL_MID, EXPERIENCE_LEVEL_MID, 1.0",
            "EXPERIENCE_LEVEL_MID, EXPERIENCE_LEVEL_SENIOR, 0.5",
            "EXPERIENCE_LEVEL_SENIOR, EXPERIENCE_LEVEL_MID, 0.5",
            "EXPERIENCE_LEVEL_INTERN, EXPERIENCE_LEVEL_MID, 0.0",
            "EXPERIENCE_LEVEL_LEAD, EXPERIENCE_LEVEL_JUNIOR, 0.0",
    })
    void experienceDependsOnTheDistanceBetweenLevels(ExperienceLevel wanted, ExperienceLevel asked, double expected) {
        Preferences prefs = Preferences.newBuilder().setExperienceLevel(wanted).build();
        Match m = scorer.score(seeker(prefs), job("a").setExperienceLevel(asked).build());
        assertEquals(expected, part(m, "experience").getValue());
    }

    // ---- the whole formula, weights, rounding ----

    @Test
    void scoreIsTheWeightedShareOfTheCountedParts() {
        // skills 1.0 (60), work_mode miss (8 x 0): 60 / 68 = 88.2 -> 88
        Preferences prefs = Preferences.newBuilder().addWorkModes(WorkMode.WORK_MODE_ONSITE).build();
        Match m = scorer.score(seeker(prefs, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)),
                job("a").setWorkMode(WorkMode.WORK_MODE_REMOTE).build());
        assertEquals(88, m.getScore());
    }

    @Test
    void weightsComeFromConfigurationAndAreReported() {
        Scorer heavyRate = new Scorer(new ScoringWeights(10, 0, 0, 0, 90, 0));
        Preferences prefs = Preferences.newBuilder().setMinAnnualRate(100).setMaxAnnualRate(200).build();
        Match m = heavyRate.score(seeker(prefs, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)),
                job("a").setAnnualRate(150).build());

        assertEquals(100, m.getScore());
        assertEquals(90.0, part(m, "rate").getWeight());
    }

    @Test
    void allWeightsZeroGivesZeroInsteadOfDividingByZero() {
        Scorer none = new Scorer(new ScoringWeights(0, 0, 0, 0, 0, 0));
        assertEquals(0, none.score(seeker(null, skill("a", Proficiency.PROFICIENCY_ADVANCED, 0)), job("a").build()).getScore());
    }

    @Test
    void partsAreAlwaysReportedInTheSameOrder() {
        Match m = scorer.score(seeker(null), job().build());
        assertEquals(java.util.List.of("skills", "location", "work_mode", "employment_type", "rate", "experience"),
                m.getPartsList().stream().map(ScorePart::getKey).toList());
    }

    @Test
    void sameInputGivesTheSameOutput() {
        SeekerProfile s = seeker(Preferences.newBuilder().addCountryCodes("DE").build(),
                skill("a", Proficiency.PROFICIENCY_INTERMEDIATE, 3));
        JobProfile j = job("a", "b").setCountryCode("DE").build();
        assertEquals(scorer.score(s, j), scorer.score(s, j));
    }
}
