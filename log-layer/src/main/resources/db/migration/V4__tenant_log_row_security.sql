DO $role$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='devinsight_log_tenant') THEN
        CREATE ROLE devinsight_log_tenant NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname='devinsight_log_tenant'
            AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb OR rolcanlogin)
    ) THEN
        RAISE EXCEPTION 'Unexpected attributes for devinsight_log_tenant';
    END IF;
END;
$role$;

GRANT USAGE ON SCHEMA public TO devinsight_log_tenant;
GRANT SELECT, INSERT, UPDATE ON public.organizations, public.organization_members,
    public.application_integrations, public.application_credentials, public.logs TO devinsight_log_tenant;
GRANT USAGE, SELECT ON SEQUENCE public.application_integrations_id_seq, public.logs_id_seq TO devinsight_log_tenant;

ALTER TABLE public.logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.logs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_log_access ON public.logs TO devinsight_log_tenant
USING (organization_id = nullif(current_setting('devinsight.organization_id', true), '')::uuid)
WITH CHECK (organization_id = nullif(current_setting('devinsight.organization_id', true), '')::uuid);
