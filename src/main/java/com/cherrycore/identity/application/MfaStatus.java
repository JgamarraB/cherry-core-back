package com.cherrycore.identity.application;

public record MfaStatus(boolean enabled, boolean required, int recoveryCodesLeft) {
}
