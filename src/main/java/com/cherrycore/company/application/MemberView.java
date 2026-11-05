package com.cherrycore.company.application;

import java.util.UUID;

import com.cherrycore.company.domain.MembershipStatus;

public record MemberView(
        UUID membershipId,
        UUID accountId,
        String email,
        String firstName,
        String lastName,
        UUID roleId,
        String roleCode,
        String roleName,
        MembershipStatus status
) {
}
