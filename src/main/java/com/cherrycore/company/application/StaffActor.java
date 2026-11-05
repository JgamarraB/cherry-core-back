package com.cherrycore.company.application;

import java.util.UUID;

/**
 * Quién hace la operación: la cuenta de backoffice y la empresa con la que
 * está trabajando (la que eligió al entrar).
 */
public record StaffActor(UUID accountId, UUID companyId) {
}
