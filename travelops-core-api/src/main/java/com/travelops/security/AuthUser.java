package com.travelops.security;

/**
 * The authenticated principal. Built from the token's subject alone, so authenticating
 * a request never needs a database query.
 */
public record AuthUser(Long id) {
}
