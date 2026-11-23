package com.flagship.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "key" is a reserved word in SQL, so the column gets a different name.
    @Column(name = "project_key", nullable = false, unique = true, length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    // If true, changes to PRODUCTION must be approved by a second person.
    // The default lets Hibernate add this column to a table that already has rows.
    @Column(name = "require_approval", nullable = false, columnDefinition = "boolean default false")
    private boolean requireApproval;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isRequireApproval() { return requireApproval; }
    public void setRequireApproval(boolean requireApproval) { this.requireApproval = requireApproval; }

    public Instant getCreatedAt() { return createdAt; }
}