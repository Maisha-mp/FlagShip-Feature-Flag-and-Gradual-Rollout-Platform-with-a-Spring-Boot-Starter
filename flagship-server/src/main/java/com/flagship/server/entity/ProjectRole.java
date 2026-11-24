package com.flagship.server.entity;

/** A person's role in ONE project. Each role includes everything the roles before it may do. */
public enum ProjectRole {
    VIEWER,     // read flags, history, change requests
    EDITOR,     // change flags, request changes, restore, cancel own requests
    APPROVER,   // approve or reject change requests
    ADMIN;      // project settings and members

    public boolean atLeast(ProjectRole needed) {
        return ordinal() >= needed.ordinal();
    }
}