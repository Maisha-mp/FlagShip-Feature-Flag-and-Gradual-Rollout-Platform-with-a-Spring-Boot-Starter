package com.flagship.server.service;

import com.flagship.server.api.ChangeRequestResponse;

/** Either the change was applied right away (applied) or it was queued (queued). One of them is null. */
public record Outcome<T>(T applied, ChangeRequestResponse queued) { }