package com.mcverse.jobify.user.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** One line of a company's activity feed: who did what, written in the same transaction as the action. Plain text. */
@Entity
@Table(name = "company_activity",
        indexes = @Index(name = "idx_company_activity_company_created", columnList = "companyId, createdAt"))
public class CompanyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String companyId;

    /** Username of the person who did it. */
    @Column(nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private ActivityType type;

    @Column(nullable = false, length = 400)
    private String summary;

    private Integer jobId;

    private String applicationId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CompanyActivity() {}

    public CompanyActivity(String companyId, String actor, ActivityType type, String summary, Integer jobId,
                           String applicationId) {
        this.companyId = companyId;
        this.actor = actor;
        this.type = type;
        this.summary = summary;
        this.jobId = jobId;
        this.applicationId = applicationId;
    }

    public String getId()             { return id; }
    public String getCompanyId()      { return companyId; }
    public String getActor()          { return actor; }
    public ActivityType getType()     { return type; }
    public String getSummary()        { return summary; }
    public Integer getJobId()         { return jobId; }
    public String getApplicationId()  { return applicationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
