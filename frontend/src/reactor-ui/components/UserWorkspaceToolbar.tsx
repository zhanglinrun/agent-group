import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { Brain, Moon, Settings, Sun, Wallet } from "lucide-react";

import ModelConfigDialog from "../../components/workspace/ModelConfigDialog";
import MemoryDialog from "../../components/workspace/MemoryDialog";
import { apiSucceeded } from "../../appRuntime";
import {
  deleteUserAgentMemory,
  disableUserAgentMemory,
  getModelConfig,
  getQuotaSummary,
  getUserAuth,
  normalizeApiMessage,
  queryUserAgentMemories,
  saveModelConfig,
  saveUserAgentMemory,
} from "../../services/api";
import { applyTheme, getStoredTheme, nextTheme } from "../../theme";
import { ROUTES } from "@/router/routes";

type UserWorkspaceToolbarProps = {
  className?: string;
};

function toolbarButtonClassName(active = false) {
  return [
    "inline-flex h-9 shrink-0 items-center gap-1.5 rounded-xl border px-3 text-[13px] transition-colors",
    active
      ? "border-[var(--chat-border-strong)] bg-[var(--chat-surface-soft)] text-[var(--chat-text)]"
      : "border-[var(--chat-border)] bg-[var(--chat-surface)]/90 text-[var(--chat-text-soft)] hover:bg-[var(--chat-surface-soft)] hover:text-[var(--chat-text)]",
  ].join(" ");
}

