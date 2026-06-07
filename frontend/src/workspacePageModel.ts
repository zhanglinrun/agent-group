import {
  WORKSPACE_PROMPTS,
  WORKSPACES,
  type WorkspaceDefinition,
  type WorkspaceId,
  type WorkspacePrompt
} from "./workspaces";
import {
  workspaceAcceptsFile,
  workspaceDisplayProfile,
  workspaceRuntimeCoverage,
  workspaceServiceProfile,
  workspaceSupportsHistory,
  workspaceToolReadiness,
  type WorkspaceRuntimeCoverage,
  type WorkspaceServiceProfile,
  type WorkspaceToolReadinessSummary
} from "./workspaceServices";

export type WorkspacePageStatus = "ready" | "partial" | "pending";

export type WorkspacePageInputKind =
  | "prompt"
  | "file"
  | "image-options"
  | "data-catalog"
  | "knowledge-base"
  | "quota";

export interface WorkspacePageAction {
  key: string;
  label: string;
  enabled: boolean;
  endpoint: string;
}

export interface WorkspacePageModel {
  workspace: WorkspaceDefinition;
  profile: WorkspaceServiceProfile;
  prompts: WorkspacePrompt[];
  status: WorkspacePageStatus;
  acceptsFiles: boolean;
  supportsHistory: boolean;
  dedicatedRun: boolean;
  dedicatedHistory: boolean;
  inputKinds: WorkspacePageInputKind[];
  actions: WorkspacePageAction[];
  toolReadiness: WorkspaceToolReadinessSummary;
  runtimeCoverage: WorkspaceRuntimeCoverage;
}

export function buildWorkspacePageModel(
  workspaceId: string,
  capabilities?: Record<string, unknown> | null
): WorkspacePageModel {
  const workspace = workspaceById(workspaceId);
  const profile = workspaceDisplayProfile(
    workspace.id,
    capabilities,
    workspaceServiceProfile(workspace.id)
  );
  const toolReadiness = workspaceToolReadiness(workspace.id, capabilities);
  const runtimeCoverage = workspaceRuntimeCoverage(workspace.id, capabilities);
  const supportsHistory = workspaceSupportsHistory(workspace.id);
  const acceptsFiles = workspaceAcceptsFile(workspace.id, profile.taskType);

  return {
    workspace,
    profile,
    prompts: WORKSPACE_PROMPTS[workspace.id] || WORKSPACE_PROMPTS.agent,
    status: pageStatus(capabilities, toolReadiness, runtimeCoverage),
    acceptsFiles,
    supportsHistory,
    dedicatedRun: isDedicatedWorkspaceRun(workspace.id, profile.runEndpoint),
    dedicatedHistory: supportsHistory && isDedicatedWorkspaceHistory(workspace.id, profile.historyEndpoint),
    inputKinds: inputKinds(workspace.id, acceptsFiles),
    actions: pageActions(workspace.id, profile, supportsHistory),
    toolReadiness,
    runtimeCoverage
  };
}

function workspaceById(workspaceId: string): WorkspaceDefinition {
  return WORKSPACES.find((workspace) => workspace.id === workspaceId) || WORKSPACES[0];
}

function pageStatus(
  capabilities: Record<string, unknown> | null | undefined,
  toolReadiness: WorkspaceToolReadinessSummary,
  runtimeCoverage: WorkspaceRuntimeCoverage
): WorkspacePageStatus {
  if (toolReadiness.status === "ready" && runtimeCoverage.runReady) {
    return "ready";
  }
  if (capabilities && (toolReadiness.readyTools.length > 0 || runtimeCoverage.availableTools.length > 0)) {
    return "partial";
  }
  return "pending";
}

function inputKinds(workspaceId: WorkspaceId, acceptsFiles: boolean): WorkspacePageInputKind[] {
  if (workspaceId === "trade") {
    return ["quota"];
  }
  const kinds: WorkspacePageInputKind[] = ["prompt"];
  if (acceptsFiles) {
    kinds.push("file");
  }
  if (workspaceId === "image") {
    kinds.push("image-options");
  }
  if (workspaceId === "data") {
    kinds.push("data-catalog");
  }
  if (workspaceId === "mrag") {
    kinds.push("knowledge-base");
  }
  return kinds;
}

function pageActions(
  workspaceId: WorkspaceId,
  profile: WorkspaceServiceProfile,
  supportsHistory: boolean
): WorkspacePageAction[] {
  const actions: WorkspacePageAction[] = workspaceId === "trade"
    ? []
    : [
        {
          key: "run",
          label: runLabel(workspaceId),
          enabled: Boolean(profile.runEndpoint),
          endpoint: profile.runEndpoint || ""
        }
      ];
  if (supportsHistory) {
    actions.push({
      key: "history",
      label: "查看历史",
      enabled: Boolean(profile.historyEndpoint),
      endpoint: profile.historyEndpoint || ""
    });
  }
  if (workspaceId === "trade") {
    actions.push({
      key: "recharge",
      label: "额度购买",
      enabled: true,
      endpoint: "/api/v1/quota/packages"
    });
  }
  return actions;
}

function runLabel(workspaceId: WorkspaceId): string {
  if (workspaceId === "image") return "生成图像";
  if (workspaceId === "data") return "运行数据问答";
  if (workspaceId === "mrag") return "运行知识问答";
  return "开始对话";
}

function isDedicatedWorkspaceRun(workspaceId: WorkspaceId, endpoint: string | undefined): boolean {
  if (workspaceId === "image" || workspaceId === "data" || workspaceId === "mrag") {
    return Boolean(endpoint);
  }
  return isDedicatedWorkspaceEndpoint(endpoint);
}

function isDedicatedWorkspaceHistory(workspaceId: WorkspaceId, endpoint: string | undefined): boolean {
  if (workspaceId === "image" || workspaceId === "data" || workspaceId === "mrag") {
    return Boolean(endpoint);
  }
  return isDedicatedWorkspaceEndpoint(endpoint);
}

function isDedicatedWorkspaceEndpoint(endpoint: string | undefined): boolean {
  return String(endpoint || "").includes("/workspace/");
}
