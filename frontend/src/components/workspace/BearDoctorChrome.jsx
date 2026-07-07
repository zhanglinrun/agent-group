import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Brain, LogIn, LogOut, Settings, Wallet } from "lucide-react";
import ThemeToggle from "../ThemeToggle";
import AuthDialog from "./AuthDialog";
import ModelConfigDialog from "./ModelConfigDialog";
import MemoryDialog from "./MemoryDialog";
import { apiSucceeded } from "../../appRuntime";
import {
  deleteUserAgentMemory,
  disableUserAgentMemory,
  getModelConfig,
  getQuotaSummary,
  getUserAuth,
  login,
  logout,
  normalizeApiMessage,
  queryUserAgentMemories,
  register,
  saveModelConfig,
  saveUserAgentMemory
} from "../../services/api";
import { applyTheme, getStoredTheme, nextTheme } from "../../theme";
import { ROUTES } from "../../reactor-ui/router/routes";

const DEMO_AUTH_FORM = {
  username: "demo",
  password: "123456",
  nickname: "演示用户",
  email: "demo@example.com"
};

const EMPTY_AUTH_FORM = {
  username: "",
  password: "",
  nickname: "",
  email: ""
};

function validateAuthForm(form, mode) {
  if (!String(form.username || "").trim()) return "请输入账号";
  if (!String(form.password || "").length) return "请输入密码";
  if (mode === "register" && String(form.password || "").length < 6) return "密码至少 6 位";
  const email = String(form.email || "").trim();
  if (mode === "register" && email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return "邮箱格式不正确";
  }
  return "";
}

