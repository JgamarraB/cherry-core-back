package com.cherrycore.company.application;

import java.util.UUID;

/**
 * Empresa con la que una persona de backoffice puede entrar.
 */
public record TenantOption(UUID companyId, String name, String logoUrl, String roleCode, String roleName) {
}
