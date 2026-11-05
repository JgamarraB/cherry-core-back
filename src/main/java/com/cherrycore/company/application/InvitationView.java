package com.cherrycore.company.application;

import java.time.Instant;
import java.util.UUID;

public record InvitationView(UUID id, String email, UUID roleId, String roleName, Instant expiresAt) {
}
