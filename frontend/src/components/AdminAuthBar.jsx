import { useState } from "react";
import { clearAdminAuth, getAdminAuth, saveAdminAuth } from "../services/api";

export default function AdminAuthBar({ onSaved }) {
  const saved = getAdminAuth();
  const [username, setUsername] = useState(saved?.username || "");
  const [password, setPassword] = useState(saved?.password || "");

  const handleSave = () => {
    if (!username.trim() || !password) {
      alert("请输入运营账号和密码");
      return;
    }
    saveAdminAuth(username.trim(), password);
    onSaved?.();
  };

  const handleClear = () => {
    clearAdminAuth();
    setUsername("");
    setPassword("");
    onSaved?.();
  };

  return (
    <div className="auth-bar">
      <div className="auth-copy">
        <strong>后台权限</strong>
        <span>知识库、评测、订单和模板消息接口需要运营或管理员账号。</span>
      </div>
      <div className="auth-form">
        <input
          aria-label="运营账号"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          placeholder="账号"
        />
        <input
          aria-label="运营密码"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="密码"
        />
        <button type="button" className="admin-btn primary" onClick={handleSave}>保存</button>
        <button type="button" className="admin-btn outline" onClick={handleClear}>清除</button>
      </div>
    </div>
  );
}
