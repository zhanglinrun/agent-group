export type AgentExecutionFamily = "react" | "plan-execute" | "flow" | "skill-sop";

export interface AgentModeOption {
  id: string;
  name: string;
  icon: string;
  executionMode: string;
  executionFamily: AgentExecutionFamily;
  summary: string;
}

export const AGENT_MODES: AgentModeOption[] = [
  {
    id: "chat",
    name: "对话助手",
    icon: "💬",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "通用问答、交易解释和轻量工具调用"
  },
  {
    id: "file",
    name: "文件问答",
    icon: "📁",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "文件理解、引用回答和上下文追问"
  },
  {
    id: "ppt",
    name: "PPT生成",
    icon: "📊",
    executionMode: "Flow",
    executionFamily: "flow",
    summary: "需求澄清、大纲、搜索、模板、渲染的状态流转"
  },
  {
    id: "deep",
    name: "深度研究",
    icon: "🔬",
    executionMode: "Plan",
    executionFamily: "plan-execute",
    summary: "计划拆解、分步执行、反思和动态重规划"
  },
  {
    id: "image",
    name: "图像生成",
    icon: "🖼",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "图像生成、图生图和多模态参考图处理"
  },
  {
    id: "data",
    name: "数据问答",
    icon: "📈",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "数据分析、表格检索和自然语言转 SQL"
  },
  {
    id: "mrag",
    name: "MRAG 知识问答",
    icon: "MR",
    executionMode: "ReAct",
    executionFamily: "react",
    summary: "多模态检索、知识库证据和资料交叉验证"
  },
  {
    id: "trade-audit",
    name: "交易审计",
    icon: "🧾",
    executionMode: "Trade Flow",
    executionFamily: "flow",
    summary: "按支付、成团、额度到账和退款回滚流程核查交易闭环"
  },
  {
    id: "skills",
    name: "技能助手",
    icon: "🛠",
    executionMode: "Skill",
    executionFamily: "skill-sop",
    summary: "自动选择技能并执行标准流程"
  },
  {
    id: "manual-skills",
    name: "手动技能",
    icon: "🧩",
    executionMode: "Skill",
    executionFamily: "skill-sop",
    summary: "读取技能文件、检索技能目录和运行技能脚本"
  }
];

export function agentModeById(agentId: string): AgentModeOption {
  return AGENT_MODES.find((agent) => agent.id === agentId) || AGENT_MODES[0];
}
