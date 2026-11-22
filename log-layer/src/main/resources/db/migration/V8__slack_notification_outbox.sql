ALTER TABLE public.application_integrations
    ADD COLUMN slack_delivery_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN slack_delivery_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN slack_minimum_severity SMALLINT NOT NULL DEFAULT 1 CHECK (slack_minimum_severity BETWEEN 1 AND 3),
    ADD COLUMN slack_not_before TIMESTAMPTZ;

ALTER TABLE public.diagnosis_jobs ADD CONSTRAINT diagnosis_job_source_key UNIQUE (organization_id,id,log_id);
ALTER TABLE public.logs ADD CONSTRAINT notification_source_app_key UNIQUE (organization_id,id,application_id);

CREATE TABLE public.notification_outbox (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    diagnosis_id UUID NOT NULL,
    incident_id UUID,
    log_id BIGINT NOT NULL,
    application_id BIGINT,
    destination_revision BIGINT,
    policy_version SMALLINT NOT NULL DEFAULT 1 CHECK (policy_version>0),
    severity SMALLINT NOT NULL CHECK (severity BETWEEN 1 AND 3),
    status VARCHAR(20) NOT NULL CHECK (status IN
        ('PENDING','SENDING','RETRY_WAIT','DELIVERED','FAILED_TERMINAL','DELIVERY_UNKNOWN','CANCELLED','SKIPPED')),
    failure_category VARCHAR(40),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count>=0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts BETWEEN 1 AND 15),
    manual_retries INTEGER NOT NULL DEFAULT 0 CHECK (manual_retries BETWEEN 0 AND 2),
    requested_by BIGINT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    worker_id UUID,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    dispatch_started_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id,id),
    UNIQUE (organization_id,diagnosis_id,policy_version),
    UNIQUE (organization_id,incident_id,policy_version),
    FOREIGN KEY (organization_id,diagnosis_id,log_id) REFERENCES public.diagnosis_jobs(organization_id,id,log_id),
    FOREIGN KEY (organization_id,incident_id,log_id) REFERENCES public.incidents(organization_id,id,diagnosis_log_id),
    FOREIGN KEY (organization_id,log_id,application_id) REFERENCES public.logs(organization_id,id,application_id),
    FOREIGN KEY (organization_id,application_id) REFERENCES public.application_integrations(organization_id,id),
    CHECK (status IN ('SKIPPED','CANCELLED') OR (application_id IS NOT NULL AND destination_revision >= 0
                                                AND destination_revision IS NOT NULL)),
    CHECK ((status='SENDING' AND worker_id IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status<>'SENDING' AND worker_id IS NULL AND lease_token IS NULL AND lease_until IS NULL
                             AND dispatch_started_at IS NULL)),
    CHECK ((status='DELIVERED')=(acknowledged_at IS NOT NULL))
);

CREATE INDEX notification_due ON public.notification_outbox(organization_id,next_attempt_at,id)
    WHERE status IN ('PENDING','RETRY_WAIT');
CREATE INDEX notification_expired ON public.notification_outbox(organization_id,lease_until,id)
    WHERE status='SENDING';
CREATE INDEX notification_active_destination ON public.notification_outbox(organization_id,application_id)
    WHERE status='SENDING';
CREATE INDEX notification_incident_history ON public.notification_outbox(organization_id,incident_id,created_at DESC,id);

CREATE TABLE public.notification_attempts (
    organization_id UUID NOT NULL,
    notification_id UUID NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt>0),
    worker_id UUID NOT NULL,
    requested_by BIGINT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(20) CHECK (outcome IN
        ('RETRY_WAIT','DELIVERED','FAILED_TERMINAL','DELIVERY_UNKNOWN','CANCELLED','ABANDONED')),
    http_status SMALLINT CHECK (http_status BETWEEN 100 AND 599),
    failure_category VARCHAR(40),
    next_attempt_at TIMESTAMPTZ,
    PRIMARY KEY (organization_id,notification_id,attempt),
    FOREIGN KEY (organization_id,notification_id) REFERENCES public.notification_outbox(organization_id,id),
    CHECK ((outcome IS NULL)=(finished_at IS NULL))
);

GRANT SELECT, INSERT, UPDATE ON public.notification_outbox, public.notification_attempts TO devinsight_log_tenant;
ALTER TABLE public.notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_outbox FORCE ROW LEVEL SECURITY;
ALTER TABLE public.notification_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_tenant ON public.notification_outbox TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id',true))
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id',true));
CREATE POLICY notification_attempt_tenant ON public.notification_attempts TO devinsight_log_tenant
    USING (organization_id::text = current_setting('devinsight.organization_id',true))
    WITH CHECK (organization_id::text = current_setting('devinsight.organization_id',true));
