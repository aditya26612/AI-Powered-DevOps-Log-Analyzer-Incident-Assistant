CREATE TABLE public.notification_destination_gate (
    destination_digest BYTEA PRIMARY KEY CHECK (octet_length(destination_digest)=32),
    lease_token UUID,
    available_at TIMESTAMPTZ NOT NULL
);

REVOKE ALL ON public.notification_destination_gate FROM PUBLIC, devinsight_log_tenant;

CREATE FUNCTION public.reserve_notification_destination(
    digest BYTEA, token UUID, lease_ms BIGINT
) RETURNS BOOLEAN
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog
AS $$
    WITH reserved AS (
        INSERT INTO public.notification_destination_gate AS gate
            (destination_digest,lease_token,available_at)
        SELECT digest,token,clock_timestamp()+lease_ms*interval '1 millisecond'
        WHERE octet_length(digest)=32 AND token IS NOT NULL AND lease_ms BETWEEN 1000 AND 121000
        ON CONFLICT (destination_digest) DO UPDATE
            SET lease_token=excluded.lease_token,available_at=excluded.available_at
            WHERE gate.available_at<=clock_timestamp()
        RETURNING destination_digest
    )
    SELECT EXISTS (SELECT 1 FROM reserved);
$$;

CREATE FUNCTION public.update_notification_destination(
    digest BYTEA, token UUID, delay_ms BIGINT, release BOOLEAN
) RETURNS BOOLEAN
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog
AS $$
    WITH updated AS (
        UPDATE public.notification_destination_gate
        SET available_at=clock_timestamp()+delay_ms*interval '1 millisecond',
            lease_token=CASE WHEN release THEN NULL ELSE token END
        WHERE destination_digest=digest AND lease_token=token AND available_at>clock_timestamp()
          AND ((release AND delay_ms BETWEEN 0 AND 31536000000)
               OR (NOT release AND delay_ms BETWEEN 1000 AND 121000))
        RETURNING destination_digest
    )
    SELECT EXISTS (SELECT 1 FROM updated);
$$;

REVOKE ALL ON FUNCTION public.reserve_notification_destination(BYTEA,UUID,BIGINT),
    public.update_notification_destination(BYTEA,UUID,BIGINT,BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.reserve_notification_destination(BYTEA,UUID,BIGINT),
    public.update_notification_destination(BYTEA,UUID,BIGINT,BOOLEAN) TO devinsight_log_tenant;
