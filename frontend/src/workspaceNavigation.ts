import type { AgentMode, WorkspaceId } from "./workspaces";
import { WORKSPACES, workspaceAgentMode, workspaceFromPath, workspacePath } from "./workspaces";
import {
  workspaceDisplayProfile,
  workspaceRuntimeCoverage,
  type WorkspaceRuntimeCoverage,
  type WorkspaceServiceProfile
} from "./workspaceServices";

export interface WorkspaceNavigationItem {
  id: WorkspaceId;
  name: string;
  path: string;
  icon: string;
  agentId: AgentMode;
  active: boolean;
  title: string;
  summary: string;
  runEndpoint: string;
  historyEndpoint: string;
  runtimeStatus: WorkspaceRuntimeCoverage["status"];
  runtimeStatusLabel: string;
  availableTools: string[];
  missingTools: string[];
}

export interface WorkspaceNavigationTarget {
  workspaceId: WorkspaceId;
  path: string;
  agentId: AgentMode;
}

export function buildWorkspaceNavigation(
  activeWorkspaceIdOrPath: string,
  capabilities?: Record<string, unknown> | null
): WorkspaceNavigationItem[] {
  const activeWorkspaceId = normalizeActiveWorkspaceId(activeWorkspaceIdOrPath);
  return WORKSPACES.map((workspace) => {
    const profile = workspaceDisplayProfile(workspace.id, capabilities);
    const coverage = workspaceRuntimeCoverage(workspace.id, capabilities);
    return {
      id: workspace.id,
      name: workspace.name,
      path: workspace.path,
      icon: workspace.icon,
      agentId: workspace.agentId,
      active: workspace.id === activeWorkspaceId,
      title: profile.title,
      summary: profile.summary,
      runEndpoint: safeEndpoint(profile, "runEndpoint"),
      historyEndpoint: safeEndpoint(profile, "historyEndpoint"),
      runtimeStatus: coverage.status,
      runtimeStatusLabel: coverage.statusLabel,
      availableTools: coverage.availableTools,
      missingTools: coverage.missingTools
    };
  });
}

export function activeWorkspaceNavigationItem(
  activeWorkspaceIdOrPath: string,
  capabilities?: Record<string, unknown> | null
): WorkspaceNavigationItem {
  return buildWorkspaceNavigation(activeWorkspaceIdOrPath, capabilities)
    .find((item) => item.active) || buildWorkspaceNavigation("agent", capabilities)[0];
}

export function resolveWorkspaceNavigationTarget(workspaceId: string): WorkspaceNavigationTarget {
  const path = workspacePath(workspaceId);
  return {
    workspaceId: workspaceFromPath(path),
    path,
    agentId: workspaceAgentMode(workspaceId)
  };
}

function normalizeActiveWorkspaceId(value: string): WorkspaceId {
  const text = String(value || "");
  if (text.startsWith("/")) {
    return workspaceFromPath(text);
  }
  return WORKSPACES.some((workspace) => workspace.id === text) ? text as WorkspaceId : "agent";
}

function safeEndpoint(profile: WorkspaceServiceProfile, key: "runEndpoint" | "historyEndpoint"): string {
  return profile[key] || "";
}
