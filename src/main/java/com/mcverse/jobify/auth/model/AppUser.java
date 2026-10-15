package com.mcverse.jobify.auth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** False when an administrator disabled the account: no login, and existing tokens stop working. */
    @Column(nullable = false, columnDefinition = "boolean default true not null")
    private boolean enabled = true;

    protected AppUser() {}

    public AppUser(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId()       { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role getRole()     { return role; }

    public boolean isEnabled() { return enabled; }

    public void setPassword(String password) { this.password = password; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
