package com.cherrycore.auth.infrastructure.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.identity.application.SecurityActivityItem;
import com.cherrycore.identity.application.SecurityActivityService;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;
import com.cherrycore.shared.infrastructure.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Actividad de seguridad de Mi cuenta: mis últimos eventos, por páginas.
 */
@RestController
@RequestMapping(AccountController.BASE)
public class AccountActivityController {

    public static final String ACTIVITY = "/activity";

    private final SecurityActivityService activity;

    public AccountActivityController(SecurityActivityService activity) {
        this.activity = activity;
    }

    @GetMapping(ACTIVITY)
    public ApiSuccessResponse<PageResponse<SecurityActivityItem>> activity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        UUID accountId = CurrentAccount.id()
                .orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
        return ApiSuccessResponse.of(PageResponse.from(
                activity.activity(accountId, new PageQuery(page, size), RequestContextResolver.from(request))));
    }
}
