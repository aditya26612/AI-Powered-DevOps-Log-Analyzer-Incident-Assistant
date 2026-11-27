import { useEffect, useState } from "react";
import {
  createApplication,
  createCredential,
  createOrganization,
  deactivateApplication,
  listCredentials,
  revokeCredential,
  rotateCredential,
  setSlack,
  setSlackPolicy,
  updateApplication,
} from "../api/integrationApi";
import "./Integrations.css";
import LokiLogSource from "../components/LokiLogSource";
import LokiConnection from "../components/LokiConnection";
import { useWorkspace } from "../context/workspace";
import { readError } from "../hooks/useResource";

const stacks = ["SPRING_BOOT", "PYTHON", "NODEJS", "GO", "KUBERNETES", "POSTGRESQL", "NGINX", "CUSTOM"];
const environments = ["DEVELOPMENT", "TESTING", "QA", "STAGING", "PRODUCTION"];

function errorMessage(error) {
  return readError(error);
}

function ApplicationSettings({ organization, app, refresh }) {
  const admin = ["OWNER", "ADMIN"].includes(organization.role);
  const editor = organization.role !== "VIEWER";
  const [keys, setKeys] = useState([]);
  const [secret, setSecret] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [webhook, setWebhook] = useState("");
  const [slackEnabled, setSlackEnabled] = useState(app.slackDeliveryEnabled ?? true);
  const [slackSeverity, setSlackSeverity] = useState(app.slackMinimumSeverity ?? 1);
  const [environment, setEnvironment] = useState(app.environment);
  const [description, setDescription] = useState(app.description || "");

  useEffect(() => {
    if (!admin) return;
    let current = true;
    listCredentials(organization.id, app.id).then(
      (data) => { if (current) setKeys(data); },
      (failure) => { if (current) setError(errorMessage(failure)); }
    );
    return () => { current = false; };
  }, [organization.id, app.id, admin]);

  const perform = async (operation) => {
    setError("");
    try {
      await operation();
    } catch (failure) {
      setError(errorMessage(failure));
    }
  };

  const issue = (event, replaces) => {
    event?.preventDefault();
    if (replaces && !window.confirm("Rotate this credential and revoke its current secret?")) return;
    setSecret("");
    perform(async () => {
      const data = { name: name.trim() || "Ingestion credential" };
      const issued = replaces
        ? await rotateCredential(organization.id, app.id, replaces, data)
        : await createCredential(organization.id, app.id, data);
      setSecret(issued.key);
      setKeys(await listCredentials(organization.id, app.id));
    });
  };

  return (
    <section className="tenant-panel">
      <h2>{app.applicationName}</h2>
      <details>
        <summary>Connect a log collector</summary>
        <p>
          Set <code>DEVINSIGHT_APPLICATION_NAME</code> to <code>{app.applicationName}</code> and
          <code> DEVINSIGHT_ENVIRONMENT</code> to <code>{app.environment}</code>.
          Supply the scoped key as <code>DEVINSIGHT_API_KEY</code> from protected configuration.
        </p>
        <p>
          Set <code>DEVINSIGHT_ENDPOINT</code> to your Gateway URL ending in <code>/api/v1/logs/batch</code>.
          The default local URL is <code>http://127.0.0.1:8080/api/v1/logs/batch</code>.
        </p>
        <ul>
          <li>Java and Spring Boot use the <code>devinsight-logback</code> local Maven module and its Logback appender.</li>
          <li>Python uses <code>DevInsightHandler</code> from <code>devinsight_logger</code> with standard logging.</li>
          <li>Node.js uses the local <code>devinsight-log-collector</code> package, with an adapter for Winston.</li>
        </ul>
        <p>
          For a Kafka-enabled Gateway, set <code>DEVINSIGHT_DELIVERY_MODE=ASYNC_KAFKA</code> and use
          <code> /api/v2/logs/batch</code>. HTTP 202 confirms broker acceptance. Persistence follows asynchronously.
        </p>
        <p>
          Batches allow 10 events and 64 KiB of JSON. Collectors set a stable event ID, timestamp, severity, source,
          and message. For 401, check the credential. For 403, check the application name and environment.
          Check the collector counters to confirm delivery.
        </p>
      </details>
      <LokiLogSource organization={organization.id} application={app.id} admin={admin} />
      {error && <p role="alert">{error}</p>}
      {editor && (
        <form onSubmit={(event) => {
          event.preventDefault();
          perform(async () => {
            await updateApplication(organization.id, app.id, { environment, description });
            await refresh();
          });
        }}>
          <label>Environment <select value={environment} onChange={(event) => setEnvironment(event.target.value)}>
            {environments.map((value) => <option key={value}>{value}</option>)}
          </select></label>
          <label>Description <input maxLength={255} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
          <button type="submit">Save application</button>
        </form>
      )}
      {admin && (
        <>
          <form onSubmit={(event) => issue(event)}>
            <label>Credential name <input maxLength={80} value={name} onChange={(event) => setName(event.target.value)} /></label>
            <button type="submit">Create ingestion key</button>
          </form>
          {secret && (
            <div className="tenant-secret">
              <p>Copy this key now. It cannot be retrieved later.</p>
              <output>{secret}</output>
              <button type="button" onClick={() => perform(() => navigator.clipboard.writeText(secret))}>Copy key</button>
              <button type="button" onClick={() => setSecret("")}>Hide key</button>
            </div>
          )}
          <ul>
            {keys.map((item) => (
              <li key={item.id}>
                <span>{item.name} · {item.id.slice(0, 8)}… · {item.revokedAt ? "Revoked" : item.scope}</span>
                {!item.revokedAt && <>
                  <button type="button" onClick={(event) => issue(event, item.id)}>Rotate</button>
                  <button type="button" onClick={() => {
                    if (window.confirm("Revoke this ingestion credential?")) {
                      setSecret("");
                      perform(async () => {
                        await revokeCredential(organization.id, app.id, item.id);
                        setKeys(await listCredentials(organization.id, app.id));
                      });
                    }
                  }}>Revoke</button>
                </>}
              </li>
            ))}
          </ul>
          <form onSubmit={(event) => {
            event.preventDefault();
            perform(async () => {
              await setSlack(organization.id, app.id, webhook);
              setWebhook("");
              await refresh();
            });
          }}>
            <label>Slack webhook <input type="password" autoComplete="off" maxLength={500}
              placeholder={app.slackConfigured ? "Replace configured webhook" : "Incoming webhook URL"}
              value={webhook} onChange={(event) => setWebhook(event.target.value)} required /></label>
            <button type="submit">Save webhook</button>
            {app.slackConfigured && <button type="button" onClick={() => perform(async () => {
              await setSlack(organization.id, app.id, null);
              await refresh();
            })}>Clear webhook</button>}
          </form>
          <p>{app.slackConfigured ? "A webhook is configured." : "No webhook configured."}
            {" Replacing a webhook invalidates work queued for the previous destination."}</p>
          <form onSubmit={(event) => {
            event.preventDefault();
            perform(async () => {
              await setSlackPolicy(organization.id, app.id, slackEnabled, slackSeverity);
              await refresh();
            });
          }}>
            <label>Slack notifications <input type="checkbox" checked={slackEnabled}
              onChange={(event) => setSlackEnabled(event.target.checked)} /></label>
            <label>Minimum severity <select value={slackSeverity} onChange={(event) => setSlackSeverity(Number(event.target.value))}>
              <option value={1}>Medium</option><option value={2}>High</option><option value={3}>Critical</option>
            </select></label>
            <button type="submit">Save notification policy</button>
          </form>
          <button type="button" onClick={() => {
            if (window.confirm("Deactivate this application? Its historical logs will be preserved.")) {
              perform(async () => {
                await deactivateApplication(organization.id, app.id);
                await refresh();
              });
            }
          }}>Deactivate application</button>
        </>
      )}
    </section>
  );
}

