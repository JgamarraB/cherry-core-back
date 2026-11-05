package com.cherrycore.catalog.infrastructure.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.application.PermissionDefinition;
import com.cherrycore.catalog.domain.CatalogErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.TokenClaims;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Las apps sincronizan aquí su catálogo de permisos al arrancar.
 *
 * <p>Se llama desde el BACKEND de la app con un token de máquina
 * (grant client_credentials, scope {@code catalog.sync}). La app solo puede
 * tocar su propio catálogo: el código de app sale del token, no de la petición.</p>
 */
@RestController
public class AppPermissionsController {

    public static final String PATH = "/api/apps/permissions";
    private static final String HAS_SYNC_SCOPE = "hasAuthority('SCOPE_catalog.sync')";
    private static final int MAX_CODE = 100;
    private static final int MAX_DESCRIPTION = 255;

    private final CatalogService catalog;

    public AppPermissionsController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PutMapping(PATH)
    @PreAuthorize(HAS_SYNC_SCOPE)
    public ApiSuccessResponse<List<String>> sync(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody SyncRequest body) {
        String application = token.getClaimAsString(TokenClaims.APPLICATION);
        if (application == null) {
            throw new DomainException(CatalogErrorCode.APPLICATION_NOT_FOUND);
        }
        List<PermissionDefinition> definitions = body.permissions().stream()
                .map(permission -> new PermissionDefinition(permission.code(), permission.description()))
                .toList();
        return ApiSuccessResponse.of(catalog.syncPermissions(application, definitions).stream()
                .map(permission -> permission.code().value())
                .toList());
    }

    public record SyncRequest(
            @NotNull @Size(max = CatalogService.MAX_PERMISSIONS_PER_APPLICATION) List<@Valid PermissionItem> permissions
    ) {
    }

    public record PermissionItem(
            @NotBlank @Size(max = MAX_CODE) String code,
            @Size(max = MAX_DESCRIPTION) String description
    ) {
    }
}
