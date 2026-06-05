import { planStepLabel, type TimelineItem } from "./agentTimeline";

export interface PlannerHistoryVersion {
  id: string;
  title: string;
  stepCount: number;
  stageCount: number;
  flowUpdates: number;
  revision: number;
  changeType: string;
  replanReason: string;
  status: "planned" | "running" | "completed" | "blocked" | "replanned";
  summary: string;
  latest: boolean;
}

function planSteps(item: TimelineItem): unknown[] {
  const stages = Array.isArray(item.flowStages) ? item.flowStages : [];
  if (stages.length) {
    return stages.flatMap((stage) => (
      stage && typeof stage === "object" && Array.isArray((stage as Record<string, unknown>).steps)
        ? (stage as Record<string, unknown>).steps as unknown[]
        : []
    ));
  }
  return Array.isArray(item.steps) ? item.steps : [];
}

function flowStatusRank(status: string): PlannerHistoryVersion["status"] {
  const normalized = String(status || "").toLowerCase();
  if (normalized === "replanned") return "replanned";
  if (normalized === "blocked" || normalized === "error" || normalized === "failed") return "blocked";
  if (normalized === "completed" || normalized === "done") return "completed";
  if (normalized === "running") return "running";
  return "planned";
}

function mergeStatus(
  current: PlannerHistoryVersion["status"],
  next: PlannerHistoryVersion["status"]
): PlannerHistoryVersion["status"] {
  const priority = ["planned", "completed", "running", "blocked", "replanned"];
  return priority.indexOf(next) > priority.indexOf(current) ? next : current;
}

function firstStepSummary(item: TimelineItem): string {
  const steps = planSteps(item);
  if (!steps.length) return "";
  const label = planStepLabel(steps[0]);
  return steps.length > 1 ? `${label} 等 ${steps.length} 步` : label;
}

export function buildPlannerHistory(timeline: TimelineItem[] = []): PlannerHistoryVersion[] {
  const versions: PlannerHistoryVersion[] = [];
  let currentIndex = -1;

  for (const item of timeline || []) {
    if (item.type === "plan") {
      const steps = planSteps(item);
      const revision = Number(item.revision || versions.length + 1);
      versions.push({
        id: `plan-${versions.length + 1}`,
        title: String(item.title || `执行计划 ${versions.length + 1}`),
        stepCount: steps.length,
        stageCount: Array.isArray(item.flowStages) ? item.flowStages.length : 0,
        flowUpdates: 0,
        revision: Number.isFinite(revision) && revision > 0 ? revision : versions.length + 1,
        changeType: String(item.changeType || (versions.length > 0 ? "replan" : "initial")),
        replanReason: String(item.replanReason || ""),
        status: "planned",
        summary: firstStepSummary(item),
        latest: false
      });
      currentIndex = versions.length - 1;
      continue;
    }

    if (item.type === "flow" && currentIndex >= 0) {
      const current = versions[currentIndex];
      versions[currentIndex] = {
        ...current,
        flowUpdates: current.flowUpdates + 1,
        status: mergeStatus(current.status, flowStatusRank(String(item.status || ""))),
        replanReason: String(item.status || "").toLowerCase() === "replanned" && item.message
          ? String(item.message)
          : current.replanReason
      };
    }
  }

  if (versions.length) {
    versions[versions.length - 1] = {
      ...versions[versions.length - 1],
      latest: true
    };
  }
  return versions;
}
