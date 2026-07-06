import { useState } from "react";
import { ChevronRight, LogOut, Moon, Sun } from "lucide-react";
import { ADMIN_MENU_GROUPS } from "../adminNavigation";
import { clearAdminAuth, getAdminAuth, saveAdminAuth } from "../services/api";

export default function AdminSidebar({ current, onSelect, theme, onToggleTheme, onAuthChanged }) {
  const saved = getAdminAuth();
  const [username, setUsername] = useState(saved?.username || "");
  const [password, setPassword] = useState(saved?.password || "");
  const [openGroups, setOpenGroups] = useState(() => new Set(ADMIN_MENU_GROUPS.map((g) => g.name)));

  const toggleGroup = (name) => {
    setOpenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(name)) {
        next.delete(name);
      } else {
        next.add(name);
      }
      return next;
    });
  };

  const handleSaveAuth = () => {
    if (!username.trim() || !password) {
      alert("请输入运营账号和密码");
      return;
    }
    saveAdminAuth(username.trim(), password);
    onAuthChanged?.();
  };

  const handleClearAuth = () => {
    clearAdminAuth();
    setUsername("");
    setPassword("");
    onAuthChanged?.();
  };

  const loggedIn = Boolean(saved?.username);

  return (
    <aside className="admin-sidebar">
      <div className="admin-sidebar-brand">
        <span>管理后台</span>
        <button className="admin-icon-btn" type="button" onClick={onToggleTheme} title="切换主题" aria-label="切换主题">
          {theme === "dark" ? <Sun size={16} /> : <Moon size={16} />}
        </button>
      </div>

      <div className="admin-sidebar-nav">
        <button
          type="button"
          className={`admin-nav-item ${current === "overview" ? "active" : ""}`}
          onClick={() => onSelect("overview")}
        >
          <span className="admin-nav-dot" />
          <span>总览</span>
        </button>
        {ADMIN_MENU_GROUPS.map((group) => (
          <div key={group.name} className="admin-nav-group">
            <button type="button" className="admin-nav-group-title" onClick={() => toggleGroup(group.name)}>
              <ChevronRight size={14} className={`admin-caret ${openGroups.has(group.name) ? "open" : ""}`} />
              <span>{group.label}</span>
            </button>
            {openGroups.has(group.name) && (
              <div className="admin-nav-items">
                {group.items.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    className={`admin-nav-item ${current === item.key ? "active" : ""}`}
                    onClick={() => onSelect(item.key)}
                  >
                    <span className="admin-nav-dot" />
                    <span>{item.label}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="admin-sidebar-footer">
        {loggedIn ? (
          <div className="admin-user-card">
            <div className="admin-avatar">{(saved.username || "?").slice(0, 1).toUpperCase()}</div>
            <div className="admin-user-name">{saved.username}</div>
            <button className="admin-icon-btn" type="button" onClick={handleClearAuth} title="退出登录" aria-label="退出登录">
              <LogOut size={16} />
            </button>
          </div>
        ) : (
          <div className="admin-login-card">
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="运营账号" />
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="密码" />
            <button type="button" className="admin-btn primary small" onClick={handleSaveAuth}>登录</button>
          </div>
        )}
      </div>
    </aside>
  );
}
