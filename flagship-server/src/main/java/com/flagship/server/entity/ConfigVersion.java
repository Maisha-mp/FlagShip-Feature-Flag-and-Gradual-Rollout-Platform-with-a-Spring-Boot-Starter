package com.flagship.server.entity;

import com.flagship.core.model.Environment;
import jakarta.persistence.*;
import java.time.Instant;

/** One saved version of one environment's configuration of one flag. */
@Entity
@Table(name = "flag_config_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"flag_id", "environment", "version_number"}))
public class ConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id", nullable = false)
    private FlagEntity flag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // If this version was made by restoring an older one: that version's number.
    @Column(name = "restored_from")
    private Integer restoredFrom;

    // The full configuration as JSON text
    @Column(name = "config_json", nullable = false, length = 100000)
    private String configJson;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public FlagEntity getFlag() { return flag; }
    public void setFlag(FlagEntity flag) { this.flag = flag; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }

    public Integer getRestoredFrom() { return restoredFrom; }
    public void setRestoredFrom(Integer restoredFrom) { this.restoredFrom = restoredFrom; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
}