package com.cherrycore.customer.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cherrycore.customer.application.CustomerSettings;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

@Configuration
public class CustomerConfig {

    @Bean
    CustomerSettings customerSettings(CherryCoreProperties properties) {
        return new CustomerSettings(properties.legal().termsVersion(), properties.customers().maxAddresses());
    }
}
