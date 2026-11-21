-- Run in one transaction after V1–V4 and a reviewed mapping export.
-- The caller supplies these session-local tables on the same connection:
-- verified_legacy_users(user_id bigint, email text, organization_id uuid)
-- verified_legacy_apps(application_id bigint, user_id bigint)
-- verified_legacy_logs(log_id bigint, user_id bigint)
-- legacy_webhook_ciphertexts(application_id bigint, ciphertext text)
DO $mapping$
BEGIN
    IF EXISTS (
        SELECT 1 FROM verified_legacy_apps m
        JOIN verified_legacy_users u USING (user_id)
        LEFT JOIN public.application_integrations a ON a.id=m.application_id
        WHERE a.id IS NULL OR a.user_email<>u.email
           OR (a.organization_id IS NOT NULL AND a.organization_id<>u.organization_id)
    ) OR EXISTS (
        SELECT 1 FROM verified_legacy_logs m
        JOIN verified_legacy_users u USING (user_id)
        LEFT JOIN public.logs l ON l.id=m.log_id
        WHERE l.id IS NULL OR l.user_email<>u.email
           OR lower(trim(l.user_email))='system@devinsight.io'
           OR (l.organization_id IS NOT NULL AND l.organization_id<>u.organization_id)
    ) THEN
        RAISE EXCEPTION 'Legacy ownership mapping does not match preserved records';
    END IF;
END;
$mapping$;

INSERT INTO public.organizations(id,name,slug,personal_user_id)
SELECT organization_id, 'Personal workspace', 'personal-' || user_id, user_id
FROM verified_legacy_users
ON CONFLICT (personal_user_id) DO NOTHING;

DO $personal$
BEGIN
    IF EXISTS (
        SELECT 1 FROM verified_legacy_users u LEFT JOIN public.organizations o
          ON o.id=u.organization_id AND o.personal_user_id=u.user_id AND o.deactivated_at IS NULL
        WHERE o.id IS NULL
    ) OR EXISTS (
        SELECT 1 FROM public.organization_members m JOIN verified_legacy_users u USING (organization_id)
        WHERE m.active AND m.user_id<>u.user_id
    ) THEN
        RAISE EXCEPTION 'Existing personal organization requires ownership review';
    END IF;
END;
$personal$;

INSERT INTO public.organization_members(organization_id,user_id,role)
SELECT organization_id,user_id,'OWNER' FROM verified_legacy_users
ON CONFLICT (organization_id,user_id) DO NOTHING;

DO $owner$
BEGIN
    IF EXISTS (
        SELECT 1 FROM verified_legacy_users u LEFT JOIN public.organization_members m
        ON m.organization_id=u.organization_id AND m.user_id=u.user_id AND m.active AND m.role='OWNER'
        WHERE m.user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Expected personal owner is missing';
    END IF;
END;
$owner$;

UPDATE public.application_integrations a SET organization_id=u.organization_id
FROM verified_legacy_apps m JOIN verified_legacy_users u USING (user_id)
WHERE a.id=m.application_id AND a.organization_id IS NULL;

UPDATE public.logs l SET organization_id=u.organization_id
FROM verified_legacy_logs m JOIN verified_legacy_users u USING (user_id)
WHERE l.id=m.log_id AND l.organization_id IS NULL;

-- The legacy generator used random v4 UUIDs. Transition eligible keys with an explicit expiry.
INSERT INTO public.application_credentials
    (id,organization_id,application_id,name,key_hash,scope,expires_at)
SELECT gen_random_uuid(),a.organization_id,a.id,'Legacy transition',
       sha256(convert_to(a.api_key,'UTF8')),'logs:ingest',now() + interval '7 days'
FROM public.application_integrations a JOIN verified_legacy_apps m ON a.id=m.application_id
WHERE a.api_key ~ '^dvi_[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$'
ON CONFLICT (key_hash) DO NOTHING;

UPDATE public.application_integrations a SET slack_webhook_ciphertext=w.ciphertext
FROM legacy_webhook_ciphertexts w JOIN verified_legacy_apps m USING (application_id)
WHERE a.id=w.application_id AND a.slack_webhook_ciphertext IS NULL;

