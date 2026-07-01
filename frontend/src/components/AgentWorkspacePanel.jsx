import { AlertTriangle, BookOpen, FileText, Loader2, Plus, RotateCcw, ShieldCheck } from "lucide-react";

import { buildAgentWorkspace } from "../agentWorkspace";
import { WorkspacePanelHeader } from "./WorkspacePanelHeader";

export function AgentWorkspacePanel({
  projects = [],
  model,
  activeWorkspaceId = "",
  loading,
  error,
  onRefresh,
  onCreate,
  onSelect,
  onApplyPatch
}) {
  const workspace = model || buildAgentWorkspace(null);
  const hasWorkspace = Boolean(activeWorkspaceId);
  const visibleDrafts = workspace.draftFiles.slice(0, 3);
  const visibleReferences = workspace.referenceFiles.slice(0, 3);
  const visiblePatches = workspace.pendingPatches.slice(0, 3);
  const hasWorkspaceDetails = hasWorkspace && (
    visibleDrafts.length > 0 || visibleReferences.length > 0 || visiblePatches.length > 0
  );
  return (
    <section className={`agent-workspace-panel ${hasWorkspaceDetails ? "" : "compact"}`}>
      <WorkspacePanelHeader
        className="agent-workspace-head"
        eyebrow={<span className="agent-workspace-kicker">工作上下文</span>}
        title={workspace.title}
        subtitleElement={<em>{workspace.subtitle || workspace.contextSummary}</em>}
        trailing={(
          <div className="agent-workspace-actions">
          {projects.length > 0 && (
            <select
              value={activeWorkspaceId}
              onChange={(event) => onSelect?.(event.target.value)}
              disabled={loading}
            >
              {projects.map((project) => (
                <option key={project.projectId} value={project.projectId}>
                  {project.title || project.projectId}
                </option>
              ))}
            </select>
          )}
          <button type="button" onClick={onRefresh} disabled={loading}>
            {loading ? <Loader2 size={14} className="spin" /> : <RotateCcw size={14} />}
          </button>
          <button type="button" className="primary" onClick={onCreate} disabled={loading}>
            <Plus size={14} />
            <span>新建</span>
          </button>
        </div>
        )}
      />
      {error && <div className="agent-workspace-error"><AlertTriangle size={14} /> <span>{error}</span></div>}
      <div className="agent-workspace-metrics">
        <span><b>{workspace.statusLabel}</b>状态</span>
        <span><b>{workspace.fileCount}</b>材料</span>
        <span><b>{workspace.pendingPatchCount}</b>待确认补丁</span>
      </div>
      {hasWorkspaceDetails ? (
        <div className="agent-workspace-grid">
          <WorkspaceFileColumn
            icon={<FileText size={14} />}
            title="工作材料"
            files={visibleDrafts}
            emptyText="暂无工作材料"
          />
          <WorkspaceFileColumn
            icon={<BookOpen size={14} />}
            title="参考资料"
            files={visibleReferences}
            emptyText="暂无参考资料"
          />
          <WorkspacePatchColumn
            patches={visiblePatches}
            loading={loading}
            onApplyPatch={onApplyPatch}
          />
        </div>
      ) : !hasWorkspace ? (
        <div className="agent-workspace-empty wide">新建工作区后，上传文件会自动进入当前工作区</div>
      ) : null}
    </section>
  );
}

function WorkspaceFileColumn({ icon, title, files = [], emptyText }) {
  return (
    <div className="agent-workspace-column">
      <div className="agent-workspace-column-head">
        {icon}
        <strong>{title}</strong>
      </div>
      {files.map((file) => (
        <WorkspaceFileRow file={file} key={file.fileId || file.fileName} />
      ))}
      {files.length === 0 && <div className="agent-workspace-empty">{emptyText}</div>}
    </div>
  );
}

function WorkspacePatchColumn({ patches = [], loading, onApplyPatch }) {
  return (
    <div className="agent-workspace-column">
      <div className="agent-workspace-column-head">
        <ShieldCheck size={14} />
        <strong>待确认补丁</strong>
      </div>
      {patches.map((patch) => (
        <article className="agent-patch-row" key={patch.patchId || patch.title}>
          <div>
            <b>{patch.title || patch.patchId}</b>
            <span>{patch.reason || patch.fileId || "等待人工确认"}</span>
          </div>
          <button type="button" onClick={() => onApplyPatch?.(patch)} disabled={loading}>
            确认
          </button>
        </article>
      ))}
      {patches.length === 0 && <div className="agent-workspace-empty">暂无待确认补丁</div>}
    </div>
  );
}

function WorkspaceFileRow({ file = {} }) {
  return (
    <article className="agent-workspace-file-row">
      <div>
        <b>{file.fileName || file.fileId || "未命名文件"}</b>
        <span>{file.summary || file.folderType || "暂无摘要"}</span>
      </div>
      <em>{file.fileType || file.folderType || "-"}</em>
    </article>
  );
}
