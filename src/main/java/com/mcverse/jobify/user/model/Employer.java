package com.mcverse.jobify.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mcverse.jobify.job.model.JobPost;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employers")
public class Employer extends User {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** When the person connected to their company; null for rows from before this was recorded. */
    private LocalDateTime companyJoinedAt;

    // VARCHAR, not a database enum, so a new role never needs a schema change
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private CompanyRole companyRole;

    @JsonIgnore
    @OneToMany(mappedBy = "employer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPost> jobPosts;

    protected Employer() {}

    public Employer(String name, String lastName, String username) {
        super(name, lastName, username);
        this.jobPosts = new ArrayList<>();
    }

    public Company getCompany()          { return company; }
    public List<JobPost> getJobPosts()   { return jobPosts; }

    /**
     * The person's role in their company; null without a company. An employer whose company was set before roles
     * existed counts as its owner, so nothing depends on the startup backfill having run.
     */
    public CompanyRole getCompanyRole() {
        if (company == null) return null;
        return companyRole != null ? companyRole : CompanyRole.OWNER;
    }

    public void setCompanyRole(CompanyRole companyRole) { this.companyRole = companyRole; }

    /** When the person connected to their company: the recorded time, else when their account was created. */
    public LocalDateTime getCompanyJoinedAt() {
        return companyJoinedAt != null ? companyJoinedAt : getCreationDate();
    }

    /** Connects the person to a company with a role. */
    public void joinCompany(Company company, CompanyRole role) {
        this.company = company;
        this.companyRole = role;
        this.companyJoinedAt = LocalDateTime.now();
    }

    /** Disconnects the person from their company (the company and its jobs stay). */
    public void leaveCompany() {
        this.company = null;
        this.companyRole = null;
        this.companyJoinedAt = null;
    }

    public void setCompany(Company company) { this.company = company; }

    public void addJobPost(JobPost jobPost) {
        jobPost.setEmployer(this);
        this.jobPosts.add(jobPost);
    }
}