export default function Integrations() {
  const { organization: org, applications, applicationError, addOrganization, refreshApplications } = useWorkspace();
  const active = org?.id;
  const [selectedApp, setSelectedApp] = useState("");
  const [orgName, setOrgName] = useState("");
  const [slug, setSlug] = useState("");
  const [appName, setAppName] = useState("");
  const [stack, setStack] = useState(stacks[0]);
  const [environment, setEnvironment] = useState(environments[0]);
  const [error, setError] = useState("");

  const app = applications.find((item) => item.id === selectedApp);

  const handleOrganization = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const created = await createOrganization({ name: orgName, slug });
      addOrganization(created);
      setOrgName("");
      setSlug("");
    } catch (failure) {
      setError(errorMessage(failure));
    }
  };

  const handleApplication = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const created = await createApplication(active, { applicationName: appName, techStack: stack, environment });
      setAppName("");
      refreshApplications();
      setSelectedApp(created.id);
    } catch (failure) {
      setError(errorMessage(failure));
    }
  };

  return (
    <div className="integrations-page tenant-onboarding">
      {error && <p role="alert">{error}</p>}
      {applicationError && <p role="alert" className="work-alert">{applicationError}</p>}
      <section className="tenant-panel">
        <h2>Create an organization</h2>
        <form onSubmit={handleOrganization}>
          <label>Name <input required maxLength={120} value={orgName} onChange={(event) => setOrgName(event.target.value)} /></label>
          <label>Slug <input required maxLength={80} pattern="[a-z0-9]+[a-z0-9\-]*" value={slug}
            onChange={(event) => setSlug(event.target.value)} /></label>
          <button type="submit">Create organization</button>
        </form>
      </section>
      {org?.role === "OWNER" && <LokiConnection key={org.id} organization={org.id} />}
      {org && <section className="tenant-panel">
        <h2>Applications</h2>
        {org.role !== "VIEWER" && <form onSubmit={handleApplication}>
          <label>Application name <input required maxLength={100} value={appName} onChange={(event) => setAppName(event.target.value)} /></label>
          <label>Technology <select value={stack} onChange={(event) => setStack(event.target.value)}>
            {stacks.map((value) => <option key={value}>{value}</option>)}
          </select></label>
          <label>Environment <select value={environment} onChange={(event) => setEnvironment(event.target.value)}>
            {environments.map((value) => <option key={value}>{value}</option>)}
          </select></label>
          <button type="submit">Register application</button>
        </form>}
        <label>Application <select value={selectedApp} onChange={(event) => setSelectedApp(event.target.value)}>
          <option value="">Select an application</option>
          {applications.map((item) => <option key={item.id} value={item.id}>{item.applicationName}</option>)}
        </select></label>
        <details className="work-details"><summary>Collector onboarding</summary>
          <p>Set the application name and environment in the collector. Provide its credential via <code>DEVINSIGHT_API_KEY</code>.</p>
          <p>Use <code>POST /api/v1/logs/batch</code> for synchronous persistence or <code>POST /api/v2/logs/batch</code> for Kafka acknowledgement when enabled.</p>
        </details>
      </section>}
      {org && app && <ApplicationSettings key={org.id + "/" + app.id} organization={org} app={app} refresh={refreshApplications} />}
    </div>
  );
}
