import { useState } from "react";
import { X } from "lucide-react";

export default function AuthDialog({
  mode,
  setMode,
  form,
  setForm,
  error,
  loading = false,
  onSubmit,
  onDemoLogin,
  onClose
}) {
  const passwordReady = String(form.password || "").length >= 6;
  return (
    <div className="modal-overlay">
      <form className="auth-dialog" onSubmit={onSubmit}>
        <button type="button" className="modal-close" onClick={onClose} disabled={loading}><X size={18} /></button>
        <img className="auth-logo" src="/bear-doctor-logo.png" alt="熊博士 Agent" />
        <h3>{mode === "login" ? "登录熊博士 Agent" : "创建用户账号"}</h3>
        <p className="auth-tip">登录后即可使用对话问答、文件解读、PPT 生成等能力。</p>
        <div className="auth-switch">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} disabled={loading}>登录</button>
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")} disabled={loading}>注册</button>
        </div>
        <label className="auth-field">
          <span>账号</span>
          <input name="username" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="请输入账号" autoComplete="username" disabled={loading} required />
        </label>
        <label className="auth-field">
          <span>密码</span>
          <input name="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} type="password" placeholder="请输入密码" autoComplete={mode === "login" ? "current-password" : "new-password"} disabled={loading} required />
        </label>
        {mode === "register" && (
          <>
            <label className="auth-field">
              <span>昵称</span>
              <input name="nickname" value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} placeholder="可选" autoComplete="nickname" disabled={loading} />
            </label>
            <label className="auth-field">
              <span>邮箱</span>
              <input name="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="可选" autoComplete="email" disabled={loading} />
            </label>
            <div className={`auth-password-rule ${passwordReady ? "ready" : ""}`}>密码至少 6 位</div>
          </>
        )}
        {error && <div className="auth-error">{error}</div>}
        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? "处理中..." : (mode === "login" ? "登录" : "注册并登录")}
        </button>
        {mode === "login" && (
          <button className="auth-demo" type="button" onClick={onDemoLogin} disabled={loading}>
            快速体验
          </button>
        )}
      </form>
    </div>
  );
}
