import { Brain, X } from "lucide-react";
import { UserMemoryPanel } from "../UserMemoryPanel";

export default function MemoryDialog({
  authenticated,
  memories,
  loading,
  error,
  onRefresh,
  onToggle,
  onEnable,
  onDisable,
  onDelete,
  onLogin,
  onClose
}) {
  return (
    <div className="modal-overlay memory-dialog-overlay">
      <div className="memory-dialog" role="dialog" aria-modal="true" aria-labelledby="memory-dialog-title">
        <button type="button" className="modal-close" onClick={onClose} aria-label="关闭长期记忆"><X size={18} /></button>
        <div className="model-config-head">
          <Brain size={20} />
          <h3 id="memory-dialog-title">长期记忆</h3>
        </div>
        <UserMemoryPanel
          title="记忆状态"
          authenticated={authenticated}
          memories={memories}
          loading={loading}
          error={error}
          onRefresh={onRefresh}
          onToggle={onToggle}
          onEnable={onEnable}
          onDisable={onDisable}
          onDelete={onDelete}
          onLogin={onLogin}
        />
      </div>
    </div>
  );
}
