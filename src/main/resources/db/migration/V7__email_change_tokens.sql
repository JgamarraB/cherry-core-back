/*
 * Cambio de email: el token viaja al email NUEVO y guarda a qué email se cambia.
 */
ALTER TABLE one_time_tokens ADD COLUMN payload VARCHAR(254);

ALTER TABLE one_time_tokens DROP CONSTRAINT chk_one_time_tokens_purpose;
ALTER TABLE one_time_tokens ADD CONSTRAINT chk_one_time_tokens_purpose
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'));
