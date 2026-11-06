package com.cherrycore.company.application;

import java.util.List;
import java.util.UUID;

/**
 * Lo que puede hacer la persona en la empresa activa: su rol y los permisos de
 * CherryCore que tiene (el dueño, todos). El frontend lo usa para no enseñar
 * acciones que no puede hacer; el backend vuelve a comprobarlo en cada petición.
 *
 * @param membershipId su pertenencia a la empresa (para reconocerse en el listado de miembros)
 * @param permissions  códigos de {@link com.cherrycore.company.domain.CompanyPermission}
 */
public record MyCompanyAccess(
        UUID companyId,
        UUID membershipId,
        String roleCode,
        String roleName,
        boolean owner,
        List<String> permissions
) {
}
