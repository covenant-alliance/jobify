package com.mcverse.jobify.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    /** Path of the logo file under the upload folder; null when the company has no logo. */
    @Column(length = 255)
    private String logoPath;

    @Column(length = 100)
    private String logoContentType;

    /** Changes with every upload, so the logo URL changes and caches fetch the new picture. */
    private Long logoVersion;

    protected Company() {}

    public Company(String name) {
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getLogoPath()        { return logoPath; }
    public String getLogoContentType() { return logoContentType; }
    public Long getLogoVersion()       { return logoVersion; }

    public void setLogo(String path, String contentType, long version) {
        this.logoPath = path;
        this.logoContentType = contentType;
        this.logoVersion = version;
    }

    public void clearLogo() {
        this.logoPath = null;
        this.logoContentType = null;
        this.logoVersion = null;
    }
}
