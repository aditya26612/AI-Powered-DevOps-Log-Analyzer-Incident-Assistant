import { useEffect, useRef, useState } from "react";
import {
  createApplication,
  createCredential,
  createOrganization,
  deactivateApplication,
  listApplications,
  listCredentials,
  listOrganizations,
  revokeCredential,
  rotateCredential,
  selectionKey,
  setSlack,
  updateApplication,
} from "../api/integrationApi";
import "./Integrations.css";

const stacks = ["SPRING_BOOT", "PYTHON", "NODEJS", "GO", "KUBERNETES", "POSTGRESQL", "NGINX", "CUSTOM"];
const environments = ["DEVELOPMENT", "TESTING", "QA", "STAGING", "PRODUCTION"];

function errorMessage(error) {
  return error.response?.data?.message || "The request could not be completed. Check your organization and permissions.";
}

function ApplicationSettings({ organization, app, refresh }) {
  const admin = ["OWNER", "ADMIN"].includes(organization.role);
  const editor = organization.role !== "VIEWER";
  const [keys, setKeys] = useState([]);
  const [secret, setSecret] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [webhook, setWebhook] = useState("");
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
  const [organizations, setOrganizations] = useState([]);
  const [active, setActive] = useState(() => localStorage.getItem(selectionKey) || "");
  const activeOrganization = useRef(active);
  const [applicationList, setApplicationList] = useState(null);
  const [selectedApp, setSelectedApp] = useState("");
  const [orgName, setOrgName] = useState("");
  const [slug, setSlug] = useState("");
  const [appName, setAppName] = useState("");
  const [stack, setStack] = useState(stacks[0]);
  const [environment, setEnvironment] = useState(environments[0]);
  const [error, setError] = useState("");

  const org = organizations.find((item) => item.id === active);
  const applications = applicationList?.organizationId === active ? applicationList.items : [];
  const app = applications.find((item) => item.id === selectedApp);
  const refresh = async () => {
    const items = await listApplications(active);
    if (activeOrganization.current === active) setApplicationList({ organizationId: active, items });
  };

  useEffect(() => {
    let current = true;
    listOrganizations().then(
      (data) => { if (current) setOrganizations(data); },
      (failure) => { if (current) setError(errorMessage(failure)); }
    );
    return () => { current = false; };
  }, []);

  useEffect(() => {
    if (!org) return;
    let current = true;
    listApplications(org.id).then(
      (items) => { if (current) setApplicationList({ organizationId: org.id, items }); },
      (failure) => { if (current) setError(errorMessage(failure)); }
    );
    return () => { current = false; };
  }, [org]);

  const selectOrg = (id) => {
    activeOrganization.current = id;
    if (id) localStorage.setItem(selectionKey, id);
    else localStorage.removeItem(selectionKey);
    setSelectedApp("");
    setApplicationList(null);
    setActive(id);
  };

  const handleOrganization = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const created = await createOrganization({ name: orgName, slug });
      setOrganizations((prior) => [...prior, created]);
      selectOrg(created.id);
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
      await refresh();
      setSelectedApp(created.id);
    } catch (failure) {
      setError(errorMessage(failure));
    }
  };

  return (
    <main className="integrations-page tenant-onboarding">
      <h1>Organizations and applications</h1>
      {error && <p role="alert">{error}</p>}
      <section className="tenant-panel">
        <label>Active organization <select value={active} onChange={(event) => selectOrg(event.target.value)}>
          <option value="">Select an organization</option>
          {organizations.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.role}</option>)}
        </select></label>
        <form onSubmit={handleOrganization}>
          <label>Name <input required maxLength={120} value={orgName} onChange={(event) => setOrgName(event.target.value)} /></label>
          <label>Slug <input required maxLength={80} pattern="[a-z0-9]+[a-z0-9\-]*" value={slug}
            onChange={(event) => setSlug(event.target.value)} /></label>
          <button type="submit">Create organization</button>
        </form>
      </section>
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
        <p>Send log requests to <code>POST /api/v1/logs</code> with the application credential in <code>X-API-Key</code>.</p>
      </section>}
      {org && app && <ApplicationSettings key={org.id + "/" + app.id} organization={org} app={app} refresh={refresh} />}
    </main>
  );
}
