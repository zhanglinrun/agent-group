import { Loader2, RotateCcw, Trash2 } from "lucide-react";

const MEMORY_TYPE_LABELS = {
  output_style: "输出风格",
  business_context: "业务背景",
  preference: "偏好"
};

function memoryTypeLabel(memoryType) {
  return MEMORY_TYPE_LABELS[memoryType] || memoryType || "记忆";
}

export function UserMemoryPanel({
  title = "长期记忆",
  authenticated = true,
  memories = [],
  loading = false,
  error = "",
  onRefresh,
  onToggle,
  onEnable,
  onDisable,
  onDelete,
  onLogin
}) {
  const items = memories.filter(Boolean);
  const enabledItems = items.filter((memory) => memory.enabled !== false);
  const memoryEnabled = enabledItems.length > 0;
  const subtitle = !authenticated
    ? "登录后可查看和管理用户偏好"
    : items.length ? `已启用 ${enabledItems.length}/${items.length} 条` : "任务结束后会自动沉淀偏好、风格和业务背景";
  return (
    <section className="user-memory-panel">
      <div className="user-memory-head">
        <div>
          <strong>{title}</strong>
          <span>{subtitle}</span>
        </div>
        <div className="user-memory-head-actions">
          <label className="user-memory-switch" title={memoryEnabled ? "关闭长期记忆" : "启用长期记忆"}>
            <input
              type="checkbox"
              checked={memoryEnabled}
              disabled={loading || !authenticated}
              onChange={(event) => onToggle?.(event.target.checked)}
            />
            <span />
          </label>
          <button type="button" onClick={onRefresh} disabled={loading || !authenticated} aria-label="刷新长期记忆">
            {loading ? <Loader2 size={14} className="spin" /> : <RotateCcw size={14} />}
          </button>
        </div>
      </div>
      {error && <div className="user-memory-error">{error}</div>}
      {!authenticated ? (
        <div className="user-memory-empty">
          <span>登录后展示长期记忆</span>
          <button type="button" onClick={onLogin}>登录</button>
        </div>
      ) : items.length > 0 ? (
        <div className="user-memory-list">
          {items.map((memory) => (
            <article className={memory.enabled === false ? "user-memory-item disabled" : "user-memory-item"} key={memory.memoryType}>
              <div>
                <strong>{memoryTypeLabel(memory.memoryType)}</strong>
                <p>{memory.content}</p>
              </div>
              <div className="user-memory-actions">
                {memory.enabled === false ? (
                  <button
                    type="button"
                    onClick={() => onEnable?.(memory)}
                    disabled={loading}
                    title="启用这条记忆"
                  >
                    <RotateCcw size={14} />
                    <span>启用</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => onDisable?.(memory)}
                    disabled={loading}
                    title="停用这条记忆"
                  >
                    <RotateCcw size={14} />
                    <span>停用</span>
                  </button>
                )}
                <button
                  type="button"
                  className="danger"
                  onClick={() => onDelete?.(memory)}
                  disabled={loading}
                  title="删除这条记忆"
                >
                  <Trash2 size={14} />
                  <span>删除</span>
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="user-memory-empty">
          {loading ? "正在读取长期记忆" : "暂无启用记忆"}
        </div>
      )}
    </section>
  );
}
