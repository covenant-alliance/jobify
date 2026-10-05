package com.mcverse.jobify.job.model;

import com.mcverse.jobify.model.JobPost;
import com.mcverse.jobify.user.model.Seeker;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A job a seeker bookmarked. One per (seeker, job). */
@Entity
@Table(name = "saved_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_job_seeker_job",
                columnNames = {"seeker_id", "job_post_id"}))
public class SavedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seeker_id", nullable = false)
    private Seeker seeker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost job;

    @Column(nullable = false)
    private LocalDateTime savedAt;

    protected SavedJob() {}

    public SavedJob(Seeker seeker, JobPost job) {
        this.seeker = seeker;
        this.job = job;
        this.savedAt = LocalDateTime.now();
    }

    public String getId()              { return id; }
    public Seeker getSeeker()          { return seeker; }
    public JobPost getJob()            { return job; }
    public LocalDateTime getSavedAt()  { return savedAt; }
}
