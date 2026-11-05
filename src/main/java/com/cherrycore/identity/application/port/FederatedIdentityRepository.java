package com.cherrycore.identity.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.FederatedIdentity;
import com.cherrycore.identity.domain.SocialProvider;

public interface FederatedIdentityRepository {

    Optional<FederatedIdentity> findByProviderAndSubject(SocialProvider provider, String subject, AccountType type);

    List<FederatedIdentity> findByAccount(UUID accountId);

    void save(FederatedIdentity identity);

    void deleteByAccount(UUID accountId);
}
