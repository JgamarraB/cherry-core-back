/*
 * El mismo usuario de Google/Apple puede tener cuenta de cliente y de
 * backoffice (son cuentas distintas): el vínculo es único por tipo de cuenta.
 */
ALTER TABLE federated_identities ADD COLUMN account_type VARCHAR(20);

UPDATE federated_identities f SET account_type = a.type FROM accounts a WHERE a.id = f.account_id;

ALTER TABLE federated_identities ALTER COLUMN account_type SET NOT NULL;
ALTER TABLE federated_identities ADD CONSTRAINT chk_federated_identities_account_type
    CHECK (account_type IN ('CUSTOMER', 'STAFF'));

DROP INDEX uq_federated_identities_subject;
CREATE UNIQUE INDEX uq_federated_identities_subject
    ON federated_identities (provider, provider_subject, account_type)
    WHERE deleted_at IS NULL;
