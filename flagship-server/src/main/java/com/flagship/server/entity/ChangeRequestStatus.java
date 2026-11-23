package com.flagship.server.entity;

/** Where a change request is in its life. */
public enum ChangeRequestStatus {
    PENDING_APPROVAL,   // waits for a second person
    SCHEDULED,          // waits for its apply time
    APPLIED,            // done, a new version exists
    REJECTED,
    CANCELLED,
    FAILED              // a scheduled change could not be applied
}