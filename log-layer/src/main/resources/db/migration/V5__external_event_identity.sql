ALTER TABLE public.logs
    ADD COLUMN external_event_id UUID,
    ADD COLUMN external_event_digest BYTEA,
    ADD COLUMN external_metadata JSONB,
    ADD CONSTRAINT logs_external_event_fields CHECK (
        (external_event_id IS NULL AND external_event_digest IS NULL AND external_metadata IS NULL)
        OR (external_event_id IS NOT NULL AND external_event_digest IS NOT NULL
            AND octet_length(external_event_digest) = 32
            AND organization_id IS NOT NULL AND application_id IS NOT NULL)
    );

CREATE UNIQUE INDEX logs_external_event_identity
    ON public.logs (organization_id, application_id, external_event_id)
    WHERE external_event_id IS NOT NULL;
