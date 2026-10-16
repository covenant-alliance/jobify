package com.mcverse.jobify.job.model;

import com.mcverse.jobify.common.model.EmploymentType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a seeker is looking for. One row per seeker, replaced as a whole. Lives in the job slice because it uses
 * {@link WorkMode}, {@link RateType} and {@link ExperienceLevel}, which the user slice must not depend on.
 * The pay range is in {@link #rateType}'s unit; the matching service gets it converted to a yearly amount.
 */
@Entity
@Table(name = "seeker_job_preferences")
public class SeekerJobPreferences {

    @Id
    @Column(name = "seeker_id")
    private String seekerId;

    @Column(length = 100)
    private String preferredJobTitle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seeker_preference_countries", joinColumns = @JoinColumn(name = "seeker_id"))
    @Column(name = "country_code", length = 2)
    private Set<String> countryCodes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seeker_preference_work_modes", joinColumns = @JoinColumn(name = "seeker_id"))
    @Column(name = "work_mode")
    @Enumerated(EnumType.STRING)
    private Set<WorkMode> workModes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seeker_preference_employment_types", joinColumns = @JoinColumn(name = "seeker_id"))
    @Column(name = "employment_type")
    @Enumerated(EnumType.STRING)
    private Set<EmploymentType> employmentTypes = new LinkedHashSet<>();

    private Double minRate;
    private Double maxRate;

    @Enumerated(EnumType.STRING)
    private RateType rateType;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected SeekerJobPreferences() {}

    public SeekerJobPreferences(String seekerId) {
        this.seekerId = seekerId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getSeekerId()                 { return seekerId; }
    public String getPreferredJobTitle()        { return preferredJobTitle; }
    public Set<String> getCountryCodes()        { return countryCodes; }
    public Set<WorkMode> getWorkModes()         { return workModes; }
    public Set<EmploymentType> getEmploymentTypes() { return employmentTypes; }
    public Double getMinRate()                  { return minRate; }
    public Double getMaxRate()                  { return maxRate; }
    public RateType getRateType()               { return rateType; }
    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public LocalDateTime getUpdatedAt()         { return updatedAt; }

    public void setPreferredJobTitle(String preferredJobTitle)  { this.preferredJobTitle = preferredJobTitle; }
    public void setMinRate(Double minRate)                      { this.minRate = minRate; }
    public void setMaxRate(Double maxRate)                      { this.maxRate = maxRate; }
    public void setRateType(RateType rateType)                  { this.rateType = rateType; }
    public void setExperienceLevel(ExperienceLevel level)       { this.experienceLevel = level; }
    public void touch()                                         { this.updatedAt = LocalDateTime.now(); }
}
