package com.mcverse.jobify.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One step in the life of an {@link Application}: created (from {@code null} to APPLIED), moved by the employer,
 * withdrawn, or re-applied (WITHDRAWN to APPLIED). Rows are only ever added, so the employer's funnel can say how far
 * applications really got and how long they stayed in each stage, which the current status alone cannot.
 *
 * <p>The foreign key cascades on delete in the database, so removing an application (for example when an account
 * deletion is approved) removes its history without any extra code.
 */
@Entity
@Table(name = "application_status_changes")
public class ApplicationStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Application application;

    /** The status before the change; null for the creation of the application. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ApplicationStatus toStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    /** Username of whoever caused the change (the applicant or the employer); "history-backfill" for inferred rows. */
    private String changedBy;

    protected ApplicationStatusChange() {}

    public ApplicationStatusChange(Application application, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                                   LocalDateTime changedAt, String changedBy) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public String getId()                   { return id; }
    public Application getApplication()     { return application; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus()  { return toStatus; }
    public LocalDateTime getChangedAt()     { return changedAt; }
    public String getChangedBy()            { return changedBy; }
}
