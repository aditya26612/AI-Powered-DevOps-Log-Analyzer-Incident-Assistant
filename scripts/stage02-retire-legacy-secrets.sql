-- Run only after the protected backup, migrated client traffic, and decrypted webhook checks.
-- Caller supplies a verified_legacy_retirement(application_id bigint primary key,
-- key_verified boolean not null, webhook_verified boolean not null) temporary table.
DO $retirement$
BEGIN
    IF EXISTS (
        SELECT 1 FROM verified_legacy_retirement v
        LEFT JOIN public.application_integrations a ON a.id=v.application_id
        WHERE a.id IS NULL OR a.organization_id IS NULL
           OR (v.webhook_verified AND a.slack_webhook_url IS NOT NULL AND a.slack_webhook_ciphertext IS NULL)
           OR (v.key_verified AND a.api_key IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.application_credentials k
                WHERE k.application_id=a.id AND k.organization_id=a.organization_id
                  AND k.key_hash=sha256(convert_to(a.api_key,'UTF8')) AND k.scope='logs:ingest'
                  AND k.revoked_at IS NULL AND (k.expires_at IS NULL OR k.expires_at>now())
           ))
    ) THEN
        RAISE EXCEPTION 'Legacy retirement verification did not match the credential store';
    END IF;
END;
$retirement$;

UPDATE public.application_integrations a
SET api_key=CASE WHEN v.key_verified THEN NULL ELSE a.api_key END,
    slack_webhook_url=CASE WHEN v.webhook_verified THEN NULL ELSE a.slack_webhook_url END
FROM verified_legacy_retirement v
WHERE a.id=v.application_id
  AND ((v.key_verified AND a.api_key IS NOT NULL) OR (v.webhook_verified AND a.slack_webhook_url IS NOT NULL));