export default function BearDoctorChrome({ className = "" }) {
  const navigate = useNavigate();
  const [theme, setTheme] = useState(() => getStoredTheme());
  const [auth, setAuth] = useState(() => getUserAuth());
  const [quota, setQuota] = useState(null);
  const [modelConfig, setModelConfig] = useState(() => getModelConfig());
  const [loginOpen, setLoginOpen] = useState(() => !getUserAuth()?.token);
  const [modelConfigOpen, setModelConfigOpen] = useState(false);
  const [memoryOpen, setMemoryOpen] = useState(false);
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState(EMPTY_AUTH_FORM);
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [userMemories, setUserMemories] = useState([]);
  const [userMemoriesLoading, setUserMemoriesLoading] = useState(false);
  const [userMemoriesError, setUserMemoriesError] = useState("");

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const loadQuota = useCallback(async () => {
    if (!getUserAuth()?.token) {
      setQuota(null);
      return;
    }
    try {
      const res = await getQuotaSummary(20);
      if (res?.code === "0000") {
        setQuota(res.data?.account || null);
      }
    } catch {
      // 额度读取失败时保留上次展示，避免顶栏闪烁。
    }
  }, []);

  const loadUserMemories = useCallback(async () => {
    if (!getUserAuth()?.token) {
      setUserMemories([]);
      setUserMemoriesError("");
      setUserMemoriesLoading(false);
      return;
    }
    setUserMemoriesLoading(true);
    setUserMemoriesError("");
    try {
      const res = await queryUserAgentMemories();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res.info || res.message, "长期记忆读取失败"));
      }
      setUserMemories(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      setUserMemoriesError(normalizeApiMessage(error.message, "长期记忆读取失败"));
    } finally {
      setUserMemoriesLoading(false);
    }
  }, []);

  useEffect(() => {
    loadQuota().catch(() => {});
  }, [auth?.token, loadQuota]);

  useEffect(() => {
    if (!auth?.token) return;
    loadUserMemories().catch(() => {});
  }, [auth?.token, loadUserMemories]);

  const submitAuth = async (nextForm = authForm, nextMode = authMode) => {
    setAuthError("");
    const validationError = validateAuthForm(nextForm, nextMode);
    if (validationError) {
      setAuthError(validationError);
      return;
    }
    setAuthLoading(true);
    try {
      const payload = {
        ...nextForm,
        username: String(nextForm.username || "").trim(),
        password: String(nextForm.password || ""),
        nickname: String(nextForm.nickname || "").trim(),
        email: String(nextForm.email || "").trim()
      };
      const res = nextMode === "login"
        ? await login(payload.username, payload.password)
        : await register(payload);
      if (apiSucceeded(res) && res.data?.token) {
        setAuth(res.data);
        setLoginOpen(false);
      } else {
        setAuthError(normalizeApiMessage(res.info || res.message, nextMode === "login" ? "登录失败" : "注册失败"));
      }
    } catch (error) {
      setAuthError(normalizeApiMessage(error.message, nextMode === "login" ? "登录失败" : "注册失败"));
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    setAuth(null);
    setQuota(null);
    setUserMemories([]);
    setLoginOpen(true);
  };

  const handleSaveModelConfig = async (draft) => {
    try {
      const res = await saveModelConfig(draft);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeApiMessage(res.info || res.message, "模型配置保存失败"));
      }
      setModelConfig(getModelConfig());
      setModelConfigOpen(false);
    } catch (error) {
      window.alert(normalizeApiMessage(error.message, "模型配置保存失败"));
    }
  };

  const handleToggleUserMemory = async (memory, enabled) => {
    if (!memory?.memoryType || userMemoriesLoading) return;
    const candidates = userMemories.filter((item) => item.enabled !== enabled);
    if (!candidates.length) return;
    setUserMemoriesLoading(true);
    try {
      await Promise.all(candidates.map(async (item) => {
        if (enabled) {
          const res = await saveUserAgentMemory({ memoryType: item.memoryType, content: item.content, enabled: true });
          if (!apiSucceeded(res)) throw new Error(res.info || res.message || "长期记忆启用失败");
        } else {
          const res = await disableUserAgentMemory(item.memoryType);
          if (!apiSucceeded(res)) throw new Error(res.info || res.message || "长期记忆关闭失败");
        }
      }));
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(normalizeApiMessage(error.message, enabled ? "长期记忆启用失败" : "长期记忆关闭失败"));
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const handleEnableUserMemory = async (memory) => {
    if (!memory?.memoryType || !memory?.content || userMemoriesLoading) return;
    setUserMemoriesLoading(true);
    try {
      const res = await saveUserAgentMemory({ memoryType: memory.memoryType, content: memory.content, enabled: true });
      if (!apiSucceeded(res)) throw new Error(normalizeApiMessage(res.info || res.message, "长期记忆启用失败"));
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(normalizeApiMessage(error.message, "长期记忆启用失败"));
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const handleDisableUserMemory = async (memory) => {
    if (!memory?.memoryType || userMemoriesLoading) return;
    setUserMemoriesLoading(true);
    try {
      const res = await disableUserAgentMemory(memory.memoryType);
      if (!apiSucceeded(res)) throw new Error(normalizeApiMessage(res.info || res.message, "长期记忆停用失败"));
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(normalizeApiMessage(error.message, "长期记忆停用失败"));
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const handleDeleteUserMemory = async (memory) => {
    if (!memory?.memoryType || userMemoriesLoading) return;
    if (!window.confirm(`确认删除长期记忆「${memory.content || memory.memoryType}」？`)) return;
    setUserMemoriesLoading(true);
    try {
      const res = await deleteUserAgentMemory(memory.memoryType);
      if (!apiSucceeded(res)) throw new Error(normalizeApiMessage(res.info || res.message, "长期记忆删除失败"));
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(normalizeApiMessage(error.message, "长期记忆删除失败"));
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  return (
    <>
      <div className={`top-actions ${className}`.trim()}>
        <ThemeToggle theme={theme} onToggle={() => setTheme((current) => nextTheme(applyTheme(current)))} />
        <button type="button" className="account-btn" onClick={() => setModelConfigOpen(true)}>
          <Settings size={15} />
          <span>模型</span>
        </button>
        <button
          type="button"
          className="account-btn"
          aria-haspopup="dialog"
          aria-expanded={memoryOpen}
          onClick={() => setMemoryOpen(true)}
        >
          <Brain size={15} />
          <span>记忆</span>
        </button>
        <button className="quota-chip" type="button" onClick={() => navigate(ROUTES.WORKSPACE_TRADE)}>
          <Wallet size={15} />
          <span>{Number(quota?.quotaBalance || 0).toFixed(2)} 点</span>
        </button>
        {auth?.token ? (
          <button className="account-btn" type="button" onClick={handleLogout}>
            <LogOut size={15} />
            <span>退出</span>
          </button>
        ) : (
          <button className="account-btn" type="button" onClick={() => setLoginOpen(true)}>
            <LogIn size={15} />
            <span>登录</span>
          </button>
        )}
      </div>

      {loginOpen && (
        <AuthDialog
          mode={authMode}
          setMode={setAuthMode}
          form={authForm}
          setForm={setAuthForm}
          error={authError}
          loading={authLoading}
          onSubmit={(event) => {
            event.preventDefault();
            submitAuth();
          }}
          onDemoLogin={() => {
            setAuthMode("login");
            setAuthForm(DEMO_AUTH_FORM);
            submitAuth(DEMO_AUTH_FORM, "login");
          }}
          onClose={() => setLoginOpen(false)}
        />
      )}

      {modelConfigOpen && (
        <ModelConfigDialog
          config={modelConfig}
          onSave={handleSaveModelConfig}
          onClose={() => setModelConfigOpen(false)}
        />
      )}

      {memoryOpen && (
        <MemoryDialog
          authenticated={Boolean(auth?.token)}
          memories={userMemories}
          loading={userMemoriesLoading}
          error={userMemoriesError}
          onRefresh={loadUserMemories}
          onToggle={handleToggleUserMemory}
          onEnable={handleEnableUserMemory}
          onDisable={handleDisableUserMemory}
          onDelete={handleDeleteUserMemory}
          onLogin={() => {
            setMemoryOpen(false);
            setLoginOpen(true);
          }}
          onClose={() => setMemoryOpen(false)}
        />
      )}
    </>
  );
}
