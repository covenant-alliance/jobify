package com.mcverse.jobify.user.model;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.common.validation.ValidSalaryRange;
import jakarta.persistence.*;

@Entity
@Table(name = "job_preferences")
@ValidSalaryRange
public class JobPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String preferredJobTitle;
    private String locationPreferences;
    private double minSalaryExpectation;
    private double maxSalaryExpectation;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    private String workingHours;

    private String experienceLevel;

    public JobPreferences() {}

    public String getId()                        { return id; }
    public String getPreferredJobTitle()         { return preferredJobTitle; }
    public String getLocationPreferences()       { return locationPreferences; }
    public double getMinSalaryExpectation()      { return minSalaryExpectation; }
    public double getMaxSalaryExpectation()      { return maxSalaryExpectation; }
    public EmploymentType getEmploymentType()    { return employmentType; }
    public String getWorkingHours()              { return workingHours; }
    public String getExperienceLevel()           { return experienceLevel; }

    public void setPreferredJobTitle(String preferredJobTitle)       { this.preferredJobTitle = preferredJobTitle; }
    public void setLocationPreferences(String locationPreferences)   { this.locationPreferences = locationPreferences; }
    public void setMinSalaryExpectation(double minSalaryExpectation) { this.minSalaryExpectation = minSalaryExpectation; }
    public void setMaxSalaryExpectation(double maxSalaryExpectation) { this.maxSalaryExpectation = maxSalaryExpectation; }
    public void setEmploymentType(EmploymentType employmentType)     { this.employmentType = employmentType; }
    public void setWorkingHours(String workingHours)                 { this.workingHours = workingHours; }
    public void setExperienceLevel(String experienceLevel)           { this.experienceLevel = experienceLevel; }
}
