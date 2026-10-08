package com.dispatch.user;

/**
 * ADMIN manages configuration (rules, services, devices, users, simulator);
 * OPERATOR works incidents. Both can read everything.
 */
public enum Role {
    ADMIN,
    OPERATOR
}
