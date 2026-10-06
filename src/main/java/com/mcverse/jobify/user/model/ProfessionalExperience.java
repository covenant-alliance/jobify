package com.mcverse.jobify.user.model;

import com.mcverse.jobify.common.model.EmploymentType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "professional_experience")
public class ProfessionalExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seeker_id", nullable = false)
    private Seeker seeker;

    @Column(nullable = false)
    private String jobTitle;

    @Column(nullable = false)
    private String companyName;

    private String location;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) // long text: PostgreSQL text, MySQL longtext (not a LOB handle / oid)
    private String description;

    protected ProfessionalExperience() {}

    public ProfessionalExperience(Seeker seeker, String jobTitle, String companyName, String location,
                                   EmploymentType employmentType, LocalDate startDate, LocalDate endDate,
                                   String description) {
        this.seeker = seeker;
        this.jobTitle = jobTitle;
        this.companyName = companyName;
        this.location = location;
        this.employmentType = employmentType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
    }

    public String getId()                       { return id; }
    public Seeker getSeeker()                   { return seeker; }
    public String getJobTitle()                 { return jobTitle; }
    public String getCompanyName()              { return companyName; }
    public String getLocation()                 { return location; }
    public EmploymentType getEmploymentType()   { return employmentType; }
    public LocalDate getStartDate()             { return startDate; }
    public LocalDate getEndDate()               { return endDate; }
    public String getDescription()              { return description; }

    public void setJobTitle(String jobTitle)               { this.jobTitle = jobTitle; }
    public void setCompanyName(String companyName)         { this.companyName = companyName; }
    public void setLocation(String location)               { this.location = location; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public void setStartDate(LocalDate startDate)          { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate)              { this.endDate = endDate; }
    public void setDescription(String description)        { this.description = description; }
}
