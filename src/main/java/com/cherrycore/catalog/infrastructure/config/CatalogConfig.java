package com.cherrycore.catalog.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cherrycore.catalog.application.CatalogSettings;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

@Configuration
public class CatalogConfig {

    @Bean
    CatalogSettings catalogSettings(CherryCoreProperties properties) {
        return new CatalogSettings(properties.storage().maxImageSize().toBytes());
    }
}
