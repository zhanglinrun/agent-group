export type AgentExecutionFamily = "react" | "plan-execute" | "ppt-workflow" | "skill-orchestration" | "auto";

export interface AgentModeOption {
  id: string;
  name: string;
  icon: string;
  executionMode: string;
  executionFamily: AgentExecutionFamily;
  summary: string;
  replanEnabled?: boolean;
  replanLabel?: string;
  userVisible?: boolean;
}

export const AGENT_MODES: AgentModeOption[] = [
  {
    id: "auto",
    name: "智能调度",
    icon: "✨",
    executionMode: "Auto",
    executionFamily: "auto",
    summary: "根据问题与附件自动选择最合适的执行模式"
  },
  {
    id: "chat",
    name: "对话助手",
    icon: "💬",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "通用问答、文件理解和轻量工具调用"
  },
  {
    id: "file",
    name: "文件问答",
    icon: "📁",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "文件理解、引用回答和上下文追问",
    userVisible: false
  },
  {
    id: "ppt",
    name: "PPT生成",
    icon: "📊",
    executionMode: "PPT Workflow",
    executionFamily: "ppt-workflow",
    summary: "需求澄清、大纲、搜索、模板、渲染的业务执行路线"
  },
  {
    id: "deep",
    name: "深度任务",
    icon: "🔬",
    executionMode: "Plan-Execute",
    executionFamily: "plan-execute",
    summary: "计划拆解、分步执行、反思和动态重规划",
    replanEnabled: true,
    replanLabel: "重规划"
  },
  {
    id: "image",
    name: "图像生成",
    icon: "🖼",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "图像生成、图生图和多模态参考图处理",
  },
  {
    id: "data",
    name: "数据问答",
    icon: "📈",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "数据分析、表格检索和自然语言转 SQL",
    userVisible: false
  },
  {
    id: "skills",
    name: "技能助手",
    icon: "🛠",
    executionMode: "Skill Orchestration",
    executionFamily: "skill-orchestration",
    summary: "自动选择技能并组合工具完成任务",
    userVisible: false
  },
  {
    id: "manual-skills",
    name: "Skill",
    icon: "🛠",
    executionMode: "Skill Orchestration",
    executionFamily: "skill-orchestration",
    summary: "选择一个技能并执行对应编排"
  }
];

export const USER_AGENT_MODES: AgentModeOption[] = AGENT_MODES.filter((agent) => agent.userVisible !== false);

export function agentModeById(agentId: string): AgentModeOption {
  return AGENT_MODES.find((agent) => agent.id === agentId) || AGENT_MODES[0];
}

const AGENT_TYPE_ALIASES: Record<string, string> = {
  search: "联网搜索",
  "manual-skills": "技能编排"
};

export function agentTypeLabel(agentId: string): string {
  const normalized = String(agentId || "").trim().toLowerCase();
  if (!normalized) {
    return "对话助手";
  }
  if (AGENT_TYPE_ALIASES[normalized]) {
    return AGENT_TYPE_ALIASES[normalized];
  }
  const matched = AGENT_MODES.find((agent) => agent.id === normalized);
  return matched?.name || agentId;
}

const EXECUTION_MODE_LABELS: Record<string, string> = {
  Auto: "自动选择",
  ReAct: "思考-行动循环",
  "Plan-Execute": "规划-执行",
  "PPT Workflow": "PPT 工作流",
  "Skill Orchestration": "技能编排"
};

const MODE_FAMILY_LABELS: Record<string, string> = {
  auto: "智能调度",
  react: "轻量对话",
  "plan-execute": "深度规划",
  "ppt-workflow": "PPT 流程",
  "skill-orchestration": "技能编排"
};

export function executionModeLabel(mode: string): string {
  const key = String(mode || "").trim();
  return EXECUTION_MODE_LABELS[key] || key || "思考-行动循环";
}

export function modeFamilyLabel(family: string): string {
  const key = String(family || "").trim().toLowerCase();
  return MODE_FAMILY_LABELS[key] || key;
}
