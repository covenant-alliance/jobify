package com.mcverse.jobify.job.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A picture an employer attached to a job. The file lives under the upload folder; this row says where. */
@Entity
@Table(name = "job_images")
public class JobImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_post_id", nullable = false)
    private JobPost job;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false, length = 100)
    private String contentType;

    /** Display order, oldest first. */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected JobImage() {}

    public JobImage(JobPost job, String filePath, String contentType, int position) {
        this.job = job;
        this.filePath = filePath;
        this.contentType = contentType;
        this.position = position;
    }

    public String getId()          { return id; }
    public JobPost getJob()        { return job; }
    public String getFilePath()    { return filePath; }
    public String getContentType() { return contentType; }
    public int getPosition()       { return position; }
}
