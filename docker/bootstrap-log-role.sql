-- Run as a database administrator after Log Layer Flyway V4 or later.
\set ON_ERROR_STOP on
\getenv log_password LOG_LAYER_DB_PASSWORD
BEGIN;

DO $guard$
BEGIN
    IF current_database() <> 'log_layer_db' OR NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'devinsight_log_tenant'
            AND NOT (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb OR rolcanlogin)
    ) THEN
        RAISE EXCEPTION 'Role provisioning requires the migrated Log Layer database';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'devinsight_log_app'
            AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb OR rolreplication
                 OR NOT rolcanlogin OR NOT rolinherit)
    ) THEN
        RAISE EXCEPTION 'Unexpected privileges on the existing Log Layer login';
    END IF;
END;
$guard$;

SELECT format('CREATE ROLE devinsight_log_app LOGIN INHERIT NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB NOREPLICATION PASSWORD %L', :'log_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'devinsight_log_app')
\gexec

GRANT devinsight_log_tenant TO devinsight_log_app;
COMMIT;
