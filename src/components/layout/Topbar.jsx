import { LogOut, User } from "lucide-react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const Topbar = () => {
  const { user, logout } = useAuth();
  const location = useLocation();

  const getPageInfo = () => {
    if (location.pathname === "/dashboard") {
      return {
        title: "Overview",
        subtitle: "Monitor and analyze your application logs",
      };
    }

    if (location.pathname === "/logs/ingest") {
      return {
        title: "Ingest Log",
        subtitle: "Send application logs for ML and AI analysis",
      };
    }

    if (
      location.pathname.startsWith("/logs/") &&
      location.pathname.endsWith("/analyze")
    ) {
      return {
        title: "Log Investigation",
        subtitle: "Investigate ML detection and AI-powered insights",
      };
    }

    return {
      title: "DevInsight",
      subtitle: "AI-Powered DevOps Analyzer",
    };
  };

  const { title, subtitle } = getPageInfo();

  return (
    <header className="app-topbar">
      <div className="topbar-left">
        <div>
          <h1 className="topbar-title">{title}</h1>
          <p className="topbar-subtitle">{subtitle}</p>
        </div>
      </div>

      <div className="topbar-right">
        <div className="user-info">
          <div className="user-avatar">
            <User size={17} />
          </div>

          <div className="user-details">
            <span className="user-email">
              {user?.email || "User"}
            </span>

            <span className="user-role">
              {user?.role || "USER"}
            </span>
          </div>
        </div>

        <button
          className="logout-button"
          onClick={logout}
          title="Logout"
        >
          <LogOut size={17} />
          <span>Logout</span>
        </button>
      </div>
    </header>
  );
};

export default Topbar;