package com.cherrycore.identity.application;

import java.util.UUID;

import com.cherrycore.identity.domain.AccountType;

public record AuthenticatedAccount(UUID id, AccountType type) {
}
