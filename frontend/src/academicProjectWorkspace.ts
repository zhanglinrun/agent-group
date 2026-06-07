export type AcademicProjectLike = {
  projectId?: unknown;
  title?: unknown;
  researchQuestion?: unknown;
  targetVenue?: unknown;
  writingStatus?: unknown;
  progressNote?: unknown;
  files?: Array<Record<string, unknown>>;
  patches?: Array<Record<string, unknown>>;
  fileCount?: unknown;
  pendingPatchCount?: unknown;
};

export type AcademicProjectWorkspaceModel = {
  projectId: string;
  title: string;
  subtitle: string;
  statusLabel: string;
  fileCount: number;
  pendingPatchCount: number;
  draftFiles: Array<Record<string, unknown>>;
  referenceFiles: Array<Record<string, unknown>>;
  pendingPatches: Array<Record<string, unknown>>;
  contextSummary: string;
};

function text(value: unknown): string {
  return String(value || "").trim();
}

function numberValue(value: unknown): number {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
}

function statusLabel(status: unknown): string {
  const normalized = text(status).toUpperCase();
  const labels: Record<string, string> = {
    DRAFTING: "写作中",
    REVIEWING: "检查中",
    REVISING: "修改中",
    FINALIZED: "已定稿"
  };
  return labels[normalized] || normalized || "未设置";
}

function isReferenceFile(file: Record<string, unknown>): boolean {
  const folderType = text(file.folderType).toLowerCase();
  return folderType.includes("reference") || folderType.includes("paper");
}

function isDraftFile(file: Record<string, unknown>): boolean {
  const folderType = text(file.folderType).toLowerCase();
  return folderType.includes("draft") || folderType.includes("manuscript");
}

export function buildAcademicProjectWorkspace(project: AcademicProjectLike | null | undefined): AcademicProjectWorkspaceModel {
  const source = project || {};
  const files = Array.isArray(source.files) ? source.files : [];
  const patches = Array.isArray(source.patches) ? source.patches : [];
  const pendingPatches = patches.filter((patch) => text(patch.status).toUpperCase() === "PENDING");
  const title = text(source.title) || "未选择学术项目";
  const researchQuestion = text(source.researchQuestion);
  const targetVenue = text(source.targetVenue);
  const subtitle = [researchQuestion, targetVenue].filter(Boolean).join(" · ");
  const fileCount = numberValue(source.fileCount || files.length);
  const pendingPatchCount = numberValue(source.pendingPatchCount || pendingPatches.length);
  const contextParts = [
    researchQuestion ? `研究问题：${researchQuestion}` : "",
    targetVenue ? `目标 venue：${targetVenue}` : "",
    fileCount ? `材料 ${fileCount} 份` : "",
    pendingPatchCount ? `待确认补丁 ${pendingPatchCount} 个` : ""
  ].filter(Boolean);

  return {
    projectId: text(source.projectId),
    title,
    subtitle,
    statusLabel: statusLabel(source.writingStatus),
    fileCount,
    pendingPatchCount,
    draftFiles: files.filter(isDraftFile),
    referenceFiles: files.filter(isReferenceFile),
    pendingPatches,
    contextSummary: contextParts.join("；") || "当前还没有项目上下文"
  };
}
