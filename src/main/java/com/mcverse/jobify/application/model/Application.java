package com.mcverse.jobify.application.model;

import com.mcverse.jobify.job.model.JobPost;
import com.mcverse.jobify.user.model.Seeker;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A seeker's application to a job. One per (seeker, job); withdrawing keeps the row with status WITHDRAWN. */
@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_seeker_job",
                columnNames = {"seeker_id", "job_post_id"}))
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seeker_id", nullable = false)
    private Seeker seeker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost job;

    // VARCHAR rather than a database enum so new statuses never need a schema change (see LegacySchemaPatch).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(length = 2000)
    private String coverNote;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Application() {}

    public Application(Seeker seeker, JobPost job, String coverNote) {
        this.seeker = seeker;
        this.job = job;
        this.coverNote = coverNote;
        this.status = ApplicationStatus.APPLIED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Applies again after a withdrawal: same row, fresh start. */
    public void reapply(String coverNote) {
        this.coverNote = coverNote;
        changeStatus(ApplicationStatus.APPLIED);
        this.createdAt = this.updatedAt;
    }

    public void changeStatus(ApplicationStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public String getId()                  { return id; }
    public Seeker getSeeker()              { return seeker; }
    public JobPost getJob()                { return job; }
    public ApplicationStatus getStatus()   { return status; }
    public String getCoverNote()           { return coverNote; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getUpdatedAt()    { return updatedAt; }
}
