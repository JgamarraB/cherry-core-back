package com.cherrycore.company.application;

import java.util.UUID;

public record CompanyApplicationView(UUID applicationId, String code, String name, boolean enabled) {
}
