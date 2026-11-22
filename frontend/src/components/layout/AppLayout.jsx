import { useCallback, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { listApplications, listOrganizations, selectionKey } from "../../api/integrationApi";
import { WorkspaceContext } from "../../context/workspace";
import useResource from "../../hooks/useResource";
import Sidebar from "./Sidebar";
import Topbar from "./Topbar";

const AppLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [selected, setSelected] = useState(() => localStorage.getItem(selectionKey) || "");
  const [created, setCreated] = useState(null);
  const organizations = useResource("organizations", listOrganizations, 60000);
  const items = organizations.data ?? [];
  const available = created && !items.some(({ id }) => id === created.id) ? [...items, created] : items;
  const organization = available.find(({ id }) => id === selected) ?? null;
  const orgId = organization?.id;
  const loadApplications = useCallback((signal) => listApplications(orgId, signal), [orgId]);
  const applicationList = useResource(orgId, loadApplications);
  const integrations = location.pathname === "/integrations";
  const denied = organization?.role === "VIEWER" && ["/analyzer", "/logs/ingest"].includes(location.pathname);

  function select(id) {
    if (id) localStorage.setItem(selectionKey, id);
    else localStorage.removeItem(selectionKey);
    setSelected(id);
    if (id !== selected && (location.search || /\/logs\/[^/]+\/analyze/.test(location.pathname))) {
      navigate(location.pathname.endsWith("/analyze") ? "/logs" : location.pathname, { replace: true });
    }
  }

  function addOrganization(org) {
    setCreated(org);
    select(org.id);
    organizations.refresh();
  }

  const workspace = { organization, organizations: available, select, addOrganization,
    applications: applicationList.data ?? [], applicationError: applicationList.error,
    refreshApplications: applicationList.refresh };
  return (
    <WorkspaceContext.Provider value={workspace}><div className="app-layout">
      <a className="skip-link" href="#workspace-main">Skip to content</a>
      <Sidebar />
      <div className="app-main">
        <Topbar error={organizations.error} />
        <main id="workspace-main" className="app-content" tabIndex={-1}>
          {location.state?.successMessage && <p className="workspace-flash" role="status">{location.state.successMessage}</p>}
          {denied ? <p className="work-panel" role="status">Your organization role cannot submit or analyze logs.</p>
            : organization || integrations ? <Outlet key={orgId || "onboarding"} />
            : organizations.error ? <p role="alert">{organizations.error} <button className="work-button" onClick={organizations.refresh}>Retry</button></p>
              : organizations.data ? <div className="work-panel workspace-empty">
                <h2>Choose an organization</h2>
                <p>Select an organization above or create one in Applications.</p>
                <Link className="work-button" to="/integrations">Open applications</Link>
              </div> : <p role="status">Loading organizations…</p>}
        </main>
      </div>
    </div></WorkspaceContext.Provider>
  );
};

export default AppLayout;
