# Grafana Loki log sources

DevInsight can read an exact Loki stream label for each application. A background Log Layer worker queries Loki, saves events through the shared deduplicating log writer, and reports sync progress in Applications. Imported logs go through normal scoring and incident processing.

## Configure the Loki connection

The Log Layer operator binds approved Loki URLs to one DevInsight organization. Its owner chooses the active endpoint in **Applications → Organization Loki connection**. Each application has its own stream selector. Use a Loki tenant or a read-only proxy scoped to the organization's logs.

Set these variables in the private environment passed to development Compose or in the Log Layer service environment.

| Variable | Value |
| --- | --- |
| `DEVINSIGHT_LOKI_URL` | Optional approved default Loki origin. An existing default stays active until the owner saves a different approved origin. |
| `DEVINSIGHT_LOKI_ALLOWED_URLS` | Optional comma-separated additional origins that the owner may save. With only this setting, the owner must select an endpoint before syncing. |
| `DEVINSIGHT_LOKI_ORGANIZATION` | DevInsight organization UUID from the authenticated `GET /api/v1/organizations` response |
| `DEVINSIGHT_LOKI_TENANT` | Optional Loki tenant identifier sent as `X-Scope-OrgID` |
| `DEVINSIGHT_LOKI_AUTHORIZATION` | Optional complete read-only Authorization header value, such as a Basic or Bearer credential. Credentialed connections require HTTPS. |

Recreate Log Layer after changing operator configuration. Changes the owner saves in the UI take effect without a restart. Enter a base URL such as `http://loki:3100`. Operator-provided Authorization and tenant headers are sent only to `DEVINSIGHT_LOKI_URL`. Additional origins need to expose that organization's logs through their own network or proxy configuration. Keep credentials in private operator configuration. Switching origins in the UI starts a fresh five-minute window and retains existing logs, selectors, and counters. An earlier in-flight sync cannot commit against the new endpoint.

Apply the Log Layer Flyway migrations through V11 before running the new Log Layer. For local verification, use a disposable database. Follow the database provisioning steps in the [setup guide](SETUP_GUIDE.md).

## Test locally with real Loki

1. Start a disposable development Compose stack. Create an organization and register a Development application named `pull-demo`. Leave Slack delivery disabled.
2. Start Loki on the same Docker network as Log Layer. Replace `<project>` below with the isolated Compose project name. This example exposes the Loki test port only on loopback.

   ```powershell
   docker run -d --name devinsight-loki-demo --network <project>_default --network-alias loki -p 127.0.0.1:19131:3100 grafana/loki:3.5.0
   ```

3. Set the organization UUID and `DEVINSIGHT_LOKI_ALLOWED_URLS=http://loki:3100` in the isolated stack's private environment file. Build and recreate Log Layer and the frontend from your current checkout using the same Compose file and project.
4. As owner, open **Applications → Organization Loki connection**, enter `http://loki:3100`, and click **Save Loki endpoint**. Then select **pull-demo → Grafana Loki log source**. Set **Stream label** to `app`, **Label value** to `pull-demo`, and keep **Automatic sync** enabled. Save the source.
5. After Loki's `/ready` returns HTTP 200, send a synthetic log through Loki.

   ```powershell
   $timestamp = [string][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + '000000'
   $body = @{ streams = @(@{
       stream = @{ app = 'pull-demo'; level = 'error' }
       values = ,@($timestamp, 'Synthetic checkout database connection timed out')
   }) } | ConvertTo-Json -Depth 8 -Compress
   Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $body `
       -Uri 'http://127.0.0.1:19131/loki/api/v1/push'
   ```

6. Wait for the source to show **Synced** and its import count to increase. Confirm the log appears in Log Explorer. Click **Replay last import** and check that the duplicate count increases while the import count and log IDs stay the same.

## Upgrade an existing local Compose stack

Reuse the existing Compose project, private env file, and overrides. Before applying V11, create a custom-format `pg_dump` of `log_layer_db`, verify the archive with `pg_restore -l`, and copy it outside the checkout. Run the existing project's `migrate-logs migrate` service, confirm Flyway reports V11, and rebuild and recreate only `log-layer` and `frontend`. Keep the PostgreSQL volume in place. [Container runbook](../system-architecture/CONTAINER_DEPLOYMENT.md#upgrade-an-existing-compose-stack-for-loki-v11)

The owner's URL must be reachable from Log Layer. In Docker, enter the Loki service name and its internal port, such as `http://loki:3100`. A host-side push may use a separate loopback port. To verify an endpoint save, reload after saving or inspect the persisted organization connection. A prefilled URL can come from the operator's default.

The local `pull-demo` upgrade verified V11, the saved endpoint, and replay of log ID `9`. The import count stayed at three and the duplicate count rose from one to two, with one matching row remaining. [V11 verification](VERIFICATION.md#local-v11-loki-verification)

## Checkpoints and limits

- Include a Loki level label or a structured JSON level to preserve severity. Lines without a recognized level are imported as INFO.
- A new selector looks back five minutes. Later syncs use the saved PostgreSQL checkpoint. Loki queries end 15 seconds behind the current time to allow for delivery delay.
- Sync requests use Loki's [range query API](https://grafana.com/docs/loki/latest/reference/loki-http-api/#query-logs-within-a-range-of-time). An exact label match limits the stream. A full result causes the worker to narrow the time range. If it cannot read the window safely, it shows a retry error and retains the checkpoint. Use a narrower selector for dense streams.
- Duplicates are identified by the Loki origin and tenant, application, stream labels, nanosecond timestamp, and original line text. Identical records at the same timestamp in the same stream are treated as one. Replay starts from the most recent window that added records and proceeds to the current position again.
- The safety delay covers ordinary delivery lag. Logs that arrive in Loki after their timestamp window has closed require replay of a recent import or a separate historical backfill. The connector does not provide guaranteed historical backfill.
