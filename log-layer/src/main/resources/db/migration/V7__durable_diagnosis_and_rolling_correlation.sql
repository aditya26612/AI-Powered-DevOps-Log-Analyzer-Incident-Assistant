ALTER TABLE public.logs ADD COLUMN trace_parent VARCHAR(55), ADD COLUMN trace_origin VARCHAR(24),
    ADD CONSTRAINT logs_trace_context CHECK (
        (trace_parent IS NULL AND trace_origin IS NULL) OR
        (trace_parent IS NOT NULL AND trace_origin IN ('COLLECTOR_REPORTED','INBOUND_REPORTED','LOCAL_ROOT_REPORTED','EXPLICIT')
         AND trace_origin IS NOT NULL AND trace_parent ~ '^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$'
         AND substring(trace_parent,4,32) <> repeat('0',32) AND substring(trace_parent,37,16) <> repeat('0',16))
    );

CREATE INDEX incidents_rolling_match
    ON public.incidents (organization_id, correlation_key, first_event_at, last_event_at)
    WHERE rule_version=2 AND status<>'RESOLVED';
ALTER TABLE public.incidents ADD CONSTRAINT incidents_diagnosis_owner
    UNIQUE (organization_id,id,diagnosis_log_id);

CREATE TABLE public.diagnosis_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    incident_id UUID,
    log_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','RETRY_WAIT','SUCCEEDED','FAILED_TERMINAL')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count>=0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts BETWEEN 1 AND 24),
    manual_retries INTEGER NOT NULL DEFAULT 0 CHECK (manual_retries BETWEEN 0 AND 3),
    requested_by BIGINT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    worker_id UUID,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    last_heartbeat TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_category VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id,id),
    UNIQUE (organization_id,log_id),
    UNIQUE (organization_id,incident_id),
    FOREIGN KEY (organization_id,log_id) REFERENCES public.logs(organization_id,id),
    FOREIGN KEY (organization_id,incident_id,log_id)
        REFERENCES public.incidents(organization_id,id,diagnosis_log_id),
    CHECK ((status='RUNNING' AND worker_id IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status<>'RUNNING' AND worker_id IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CHECK ((status IN ('SUCCEEDED','FAILED_TERMINAL'))=(completed_at IS NOT NULL))
);

CREATE INDEX diagnosis_jobs_due ON public.diagnosis_jobs(organization_id,next_attempt_at,id)
    WHERE status IN ('PENDING','RETRY_WAIT');
CREATE INDEX diagnosis_jobs_expired ON public.diagnosis_jobs(organization_id,lease_until,id)
    WHERE status='RUNNING';

CREATE TABLE public.diagnosis_attempts (
    organization_id UUID NOT NULL,
    job_id UUID NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt>0),
    worker_id UUID NOT NULL,
    requested_by BIGINT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(20) CHECK (outcome IN ('SUCCEEDED','RETRY_WAIT','FAILED_TERMINAL','LEASE_EXPIRED')),
    failure_category VARCHAR(32),
    next_attempt_at TIMESTAMPTZ,
    PRIMARY KEY (organization_id,job_id,attempt),
    FOREIGN KEY (organization_id,job_id) REFERENCES public.diagnosis_jobs(organization_id,id),
    CHECK ((outcome IS NULL)=(finished_at IS NULL))
);

GRANT SELECT, INSERT, UPDATE ON public.diagnosis_jobs, public.diagnosis_attempts TO devinsight_log_tenant;
ALTER TABLE public.diagnosis_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnosis_jobs FORCE ROW LEVEL SECURITY;
ALTER TABLE public.diagnosis_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnosis_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY diagnosis_job_tenant ON public.diagnosis_jobs TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id',true))
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id',true));
CREATE POLICY diagnosis_attempt_tenant ON public.diagnosis_attempts TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id',true))
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id',true));