export default function UserWorkspaceToolbar(props: UserWorkspaceToolbarProps) {
  const navigate = useNavigate();
  const [theme, setTheme] = useState(() => getStoredTheme());
  const [quota, setQuota] = useState<{ quotaBalance?: number } | null>(null);
  const [modelConfig, setModelConfig] = useState(() => getModelConfig());
  const [modelConfigOpen, setModelConfigOpen] = useState(false);
  const [memoryOpen, setMemoryOpen] = useState(false);
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
      // 额度读取失败时保留上次展示。
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
        throw new Error(
          normalizeApiMessage(res.info || res.message, "长期记忆读取失败")
        );
      }
      setUserMemories(Array.isArray(res.data) ? res.data : []);
    } catch (error) {
      setUserMemoriesError(
        normalizeApiMessage((error as Error).message, "长期记忆读取失败")
      );
    } finally {
      setUserMemoriesLoading(false);
    }
  }, []);

  useEffect(() => {
    loadQuota().catch(() => {});
  }, [loadQuota]);

  useEffect(() => {
    loadUserMemories().catch(() => {});
  }, [loadUserMemories]);

  const handleSaveModelConfig = async (draft: ReturnType<typeof getModelConfig>) => {
    try {
      const res = await saveModelConfig(draft);
      if (!apiSucceeded(res)) {
        throw new Error(
          normalizeApiMessage(res.info || res.message, "模型配置保存失败")
        );
      }
      setModelConfig(getModelConfig());
      setModelConfigOpen(false);
    } catch (error) {
      window.alert(
        normalizeApiMessage((error as Error).message, "模型配置保存失败")
      );
    }
  };

  const handleToggleUserMemory = async (enabled: boolean) => {
    if (userMemoriesLoading) return;
    const candidates = userMemories.filter(
      (item: { enabled?: boolean }) => item.enabled !== enabled
    );
    if (!candidates.length) return;
    setUserMemoriesLoading(true);
    try {
      await Promise.all(
        candidates.map(async (item: { memoryType?: string; content?: string }) => {
          if (enabled) {
            const res = await saveUserAgentMemory({
              memoryType: item.memoryType,
              content: item.content,
              enabled: true,
            });
            if (!apiSucceeded(res)) {
              throw new Error(res.info || res.message || "长期记忆启用失败");
            }
          } else {
            const res = await disableUserAgentMemory(item.memoryType);
            if (!apiSucceeded(res)) {
              throw new Error(res.info || res.message || "长期记忆关闭失败");
            }
          }
        })
      );
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(
        normalizeApiMessage(
          (error as Error).message,
          enabled ? "长期记忆启用失败" : "长期记忆关闭失败"
        )
      );
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const handleEnableUserMemory = async (memory: {
    memoryType?: string;
    content?: string;
  }) => {
    if (!memory?.memoryType || !memory?.content || userMemoriesLoading) return;
    setUserMemoriesLoading(true);
    try {
      const res = await saveUserAgentMemory({
        memoryType: memory.memoryType,
        content: memory.content,
        enabled: true,
      });
      if (!apiSucceeded(res)) {
        throw new Error(res.info || res.message || "长期记忆启用失败");
      }
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(
        normalizeApiMessage((error as Error).message, "长期记忆启用失败")
      );
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const handleDisableUserMemory = async (memory: { memoryType?: string }) => {
    if (!memory?.memoryType || userMemoriesLoading) return;
    setUserMemoriesLoading(true);
    try {
      const res = await disableUserAgentMemory(memory.memoryType);
      if (!apiSucceeded(res)) {
        throw new Error(res.info || res.message || "长期记忆停用失败");
      }
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(
        normalizeApiMessage((error as Error).message, "长期记忆停用失败")
      );
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const handleDeleteUserMemory = async (memory: {
    memoryType?: string;
    content?: string;
  }) => {
    if (!memory?.memoryType || userMemoriesLoading) return;
    if (
      !window.confirm(
        `确认删除长期记忆「${memory.content || memory.memoryType}」？`
      )
    ) {
      return;
    }
    setUserMemoriesLoading(true);
    try {
      const res = await deleteUserAgentMemory(memory.memoryType);
      if (!apiSucceeded(res)) {
        throw new Error(res.info || res.message || "长期记忆删除失败");
      }
      await loadUserMemories();
    } catch (error) {
      setUserMemoriesError(
        normalizeApiMessage((error as Error).message, "长期记忆删除失败")
      );
    } finally {
      setUserMemoriesLoading(false);
    }
  };

  const dialogPortal =
    modelConfigOpen || memoryOpen
      ? createPortal(
          <div className="bear-doctor-app" data-theme={theme}>
            {modelConfigOpen ? (
              <ModelConfigDialog
                config={modelConfig}
                onSave={handleSaveModelConfig}
                onClose={() => setModelConfigOpen(false)}
              />
            ) : null}
            {memoryOpen ? (
              <MemoryDialog
                authenticated={Boolean(getUserAuth()?.token)}
                memories={userMemories}
                loading={userMemoriesLoading}
                error={userMemoriesError}
                onRefresh={loadUserMemories}
                onToggle={handleToggleUserMemory}
                onEnable={handleEnableUserMemory}
                onDisable={handleDisableUserMemory}
                onDelete={handleDeleteUserMemory}
                onLogin={() => setMemoryOpen(false)}
                onClose={() => setMemoryOpen(false)}
              />
            ) : null}
          </div>,
          document.body
        )
      : null;

  const isDark = theme === "dark";

  return (
    <>
      <div className={["flex flex-wrap items-center justify-end gap-2", props.className].filter(Boolean).join(" ")}>
        <button
          type="button"
          className={toolbarButtonClassName()}
          title={isDark ? "切换浅色主题" : "切换深色主题"}
          onClick={() =>
            setTheme((current) => nextTheme(applyTheme(current)))
          }
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          <span>{isDark ? "浅色" : "深色"}</span>
        </button>
        <button
          type="button"
          className={toolbarButtonClassName(modelConfigOpen)}
          onClick={() => setModelConfigOpen(true)}
        >
          <Settings className="h-4 w-4" />
          <span>模型</span>
        </button>
        <button
          type="button"
          className={toolbarButtonClassName(memoryOpen)}
          aria-haspopup="dialog"
          aria-expanded={memoryOpen}
          onClick={() => setMemoryOpen(true)}
        >
          <Brain className="h-4 w-4" />
          <span>记忆</span>
        </button>
        <button
          type="button"
          className={toolbarButtonClassName()}
          onClick={() => navigate(`${ROUTES.WORKSPACE_TRADE}?tab=packages`)}
        >
          <Wallet className="h-4 w-4" />
          <span>购买额度</span>
          <span className="text-[11px] opacity-75">
            {Number(quota?.quotaBalance || 0).toFixed(2)} 点
          </span>
        </button>
      </div>
      {dialogPortal}
    </>
  );
}
