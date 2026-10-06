package com.mcverse.jobify.job.model;

import com.mcverse.jobify.common.model.EmploymentType;
import com.mcverse.jobify.user.model.Employer;
import com.mcverse.jobify.user.model.Skill;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_posts")
public class JobPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer postId;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id")
    private Employer employer;

    @Column(nullable = false)
    private String jobTitle;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) // long text: PostgreSQL text, MySQL longtext (not a LOB handle / oid)
    private String jobDescription;

    /** Hourly equivalent of {@link #rate}; kept for clients that still read {@code hourlyRate}. */
    private double hourlyRate;

    /** Pay amount, in the unit given by {@link #rateType}. Nullable only for rows awaiting the backfill. */
    private Double rate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private RateType rateType;

    private String location;

    // VARCHAR, not a database enum type: with ddl-auto=update a database enum's value list is never widened,
    // so adding an enum constant would break every existing database.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private boolean available = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_required_skills",
            joinColumns = @JoinColumn(name = "job_post_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> requiredSkills = new ArrayList<>();

    protected JobPost() {}

    public JobPost(String jobTitle, String jobDescription, double rate, RateType rateType) {
        this.jobTitle = jobTitle;
        this.jobDescription = jobDescription;
        setCompensation(rate, rateType);
        this.available = true;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getPostId()         { return postId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getJobTitle()        { return jobTitle; }
    public String getJobDescription()  { return jobDescription; }
    public double getHourlyRate()      { return hourlyRate; }
    public boolean isAvailable()       { return available; }

    public void setPostId(Integer postId)                { this.postId = postId; }
    public void setJobTitle(String jobTitle)             { this.jobTitle = jobTitle; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public Double getRate()                              { return rate; }
    public RateType getRateType()                        { return rateType; }

    /** Sets the pay and keeps the derived hourly equivalent in step. */
    public void setCompensation(double rate, RateType rateType) {
        this.rate = rate;
        this.rateType = rateType;
        this.hourlyRate = rateType.toHourly(rate);
    }
    public void setAvailable(boolean available)          { this.available = available; }
    public Employer getEmployer()                        { return employer; }
    public void setEmployer(Employer employer)           { this.employer = employer; }
    public String getLocation()                          { return location; }
    public void setLocation(String location)              { this.location = location; }
    public WorkMode getWorkMode()                         { return workMode; }
    public void setWorkMode(WorkMode workMode)            { this.workMode = workMode; }
    public EmploymentType getEmploymentType()             { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public List<Skill> getRequiredSkills()                { return requiredSkills; }
    public void setRequiredSkills(List<Skill> requiredSkills) { this.requiredSkills = requiredSkills; }
}
