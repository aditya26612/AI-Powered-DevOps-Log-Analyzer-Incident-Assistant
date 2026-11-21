ALTER TABLE public.logs ADD COLUMN correlation_origin VARCHAR(24)
    CHECK (correlation_origin IN ('COLLECTOR_SUPPLIED', 'LEGACY_SUPPLIED', 'SERVER_GENERATED'));
ALTER TABLE public.logs ADD CONSTRAINT logs_tenant_identity UNIQUE (organization_id, id);

CREATE TABLE public.incidents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES public.organizations(id),
    environment VARCHAR(30) NOT NULL,
    correlation_key BYTEA NOT NULL CHECK (octet_length(correlation_key) = 32),
    correlation_rule VARCHAR(24) NOT NULL CHECK (correlation_rule IN ('SUPPLIED_TRACE', 'APPLICATION_SIGNATURE')),
    rule_version SMALLINT NOT NULL CHECK (rule_version > 0),
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL CHECK (window_end > window_start),
    first_event_at TIMESTAMP NOT NULL,
    last_event_at TIMESTAMP NOT NULL CHECK (last_event_at >= first_event_at),
    severity SMALLINT NOT NULL CHECK (severity BETWEEN 1 AND 3),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    event_count BIGINT NOT NULL CHECK (event_count > 0),
    representative_log_id BIGINT NOT NULL,
    diagnosis_log_id BIGINT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMPTZ,
    resolved_by BIGINT,
    resolved_at TIMESTAMPTZ,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, representative_log_id) REFERENCES public.logs (organization_id, id),
    FOREIGN KEY (organization_id, diagnosis_log_id) REFERENCES public.logs (organization_id, id),
    CHECK (first_event_at >= window_start AND last_event_at < window_end),
    CHECK ((acknowledged_by IS NULL) = (acknowledged_at IS NULL)),
    CHECK ((resolved_by IS NULL) = (resolved_at IS NULL)),
    CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE UNIQUE INDEX incidents_active_group
    ON public.incidents (organization_id, correlation_key, window_start) WHERE status <> 'RESOLVED';
CREATE INDEX incidents_tenant_recency ON public.incidents (organization_id, last_event_at DESC, id DESC);

CREATE TABLE public.incident_memberships (
    organization_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    log_id BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL CHECK (reason IN ('INITIAL_SEED', 'SAME_SUPPLIED_TRACE', 'SAME_APPLICATION_SIGNATURE', 'TRACE_CONTEXT')),
    seed BOOLEAN NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, log_id),
    FOREIGN KEY (organization_id, incident_id) REFERENCES public.incidents (organization_id, id),
    FOREIGN KEY (organization_id, log_id) REFERENCES public.logs (organization_id, id),
    CHECK (seed = (reason <> 'TRACE_CONTEXT'))
);

CREATE INDEX incident_memberships_listing ON public.incident_memberships (organization_id, incident_id, log_id);
CREATE INDEX incident_memberships_context ON public.incident_memberships (organization_id, incident_id) WHERE NOT seed;

GRANT SELECT, INSERT, UPDATE ON public.incidents TO devinsight_log_tenant;
GRANT SELECT, INSERT ON public.incident_memberships TO devinsight_log_tenant;
ALTER TABLE public.incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.incidents FORCE ROW LEVEL SECURITY;
ALTER TABLE public.incident_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.incident_memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY incidents_select ON public.incidents FOR SELECT TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id', true));
CREATE POLICY incidents_insert ON public.incidents FOR INSERT TO devinsight_log_tenant
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id', true));
CREATE POLICY incidents_update ON public.incidents FOR UPDATE TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id', true))
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id', true));
CREATE POLICY incident_memberships_select ON public.incident_memberships FOR SELECT TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id', true));
CREATE POLICY incident_memberships_insert ON public.incident_memberships FOR INSERT TO devinsight_log_tenant
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id', true));
