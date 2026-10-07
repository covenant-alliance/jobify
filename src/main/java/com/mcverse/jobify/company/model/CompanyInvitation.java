package com.mcverse.jobify.company.model;

import com.mcverse.jobify.user.model.Company;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** An owner's invitation to an employer account to join a company. Valid for 14 days, answered once. */
@Entity
@Table(name = "company_invitations", indexes = {
        @Index(name = "idx_company_invitations_invitee", columnList = "inviteeUsername, status"),
        @Index(name = "idx_company_invitations_company", columnList = "company_id, status")})
public class CompanyInvitation {

    public static final int VALID_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String inviteeUsername;

    @Column(nullable = false)
    private String invitedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime respondedAt;

    protected CompanyInvitation() {}

    public CompanyInvitation(Company company, String inviteeUsername, String invitedBy, LocalDateTime now) {
        this.company = company;
        this.inviteeUsername = inviteeUsername;
        this.invitedBy = invitedBy;
        this.createdAt = now;
        this.expiresAt = now.plusDays(VALID_DAYS);
    }

    public String getId()                   { return id; }
    public Company getCompany()             { return company; }
    public String getInviteeUsername()      { return inviteeUsername; }
    public String getInvitedBy()            { return invitedBy; }
    public InvitationStatus getStatus()     { return status; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
    public LocalDateTime getExpiresAt()     { return expiresAt; }
    public LocalDateTime getRespondedAt()   { return respondedAt; }

    /** Still waiting for an answer and not past its date. */
    public boolean isOpen(LocalDateTime now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public void close(InvitationStatus newStatus, LocalDateTime now) {
        this.status = newStatus;
        this.respondedAt = now;
    }
}
