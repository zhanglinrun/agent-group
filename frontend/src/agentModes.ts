export type AgentExecutionFamily = "react" | "plan-execute" | "ppt-workflow" | "skill-orchestration";

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
    id: "mrag",
    name: "MRAG 知识问答",
    icon: "MR",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "多模态检索、知识库证据和资料交叉验证",
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
