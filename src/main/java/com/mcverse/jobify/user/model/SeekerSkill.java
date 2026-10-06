package com.mcverse.jobify.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "seeker_skills", uniqueConstraints = @UniqueConstraint(columnNames = {"seeker_id", "skill_id"}))
public class SeekerSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seeker_id", nullable = false)
    private Seeker seeker;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProficiencyLevel proficiencyLevel;

    private Double yearsOfExperience;

    protected SeekerSkill() {}

    public SeekerSkill(Seeker seeker, Skill skill, ProficiencyLevel proficiencyLevel, Double yearsOfExperience) {
        this.seeker = seeker;
        this.skill = skill;
        this.proficiencyLevel = proficiencyLevel;
        this.yearsOfExperience = yearsOfExperience;
    }

    public String getId()                            { return id; }
    public Seeker getSeeker()                        { return seeker; }
    public Skill getSkill()                          { return skill; }
    public ProficiencyLevel getProficiencyLevel()    { return proficiencyLevel; }
    public Double getYearsOfExperience()             { return yearsOfExperience; }

    public void setProficiencyLevel(ProficiencyLevel proficiencyLevel) { this.proficiencyLevel = proficiencyLevel; }
    public void setYearsOfExperience(Double yearsOfExperience)         { this.yearsOfExperience = yearsOfExperience; }
}
