CREATE TABLE public.loki_log_sources (
    organization_id UUID NOT NULL,
    application_id BIGINT NOT NULL,
    label_name VARCHAR(128) NOT NULL,
    label_value VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL,
    cursor_ns BIGINT NOT NULL CHECK (cursor_ns >= 0),
    last_import_start_ns BIGINT,
    next_poll_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error TEXT,
    imported_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (organization_id, application_id),
    FOREIGN KEY (organization_id, application_id)
        REFERENCES public.application_integrations (organization_id, id)
);

CREATE INDEX loki_log_sources_due
    ON public.loki_log_sources (organization_id, next_poll_at, application_id) WHERE enabled;

GRANT SELECT, INSERT, UPDATE ON public.loki_log_sources TO devinsight_log_tenant;
ALTER TABLE public.loki_log_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loki_log_sources FORCE ROW LEVEL SECURITY;
CREATE POLICY loki_source_tenant ON public.loki_log_sources TO devinsight_log_tenant
    USING (organization_id = nullif(current_setting('devinsight.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = nullif(current_setting('devinsight.organization_id', true), '')::uuid);
