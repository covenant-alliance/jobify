package com.mcverse.jobify.user.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seekers")
public class Seeker extends User {

    private boolean isIndependent;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_id")
    private Cv cv;

    @OneToMany(mappedBy = "seeker", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Education> educations = new ArrayList<>();

    @OneToMany(mappedBy = "seeker", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Certification> certifications = new ArrayList<>();

    @OneToMany(mappedBy = "seeker", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProfessionalExperience> professionalExperiences = new ArrayList<>();

    @OneToMany(mappedBy = "seeker", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SeekerSkill> seekerSkills = new ArrayList<>();

    protected Seeker() {}

    public Seeker(String name, String lastName, String username, boolean isIndependent) {
        super(name, lastName, username);
        this.isIndependent = isIndependent;
    }

    public boolean isIndependent()             { return isIndependent; }
    public Cv getCv()                          { return cv; }
    public List<Education> getEducations()                           { return educations; }
    public List<Certification> getCertifications()                   { return certifications; }
    public List<ProfessionalExperience> getProfessionalExperiences()  { return professionalExperiences; }
    public List<SeekerSkill> getSeekerSkills()                        { return seekerSkills; }

    public void setIndependent(boolean isIndependent)              { this.isIndependent = isIndependent; }
    public void setCv(Cv cv)                                       { this.cv = cv; }
}
