import { BarChart3, BookOpen, CreditCard, FileText, Globe2, ImagePlus, Wallet } from "lucide-react";

import { TOOL_LABELS } from "../workspaces";
import {
  workspaceCapabilityStatus,
  workspaceServiceProfile
} from "../workspaceServices";
import { buildWorkspacePageModel } from "../workspacePageModel";

const PROMPT_ICONS = {
  book: BookOpen,
  file: FileText,
  globe: Globe2,
  image: ImagePlus,
  chart: BarChart3,
  credit: CreditCard
};

export function WorkspaceEmptyState({ workspace, profile, capabilities, pageModel, onPrompt, onOpenRecharge }) {
  const page = pageModel || buildWorkspacePageModel(workspace.id, capabilities);
  const prompts = page.prompts;
  const serviceProfile = profile || page.profile || workspaceServiceProfile(workspace.id);
  const capabilityStatus = workspaceCapabilityStatus(workspace.id, capabilities);
  const toolReadiness = page.toolReadiness;
  const runtimeCoverage = page.runtimeCoverage;
  const isImage = workspace.id === "image";
  const isTrade = workspace.id === "trade";
  const isAgent = workspace.id === "agent";
  const useSimpleEmpty = isAgent || isImage;
  const showWorkspaceRuntime = !useSimpleEmpty && (page.supportsHistory || page.dedicatedRun || isTrade);
  const manualSkills = Array.isArray(capabilities?.manualSkills)
    ? capabilities.manualSkills.slice(0, 6)
    : [];
  return (
    <div className={`empty-state workspace-empty workspace-empty-${workspace.id}`}>
      {!useSimpleEmpty && (
        <div className="empty-icon-wrapper">
          <div className="empty-icon">{workspace.icon}</div>
          <div className="icon-glow" />
        </div>
      )}
      <h2>{useSimpleEmpty ? "今天想做什么？" : workspace.name}</h2>
      {!useSimpleEmpty && <p>{serviceProfile.summary}</p>}
      {showWorkspaceRuntime && (
        <div className="workspace-meter">
          {capabilityStatus.map((item) => (
            <span key={item.key} className={item.active ? "active" : ""}>{item.label}</span>
          ))}
        </div>
      )}
      {showWorkspaceRuntime && (
        <div className={`workspace-readiness-card ${toolReadiness.status}`}>
          <div className="workspace-readiness-head">
            <strong>工具状态</strong>
            <em>{toolReadiness.statusLabel}</em>
          </div>
          <div className="workspace-readiness-metrics">
            <span><b>可用</b>{toolReadiness.readyTools.length}/{toolReadiness.requiredTools.length}</span>
          </div>
          {toolReadiness.missingTools.length > 0 && (
            <div className="workspace-readiness-missing">
              {toolReadiness.missingTools.slice(0, 4).map((toolName) => (
                <span key={toolName}>{TOOL_LABELS[toolName] || toolName}</span>
              ))}
            </div>
          )}
          {toolReadiness.actions[0] && <small>{toolReadiness.actions[0]}</small>}
        </div>
      )}
      {showWorkspaceRuntime && (
        <div className={`workspace-runtime-coverage ${runtimeCoverage.status}`}>
          <span><b>覆盖</b>{runtimeCoverage.statusLabel}</span>
          <span><b>运行</b>{runtimeCoverage.runReady ? "已接入" : "未接入"}</span>
          <span><b>历史</b>{runtimeCoverage.historyReady ? "已接入" : "未接入"}</span>
          <span><b>工具</b>{runtimeCoverage.availableTools.length}/{runtimeCoverage.availableTools.length + runtimeCoverage.missingTools.length}</span>
        </div>
      )}
      {showWorkspaceRuntime && (
        <div className="workspace-tool-strip">
          {serviceProfile.primaryTools.map((toolName) => (
            <span key={toolName}>{TOOL_LABELS[toolName] || toolName}</span>
          ))}
        </div>
      )}
      {manualSkills.length > 0 && !useSimpleEmpty && (
        <div className="workspace-skill-strip">
          {manualSkills.map((skill) => (
            <span key={skill.name || skill.description}>
              <b>{skill.name}</b>
              {Number(skill.scriptCount || 0) > 0 && <em>{skill.scriptCount} scripts</em>}
            </span>
          ))}
        </div>
      )}
      <div className="quick-actions workspace-actions">
        {prompts.map((item) => {
          const Icon = PROMPT_ICONS[item.icon] || BookOpen;
          return (
            <button type="button" className="quick-action" key={item.title} onClick={() => onPrompt(item.prompt)}>
              <Icon size={18} />
              <span>{item.title}</span>
            </button>
          );
        })}
        {isTrade && (
          <button type="button" className="quick-action" onClick={onOpenRecharge}>
            <Wallet size={18} />
            <span>额度购买</span>
          </button>
        )}
      </div>
    </div>
  );
}
