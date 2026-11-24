package com.flagship.server.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A named, reusable rule condition of one project. */
@Entity
@Table(name = "segments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "segment_key"}))
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "segment_key", nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    // The condition tree as JSON text (same shape as a rule's "condition")
    @Column(name = "condition_json", nullable = false, length = 20000)
    private String conditionJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}