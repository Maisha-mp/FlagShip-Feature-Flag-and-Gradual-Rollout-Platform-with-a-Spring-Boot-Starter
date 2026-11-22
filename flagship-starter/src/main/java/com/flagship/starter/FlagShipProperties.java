package com.flagship.starter;

import com.flagship.core.model.Environment;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings an app can put in application.properties under "flagship.*". */
@ConfigurationProperties(prefix = "flagship")
public class FlagShipProperties {

    /** Switch the whole starter on or off. */
    private boolean enabled = true;

    /** Base URL of the FlagShip server. */
    private String serverUrl = "http://localhost:8080";

    /** Project key to load flags for, e.g. checkout-app. */
    private String project;

    /** Which environment's settings to evaluate. */
    private Environment environment = Environment.PRODUCTION;

    /** How often to re-download the snapshot. */
    private Duration pollInterval = Duration.ofSeconds(30);

    /** Longest the starter may delay application startup while loading flags. */
    private Duration startupTimeout = Duration.ofSeconds(2);

    /** Longest a single download may take (used for every refresh). */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /** Local copy of the last good snapshot, used when the server is unreachable. */
    private Path snapshotFile = Path.of(".flagship", "snapshot.json");

    /** How many evaluation results to remember; 0 turns the cache off. */
    private int cacheSize = 10_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }

    public Duration getStartupTimeout() { return startupTimeout; }
    public void setStartupTimeout(Duration startupTimeout) { this.startupTimeout = startupTimeout; }

    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }

    public Path getSnapshotFile() { return snapshotFile; }
    public void setSnapshotFile(Path snapshotFile) { this.snapshotFile = snapshotFile; }

    public int getCacheSize() { return cacheSize; }
    public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
}