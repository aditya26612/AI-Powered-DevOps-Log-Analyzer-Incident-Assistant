import { Building2, LogOut } from "lucide-react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../../context/auth";
import { useWorkspace } from "../../context/workspace";

const pages = {
  "/dashboard": ["Overview", "Persisted telemetry and incident activity"],
  "/logs": ["Log explorer", "Search and investigate source events"],
  "/incidents": ["Incidents", "Correlation, diagnosis and notification delivery"],
  "/integrations": ["Applications", "Collector onboarding and integration settings"],
  "/analyzer": ["AI analysis", "Inspect a log without adding it to telemetry"],
  "/logs/ingest": ["Ingest log", "Submit a log to the selected organization"],
};

export default function Topbar({ error }) {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const { organization, organizations, select } = useWorkspace();
  const [title, subtitle] = pages[pathname] ?? ["Log investigation", "Source metadata and diagnosis"];
  return <header className="app-topbar">
    <div>
      <h1 className="topbar-title">{title}</h1>
      <p className="topbar-subtitle">{subtitle}</p>
    </div>
    <div className="topbar-right">
      <label className="workspace-org">
        <Building2 size={16} aria-hidden="true" />
        <select aria-label="Organization" value={organization?.id ?? ""} onChange={(event) => select(event.target.value)}>
          <option value="">Select an organization</option>
          {organizations.map(({ id, name, role }) => <option key={id} value={id}>{name} · {role}</option>)}
        </select>
      </label>
      {error && organization && <span role="status" className="organization-warning">Organization list could not refresh.</span>}
      <button type="button" className="logout-button" onClick={logout} title={user?.email} aria-label="Sign out">
        <LogOut size={17} aria-hidden="true" /> <span>Sign out</span>
      </button>
    </div>
  </header>;
}
