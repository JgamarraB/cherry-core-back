package com.cherrycore.auth.infrastructure.social;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Proveedores sociales configurados. A diferencia del repositorio estándar,
 * admite estar vacío: sin credenciales, el login social queda desactivado.
 */
class SocialClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final Map<String, ClientRegistration> registrations;

    SocialClientRegistrations(Map<String, ClientRegistration> registrations) {
        this.registrations = Map.copyOf(registrations);
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }

    List<String> registrationIds() {
        return registrations.keySet().stream().sorted().toList();
    }
}
