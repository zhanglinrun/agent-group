export type WorkspaceId = "agent" | "image" | "data" | "mrag" | "trade";

export type AgentMode = "chat" | "image" | "data" | "mrag" | "trade-diagnosis";

export interface WorkspaceDefinition {
  id: WorkspaceId;
  name: string;
  path: string;
  icon: string;
  agentId: AgentMode;
  userVisible?: boolean;
}

export interface WorkspacePrompt {
  icon: "book" | "file" | "globe" | "image" | "chart" | "credit";
  title: string;
  prompt: string;
}

export const WORKSPACES: WorkspaceDefinition[] = [
  { id: "agent", name: "熊博士Agent", path: "/", icon: "🐻", agentId: "chat" },
  { id: "image", name: "图像生成", path: "/workspace/image", icon: "🖼", agentId: "image" },
  { id: "data", name: "数据问答", path: "/workspace/data", icon: "📈", agentId: "data", userVisible: false },
  { id: "mrag", name: "MRAG 知识问答", path: "/workspace/mrag", icon: "MR", agentId: "mrag", userVisible: false },
  { id: "trade", name: "拼团交易", path: "/workspace/trade", icon: "💳", agentId: "trade-diagnosis", userVisible: false }
];

export const USER_WORKSPACES: WorkspaceDefinition[] = WORKSPACES.filter((workspace) => workspace.userVisible !== false);

export function workspaceFromPath(pathname: string): WorkspaceId {
  const path = String(pathname || "/").replace(/\/+$/, "") || "/";
  return WORKSPACES.find((workspace) => workspace.path === path)?.id || "agent";
}

export function userWorkspaceFromPath(pathname: string): WorkspaceId {
  const workspaceId = workspaceFromPath(pathname);
  return isUserWorkspace(workspaceId) ? workspaceId : "agent";
}

export function workspacePath(workspaceId: string): string {
  return WORKSPACES.find((workspace) => workspace.id === workspaceId)?.path || "/";
}

export function workspaceAgentMode(workspaceId: string): AgentMode {
  return WORKSPACES.find((workspace) => workspace.id === workspaceId)?.agentId || "chat";
}

export function isUserWorkspace(workspaceId: string): boolean {
  return USER_WORKSPACES.some((workspace) => workspace.id === workspaceId);
}

export const WORKSPACE_PROMPTS: Record<WorkspaceId, WorkspacePrompt[]> = {
  agent: [
    { icon: "book", title: "项目技术报告", prompt: "帮我把这个项目整理成一份技术报告，突出 Agent 编排、工具调用和交易闭环" },
    { icon: "file", title: "PPT 大纲", prompt: "帮我生成一份项目汇报 PPT 大纲，包含架构、核心流程和亮点" },
    { icon: "globe", title: "深度任务", prompt: "帮我调研多智能体编排和 Skill 自动化的最新工程实践，并输出对比结论" }
  ],
  image: [
    { icon: "image", title: "项目封面图", prompt: "生成一张用于“Agent + 拼团交易系统”项目展示的封面图，突出智能体工具调用、额度账户和拼团交易闭环。" },
    { icon: "image", title: "系统架构图", prompt: "生成一张系统架构示意图，包含前端工作台、Agent 运行时、工具服务、额度账户、拼团订单、支付回调和运行账本。" },
    { icon: "image", title: "流程插图", prompt: "生成一张拼团购买额度后使用 Agent 能力的流程插图，强调支付成功、等待成团、额度到账、调用工具和扣减额度。" }
  ],
  data: [
    { icon: "chart", title: "数据指标分析", prompt: "分析上传表格中的核心指标、分布变化和异常值，并给出可复用的统计口径。" },
    { icon: "chart", title: "额度消耗分析", prompt: "分析 Agent 任务的额度消耗：按任务类型统计调用次数、消耗额度、失败率和平均耗时，并指出异常排查口径。" },
    { icon: "chart", title: "样本分布分析", prompt: "基于当前数据样本分析字段分布、缺失值、重复记录和可能影响结论的数据质量问题。" }
  ],
  mrag: [
    { icon: "file", title: "多资料问答", prompt: "请基于我上传的文件、图片和表格做一次多模态知识问答，输出结论、证据来源和不确定点。" },
    { icon: "globe", title: "资料交叉验证", prompt: "请结合文件内容、知识库和可联网资料交叉验证这个问题，区分确定事实、推断和需要补充的数据。" },
    { icon: "chart", title: "资料一致性整理", prompt: "请把多份资料放在一起整理，指出内容差异、缺失字段和需要补充的证据。" }
  ],
  trade: []
};

export const TOOL_LABELS: Record<string, string> = {
  data_analysis: "数据分析",
  report_tool: "报告工具",
  planning: "任务规划",
  web_fetch: "网页读取",
  code_interpreter: "代码解释器",
  image_generation: "图像生成",
  multimodal_agent: "多模态分析",
  deep_search: "深度搜索",
  file_tool: "文件工具",
  script_runner: "脚本运行",
  table_rag: "表格检索",
  nl2sql: "自然语言转 SQL",
  quota_usage: "额度对账"
};

export const OUTPUT_KIND_LABELS: Record<string, string> = {
  answer: "回答",
  reference: "引用来源",
  artifact: "任务产物",
  image: "图片",
  prompt: "提示词",
  table: "表格",
  sql: "查询语句",
  chart: "图表",
  report: "报告",
  evidence: "证据",
  file: "文件",
  order: "订单",
  quota: "额度",
  status: "状态",
  preview: "预览"
};
