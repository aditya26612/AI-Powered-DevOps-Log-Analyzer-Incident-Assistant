package com.project.log_layer.tenant;

public enum OrganizationRole {
    OWNER, ADMIN, DEVOPS, VIEWER;

    public boolean managesOrganization() {
        return this == OWNER || this == ADMIN;
    }

    public boolean managesApplications() {
        return this != VIEWER;
    }
}
