package com.cherrycore.catalog.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationCode;

public interface ApplicationRepository {

    Optional<Application> findById(UUID id);

    Optional<Application> findByCode(ApplicationCode code);

    List<Application> findAll();

    void save(Application application);
}
