package com.flagship.server.push;

import com.flagship.core.model.Environment;

/** "Something changed in this project." environment is null when every environment is affected. */
public record FlagsChangedEvent(String projectKey, String flagKey, Environment environment) { }