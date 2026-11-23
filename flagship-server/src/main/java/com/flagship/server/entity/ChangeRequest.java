package com.flagship.server.entity;

import com.flagship.core.model.Environment;
import jakarta.persistence.*;
import java.time.Instant;

/** A proposed change to one environment's configuration that waits for approval or for its time. */
@Entity
@Table(name = "change_requests")
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stops two people (or the scheduler) from deciding the same request at the same moment.
    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id", nullable = false)
    private FlagEntity flag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeRequestStatus status;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "apply_at")
    private Instant applyAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_version")
    private Integer appliedVersion;

    // If the change is a restore: the version that is restored.
    @Column(name = "restored_from")
    private Integer restoredFrom;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    // The proposed configuration as JSON text
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

    public ChangeRequestStatus getStatus() { return status; }
    public void setStatus(ChangeRequestStatus status) { this.status = status; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getApplyAt() { return applyAt; }
    public void setApplyAt(Instant applyAt) { this.applyAt = applyAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }

    public Integer getAppliedVersion() { return appliedVersion; }
    public void setAppliedVersion(Integer appliedVersion) { this.appliedVersion = appliedVersion; }

    public Integer getRestoredFrom() { return restoredFrom; }
    public void setRestoredFrom(Integer restoredFrom) { this.restoredFrom = restoredFrom; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
}