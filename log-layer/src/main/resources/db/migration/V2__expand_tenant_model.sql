-- Existing user, integration and log IDs remain authoritative.
CREATE TABLE public.organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    personal_user_id BIGINT UNIQUE CHECK (personal_user_id > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated_at TIMESTAMPTZ
);

CREATE TABLE public.organization_members (
    organization_id UUID NOT NULL REFERENCES public.organizations(id),
    user_id BIGINT NOT NULL CHECK (user_id > 0),
    role VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'DEVOPS', 'VIEWER')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, user_id)
);
CREATE INDEX idx_organization_members_user ON public.organization_members(user_id, organization_id)
    WHERE active;

ALTER TABLE public.application_integrations
    ADD COLUMN organization_id UUID REFERENCES public.organizations(id),
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN slack_webhook_ciphertext TEXT,
    ADD CONSTRAINT application_integrations_organization_id_id_key UNIQUE (organization_id, id),
    ADD CONSTRAINT application_integrations_organization_name_key UNIQUE (organization_id, application_name);

ALTER TABLE public.logs
    ADD COLUMN organization_id UUID REFERENCES public.organizations(id),
    ADD COLUMN application_id BIGINT,
    ADD CONSTRAINT logs_application_tenant_check CHECK (application_id IS NULL OR organization_id IS NOT NULL),
    ADD CONSTRAINT logs_organization_application_fkey
        FOREIGN KEY (organization_id, application_id)
        REFERENCES public.application_integrations(organization_id, id);
CREATE INDEX idx_logs_organization_id ON public.logs(organization_id, id DESC);

CREATE TABLE public.application_credentials (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    application_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    key_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(key_hash) = 32),
    scope VARCHAR(32) NOT NULL CHECK (scope = 'logs:ingest'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT application_credentials_organization_application_fkey
        FOREIGN KEY (organization_id, application_id)
        REFERENCES public.application_integrations(organization_id, id)
);
CREATE INDEX idx_application_credentials_app ON public.application_credentials(organization_id, application_id);
