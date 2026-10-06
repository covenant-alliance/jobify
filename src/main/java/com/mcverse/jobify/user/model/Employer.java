package com.mcverse.jobify.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mcverse.jobify.job.model.JobPost;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employers")
public class Employer extends User {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

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

    public void setCompany(Company company) { this.company = company; }

    public void addJobPost(JobPost jobPost) {
        jobPost.setEmployer(this);
        this.jobPosts.add(jobPost);
    }
}
