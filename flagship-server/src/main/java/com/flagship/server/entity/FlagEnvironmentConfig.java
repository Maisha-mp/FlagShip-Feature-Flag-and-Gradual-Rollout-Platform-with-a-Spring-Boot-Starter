package com.flagship.server.entity;

import com.flagship.core.model.Environment;
import jakarta.persistence.*;

@Entity
@Table(name = "flag_environment_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"flag_id", "environment"}))
public class FlagEnvironmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id", nullable = false)
    private FlagEntity flag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Column(name = "kill_switch_on", nullable = false)
    private boolean killSwitchOn;

    @Column(name = "off_variation", nullable = false, length = 100)
    private String offVariation;

    @Column(name = "default_variation", nullable = false, length = 100)
    private String defaultVariation;

    // userKey -> variation name, as JSON text
    @Column(name = "user_targets_json", nullable = false, length = 20000)
    private String userTargetsJson = "{}";

    // list of rules, as JSON text
    @Column(name = "rules_json", nullable = false, length = 20000)
    private String rulesJson = "[]";

    // rollout as JSON text; null means no rollout
    @Column(name = "rollout_json", length = 20000)
    private String rolloutJson;

    public Long getId() { return id; }

    public FlagEntity getFlag() { return flag; }
    public void setFlag(FlagEntity flag) { this.flag = flag; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public boolean isKillSwitchOn() { return killSwitchOn; }
    public void setKillSwitchOn(boolean killSwitchOn) { this.killSwitchOn = killSwitchOn; }

    public String getOffVariation() { return offVariation; }
    public void setOffVariation(String offVariation) { this.offVariation = offVariation; }

    public String getDefaultVariation() { return defaultVariation; }
    public void setDefaultVariation(String defaultVariation) { this.defaultVariation = defaultVariation; }

    public String getUserTargetsJson() { return userTargetsJson; }
    public void setUserTargetsJson(String userTargetsJson) { this.userTargetsJson = userTargetsJson; }

    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }

    public String getRolloutJson() { return rolloutJson; }
    public void setRolloutJson(String rolloutJson) { this.rolloutJson = rolloutJson; }
}