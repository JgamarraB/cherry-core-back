package com.cherrycore.company.infrastructure.config;

import java.util.Arrays;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.application.PermissionDefinition;
import com.cherrycore.company.application.CompanySettings;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

@Configuration
public class CompanyConfig {

    @Bean
    CompanySettings companySettings(CherryCoreProperties properties) {
        return new CompanySettings(properties.companies().invitationTtl(),
                properties.storage().maxImageSize().toBytes(), properties.legal().termsVersion());
    }

    /**
     * CherryCore se registra como app y sincroniza sus propios permisos al
     * arrancar, igual que hará cualquier otra app. Va primero porque el resto
     * de la aplicación lo necesita.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner cherryCoreCatalogBootstrap(CatalogService catalog) {
        return args -> {
            catalog.ensureApplication(CompanyPermission.APPLICATION_CODE, CompanyPermission.APPLICATION_NAME);
            catalog.syncPermissions(CompanyPermission.APPLICATION_CODE, Arrays.stream(CompanyPermission.values())
                    .map(permission -> new PermissionDefinition(permission.code(), permission.description()))
                    .toList());
        };
    }
}
