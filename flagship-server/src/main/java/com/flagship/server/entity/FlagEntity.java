package com.flagship.server.entity;

import com.flagship.core.model.FlagType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "flags",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "flag_key"}))
public class FlagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "flag_key", nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 20)
    private FlagType type;

    // Variations as JSON text, e.g. {"on": true, "off": false}
    @Column(name = "variations_json", nullable = false, length = 20000)
    private String variationsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public FlagType getType() { return type; }
    public void setType(FlagType type) { this.type = type; }

    public String getVariationsJson() { return variationsJson; }
    public void setVariationsJson(String variationsJson) { this.variationsJson = variationsJson; }

    public Instant getCreatedAt() { return createdAt; }
}