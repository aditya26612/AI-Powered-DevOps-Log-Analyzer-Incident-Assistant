import { NavLink } from "react-router-dom";
import {
  LayoutDashboard,
  Upload,
  Activity,
  CircleCheck,
} from "lucide-react";

const Sidebar = () => {
  return (
    <aside className="app-sidebar">
      {/* Brand */}
      <div className="sidebar-brand">
        <div className="brand-icon">
          <Activity size={20} />
        </div>

        <div>
          <div className="brand-name">DevInsight</div>
          <div className="brand-subtitle">DevOps Analyzer</div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        <div className="nav-section-title">WORKSPACE</div>

        <NavLink
          to="/dashboard"
          end
          className={({ isActive }) =>
            `sidebar-link ${isActive ? "active" : ""}`
          }
        >
          <LayoutDashboard size={18} />
          <span>Overview</span>
        </NavLink>

        <NavLink
          to="/logs/ingest"
          className={({ isActive }) =>
            `sidebar-link ${isActive ? "active" : ""}`
          }
        >
          <Upload size={18} />
          <span>Ingest Log</span>
        </NavLink>
      </nav>

      {/* System Status */}
      <div className="sidebar-footer">
        <div className="sidebar-status">
          <CircleCheck size={15} />
          <span>System Operational</span>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;