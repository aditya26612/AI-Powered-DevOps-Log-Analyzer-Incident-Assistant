import { Link, Outlet, useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";
import Topbar from "./Topbar";

const AppLayout = () => {
  const { pathname } = useLocation();
  const needsOrganization = !["/integrations", "/incidents"].includes(pathname)
    && !localStorage.getItem("devinsight.activeOrganization");
  return (
    <div className="app-layout">
      <Sidebar />

      <div className="app-main">
        <Topbar />

        <main className="app-content">
          {needsOrganization ? (
            <p>Select an active organization on the <Link to="/integrations">Integrations</Link> page.</p>
          ) : <Outlet />}
        </main>
      </div>
    </div>
  );
};

export default AppLayout;
