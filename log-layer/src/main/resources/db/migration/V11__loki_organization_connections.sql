CREATE TABLE public.loki_organization_connections (
    organization_id UUID PRIMARY KEY REFERENCES public.organizations (id),
    origin VARCHAR(512) NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON public.loki_organization_connections TO devinsight_log_tenant;
ALTER TABLE public.loki_organization_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loki_organization_connections FORCE ROW LEVEL SECURITY;
CREATE POLICY loki_connection_tenant ON public.loki_organization_connections TO devinsight_log_tenant
    USING (organization_id = nullif(current_setting('devinsight.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = nullif(current_setting('devinsight.organization_id', true), '')::uuid);
