package com.mcverse.jobify.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String name;

    private String category;

    protected Skill() {}

    public Skill(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public String getId()       { return id; }
    public String getName()     { return name; }
    public String getCategory() { return category; }

    public void setName(String name)         { this.name = name; }
    public void setCategory(String category) { this.category = category; }
}
