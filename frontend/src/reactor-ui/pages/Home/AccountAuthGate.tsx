import { useState } from "react";
import { motion } from "motion/react";
import { ArrowRight, LogIn } from "lucide-react";

import {
  getUserAuth,
  login,
  logout,
  normalizeApiMessage,
  register,
} from "../../../services/api.js";

type AuthMode = "login" | "register";

type AccountAuthGateProps = {
  onAuthenticated: () => void;
};

const DEMO_AUTH_FORM = {
  username: "demo",
  password: "123456",
  nickname: "演示用户",
  email: "demo@example.com",
};

const EMPTY_AUTH_FORM = {
  username: "",
  password: "",
  nickname: "",
  email: "",
};

function validateAuthForm(
  form: typeof EMPTY_AUTH_FORM,
  mode: AuthMode
): string {
  if (!String(form.username || "").trim()) return "请输入账号";
  if (!String(form.password || "").length) return "请输入密码";
  if (mode === "register" && String(form.password || "").length < 6) {
    return "密码至少 6 位";
  }
  const email = String(form.email || "").trim();
  if (
    mode === "register" &&
    email &&
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    return "邮箱格式不正确";
  }
  return "";
}

export default function AccountAuthGate(props: AccountAuthGateProps) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [form, setForm] = useState(EMPTY_AUTH_FORM);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (
    nextForm = form,
    nextMode: AuthMode = mode
  ) => {
    setError("");
    const validationError = validateAuthForm(nextForm, nextMode);
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    try {
      const payload = {
        username: String(nextForm.username || "").trim(),
        password: String(nextForm.password || ""),
        nickname: String(nextForm.nickname || "").trim(),
        email: String(nextForm.email || "").trim(),
      };
      const res =
        nextMode === "login"
          ? await login(payload.username, payload.password)
          : await register(payload);

      if (res?.code === "0000" && (res.data?.token || getUserAuth()?.token)) {
        props.onAuthenticated();
        return;
      }
      setError(
        normalizeApiMessage(res?.info || res?.message, "登录失败，请重试")
      );
    } catch (nextError) {
      setError(
        normalizeApiMessage((nextError as Error)?.message, "登录失败，请重试")
      );
    } finally {
      setLoading(false);
    }
  };

  const handleDemoLogin = async () => {
    await logout().catch(() => {});
    setMode("login");
    setForm(DEMO_AUTH_FORM);
    await submit(DEMO_AUTH_FORM, "login");
  };

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden">
      <div className="absolute inset-0 bg-[var(--page-gradient)]" />
      <div
        className="pointer-events-none absolute -right-32 top-1/4 h-[500px] w-[500px] rounded-full opacity-60"
        style={{
          background:
            "radial-gradient(circle, oklch(0.7 0.05 260 / 0.06), transparent 70%)",
          filter: "blur(60px)",
        }}
      />
      <motion.div
        className="relative z-10 w-full max-w-[460px] px-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
      >
        <div className="mb-8 text-center">
          <img
            src="/bear-doctor-logo.png"
            alt="熊博士Agent"
            className="mx-auto mb-5 h-16 w-16 rounded-2xl object-cover shadow-[var(--shadow-md)]"
          />
          <h1
            className="mb-3 text-[38px] leading-[1.1] tracking-tight text-[var(--chat-text)] md:text-[44px]"
            style={{ fontFamily: "var(--font-display)" }}
          >
            {mode === "login" ? "登录熊博士 Agent" : "创建账号"}
          </h1>
          <p className="mx-auto max-w-[320px] text-[15px] leading-relaxed text-[var(--chat-text-soft)]">
            登录后即可使用对话、文件理解、深度任务和生图能力。
          </p>
        </div>

        <div className="mb-5 flex rounded-2xl border border-[var(--chat-border)] bg-[var(--chat-surface)]/80 p-1">
          <button
            type="button"
            disabled={loading}
            onClick={() => setMode("login")}
            className={`flex-1 rounded-xl px-4 py-2 text-sm transition ${
              mode === "login"
                ? "bg-[var(--chat-text)] text-white"
                : "text-[var(--chat-text-soft)]"
            }`}
          >
            登录
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={() => setMode("register")}
            className={`flex-1 rounded-xl px-4 py-2 text-sm transition ${
              mode === "register"
                ? "bg-[var(--chat-text)] text-white"
                : "text-[var(--chat-text-soft)]"
            }`}
          >
            注册
          </button>
        </div>

        <form
          className="space-y-4 rounded-[28px] border border-[var(--chat-border)] bg-[var(--chat-surface)]/90 p-6 shadow-[var(--shadow-md)]"
          onSubmit={(event) => {
            event.preventDefault();
            submit().catch(() => {});
          }}
        >
          <label className="block space-y-2">
            <span className="text-sm text-[var(--chat-text-soft)]">账号</span>
            <input
              value={form.username}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  username: event.target.value,
                }))
              }
              placeholder="请输入账号"
              autoComplete="username"
              disabled={loading}
              className="h-12 w-full rounded-2xl border border-[var(--chat-border)] bg-white/70 px-4 text-[15px] outline-none transition focus:border-[var(--chat-border-strong)]"
            />
          </label>

          <label className="block space-y-2">
            <span className="text-sm text-[var(--chat-text-soft)]">密码</span>
            <input
              value={form.password}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  password: event.target.value,
                }))
              }
              type="password"
              placeholder="请输入密码"
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
              disabled={loading}
              className="h-12 w-full rounded-2xl border border-[var(--chat-border)] bg-white/70 px-4 text-[15px] outline-none transition focus:border-[var(--chat-border-strong)]"
            />
          </label>

          {mode === "register" ? (
            <>
              <label className="block space-y-2">
                <span className="text-sm text-[var(--chat-text-soft)]">昵称</span>
                <input
                  value={form.nickname}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      nickname: event.target.value,
                    }))
                  }
                  placeholder="可选"
                  autoComplete="nickname"
                  disabled={loading}
                  className="h-12 w-full rounded-2xl border border-[var(--chat-border)] bg-white/70 px-4 text-[15px] outline-none transition focus:border-[var(--chat-border-strong)]"
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm text-[var(--chat-text-soft)]">邮箱</span>
                <input
                  value={form.email}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      email: event.target.value,
                    }))
                  }
                  placeholder="可选"
                  autoComplete="email"
                  disabled={loading}
                  className="h-12 w-full rounded-2xl border border-[var(--chat-border)] bg-white/70 px-4 text-[15px] outline-none transition focus:border-[var(--chat-border-strong)]"
                />
              </label>
            </>
          ) : null}

          {error ? (
            <div className="rounded-2xl border border-[var(--status-failed-text)]/20 bg-[var(--status-failed-bg)] px-4 py-3 text-sm text-[var(--status-failed-text)]">
              {error}
            </div>
          ) : null}

          <button
            type="submit"
            disabled={loading}
            className="flex h-[52px] w-full items-center justify-center gap-2 rounded-2xl bg-[var(--chat-text)] text-[15px] font-medium text-white disabled:opacity-60"
          >
            {loading ? (
              <span>处理中...</span>
            ) : (
              <>
                <LogIn className="h-4 w-4" />
                <span>{mode === "login" ? "登录" : "注册并登录"}</span>
                <ArrowRight className="h-4 w-4" />
              </>
            )}
          </button>

          {mode === "login" ? (
            <button
              type="button"
              disabled={loading}
              onClick={() => handleDemoLogin().catch(() => {})}
              className="w-full text-center text-sm text-[var(--chat-text-soft)] transition hover:text-[var(--chat-text)] disabled:opacity-60"
            >
              快速体验（demo / 123456）
            </button>
          ) : null}
        </form>
      </motion.div>
    </div>
  );
}
