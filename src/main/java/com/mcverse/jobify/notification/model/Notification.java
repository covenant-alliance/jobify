package com.mcverse.jobify.notification.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A message for one user. Plain text only: clients must show it as text, never as HTML. */
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notification_recipient_created", columnList = "recipientUsername, createdAt"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String recipientUsername;

    // VARCHAR rather than a database enum so new types never need a schema change (see LegacySchemaPatch).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean isRead;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Optional pointers so the UI can link to the job or application the message is about. */
    private Integer jobId;
    private String applicationId;

    protected Notification() {}

    public Notification(String recipientUsername, NotificationType type, String title, String body,
                        Integer jobId, String applicationId) {
        this.recipientUsername = recipientUsername;
        this.type = type;
        this.title = title;
        this.body = body;
        this.jobId = jobId;
        this.applicationId = applicationId;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        this.isRead = true;
    }

    public String getId()                 { return id; }
    public String getRecipientUsername()  { return recipientUsername; }
    public NotificationType getType()     { return type; }
    public String getTitle()              { return title; }
    public String getBody()               { return body; }
    public boolean isRead()               { return isRead; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public Integer getJobId()             { return jobId; }
    public String getApplicationId()      { return applicationId; }
}
