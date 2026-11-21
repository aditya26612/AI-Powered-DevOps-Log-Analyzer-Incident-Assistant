-- Apply after the additive tenant schema. Retain existing rows and credential columns.
DO $legacy_constraint$
DECLARE
    matches integer;
    constraint_name text;
BEGIN
    SELECT count(*), min(c.conname::text)
      INTO matches, constraint_name
      FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid
     WHERE c.conrelid='public.application_integrations'::regclass
       AND c.contype='u' AND a.attname='application_name'
       AND c.conkey = ARRAY[a.attnum];
    IF matches <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one legacy application-name constraint';
    END IF;
    EXECUTE format('ALTER TABLE public.application_integrations DROP CONSTRAINT %I', constraint_name);
END;
$legacy_constraint$;

ALTER TABLE public.application_integrations ALTER COLUMN api_key DROP NOT NULL;
