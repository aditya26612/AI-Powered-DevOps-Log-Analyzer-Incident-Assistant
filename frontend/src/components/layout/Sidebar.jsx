import { NavLink } from "react-router-dom";
import { Activity, Files, Layers, LayoutDashboard, Siren, Terminal, Upload } from "lucide-react";
import { useWorkspace } from "../../context/workspace";

const links = [
  ["/dashboard", "Overview", LayoutDashboard],
  ["/logs", "Logs", Files],
  ["/incidents", "Incidents", Siren],
  ["/analyzer", "AI analysis", Terminal],
  ["/integrations", "Applications", Layers],
  ["/logs/ingest", "Ingest log", Upload],
];

export default function Sidebar() {
  const { organization } = useWorkspace();
  const visible = organization?.role === "VIEWER"
    ? links.filter(([path]) => !["/analyzer", "/logs/ingest"].includes(path)) : links;
  return <aside className="app-sidebar">
    <NavLink to="/dashboard" className="sidebar-brand">
      <span className="brand-icon"><Activity size={21} aria-hidden="true" /></span>
      <span className="brand-name">DevInsight</span>
    </NavLink>
    <nav className="sidebar-nav" aria-label="Workspace">
      {visible.map(([path,label,Icon]) => <NavLink key={path} to={path} aria-label={label}
        end={path === "/logs"}
        className={({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`}>
        <Icon size={18} aria-hidden="true" /><span>{label}</span>
      </NavLink>)}
    </nav>
  </aside>;
}
