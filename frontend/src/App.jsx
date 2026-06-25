import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import {
  AlertTriangle,
  ArrowUp,
  ArrowLeft,
  BarChart3,
  Check,
  Copy,
  CreditCard,
  Download,
  Eye,
  FileText,
  Globe2,
  ImagePlus,
  Loader2,
  LogIn,
  LogOut,
  MessageCircle,
  Paperclip,
  Pencil,
  Plus,
  RotateCcw,
  Search,
  Settings,
  Square,
  Trash2,
  UserPlus,
  Wallet,
  X
} from "lucide-react";
import ThemeToggle from "./components/ThemeToggle";
import { MarkdownRenderer } from "./components/MarkdownRenderer";
import { ImageWorkspacePanel } from "./components/ImageWorkspacePanel";
import { DataWorkspacePanel } from "./components/DataWorkspacePanel";
import { TradeWorkspacePanel } from "./components/TradeWorkspacePanel";
import { AcademicProjectPanel } from "./components/AcademicProjectPanel";
import { SessionMemoryPanel } from "./components/SessionMemoryPanel";
import { WorkspaceEmptyState } from "./components/WorkspaceEmptyState";

const AdminDashboard = lazy(() => import("./components/AdminDashboard"));
import {
  TOOL_LABELS,
  WORKSPACES,
  workspaceAgentMode,
  workspaceFromPath,
  workspacePath
} from "./workspaces";
import {
  buildKnowledgeBaseCatalog,
  buildWorkspaceDataCatalogDraft,
  buildWorkspaceDataRunPayload,
  buildWorkspaceImageGeneratePayload,
  buildWorkspaceStreamDraft,
  knowledgeBaseCatalogKey,
  normalizeWorkspaceHistoryItems,
  workspaceAcceptsFile,
  workspaceSupportsHistory
} from "./workspaceServices";
import {
  isTimelineAttentionItem,
  mergeThinking,
  mergeTimelineEvent,
  planStepLabel,
  planStepMeta,
  replayEventsToTimeline,
  streamEventToTimelineItem,
  timelineItemStatus,
  timelineItemStatusLabel
} from "./agentTimeline";
import { buildPlannerHistory } from "./plannerHistory";
import { buildAgentRunDigest } from "./agentRunDigest";
import { buildAgentRunEvidenceSummary } from "./agentRunEvidence";
import {
  eventArtifacts,
  mergeArtifacts,
  mergeResultPanels,
  replayEventsToArtifacts,
  replayEventsToResultPanels,
  resultPanelKindLabel,
  runDetailToResultPanels,
  toUiArtifact,
  toolResultPanels
} from "./taskArtifacts";
import { buildArtifactPreviewModel } from "./artifactPreview";
import {
  apiSucceeded,
  attachReplayTimeline,
  createLocalPreviewUrl,
  createRuntimeId,
  hasAssistantPayload,
  isImageArtifact,
  isImageUpload,
  isOperatorAuthText,
  isPaymentFormHtml,
  latestAssistantWithPayload,
  openGatewayPayment,
  paymentReturnUrl,
  preferredFrontendPayChannel,
  revokeLocalPreviewUrl,
  safeExternalUrl,
  safeResourceUrl,
  toUiReference,
  workspaceDataToolResultEvent,
  workspaceImageArtifacts,
  workspaceImageToolResultEvent,
  workspaceMragToolResultEvent
} from "./appRuntime";
import {
  applyAcademicProjectPatch,
  bindAcademicProjectFile,
  createPayment,
  createDirectOrder,
  createAcademicProject,
  deleteAcademicSession,
  deleteKnowledgeDocument,
  downloadAcademicArtifact,
  generateWorkspaceImage,
  getKnowledgeDocumentFullContent,
  getAdminAuth,
  getKnowledgeFragments,
  getKnowledgeDocuments,
  getModelConfig,
  getQuotaSummary,
  getSessionId,
  getUserModelConfig,
  getUserAuth,
  lockMarketPayOrder,
  login,
  logout,
  modelConfigReady,
  normalizeApiMessage,
  queryAgentCapabilities,
  queryAcademicReplay,
  queryAcademicRunDetail,
  queryAcademicProjects,
  queryAcademicTaskStatus,
  queryAcademicSessionDetail,
  queryAcademicSessions,
  queryGroupBuyMarketConfig,
  queryQuotaPackages,
  queryWorkspaceDataCatalog,
  queryWorkspaceDataHistory,
  queryWorkspaceImageHistory,
  queryWorkspaceMragHistory,
  queryUserOrderList,
  rebuildKnowledgeVector,
  register,
  rollbackAcademicSession,
  requestAcademicAttachStream,
  requestAcademicResumeStream,
  requestAcademicStream,
  runWorkspaceData,
  runWorkspaceMrag,
  saveAdminAuth,
  saveModelConfig,
  stopAcademicStream,
  compensateKnowledgeVector,
  uploadKnowledgeDocument,
  uploadKnowledgeWebUrl,
  uploadAcademicFile
} from "./services/api";
import { applyTheme, getStoredTheme, nextTheme } from "./theme";
import { APP_ROUTES } from "./routes";
import { USER_AGENT_MODES } from "./agentModes";
import { summarizeTradeWorkspace } from "./tradeWorkspace";
import { buildWorkspacePageModel } from "./workspacePageModel";
import { buildAcademicProjectWorkspace } from "./academicProjectWorkspace";
import {
  artifactMetaLabel,
  artifactSourceLabel,
  assistantReasoningMeta,
  buildDataChartPreview,
  formatFileSize,
  formatPanelValue,
  hostFromUrl,
  normalizeRecommendItems
} from "./appFormatters";

const AGENTS = USER_AGENT_MODES;

const COMPOSER_AGENT_LABELS = {
  chat: "问答",
  ppt: "PPT",
  deep: "深度任务",
  image: "图像",
  "manual-skills": "Skill"
};

const PPT_IMAGE2_SKILL_NAME = "ppt-image2-editable-rebuild";
const PPT_IMAGE2_SKILL_INSTRUCTION = [
  `请优先使用 ${PPT_IMAGE2_SKILL_NAME} 技能逻辑制作 PPT。`,
  "用户可能会输入主题、页数、受众、文档素材、图片素材或本地文件路径。",
  "先理解用户要做的汇报目标，再整理大纲、页面结构和每页重点。",
  "生成 PPT 时，标题、正文、表格、卡片、箭头和结论条尽量保持可编辑；复杂图片、复杂图表和难以稳定复刻的局部视觉可以作为图片元素保留。",
  "不要把整页参考图直接作为最终 PPT 页面。"
].join("\n");
const DEEP_RESEARCH_STYLE_INSTRUCTION = [
  "本轮使用深度任务模式，适合技术调研、方案选型、竞品对比、资料整理和复杂问题拆解。",
  "先把用户问题拆成执行计划，再按计划检索、整理和对比证据，最后输出结构化结果。",
  "回答中区分确定事实、推断结论和仍需补充验证的信息；引用来源时说明证据用途。"
].join("\n");

const COMPOSER_MODE_INTENTS = {
  chat: {
    label: "即时任务执行",
    executionMode: "ReAct",
    route: "意图识别 -> 工具判断 -> 回复生成",
    hint: "展示本轮是否需要调用工具、读取文件、联网搜索或进入技能流程。",
    agents: "对话 Agent / 文件工具 / 额度对账",
    outputs: "直接回答、引用来源、必要的工具记录",
    trace: "任务分析、工具判断、模型调用"
  },
  ppt: {
    label: "PPT 生成流水线",
    executionMode: "PPT Workflow",
    route: "需求澄清 -> 大纲 -> 素材 -> 渲染",
    hint: "不是只切换标签，会按阶段产出大纲、页面结构和可下载文件。",
    agents: "需求澄清 / 内容整理 / PPT 生成",
    outputs: "大纲、页面结构、演示文稿文件",
    trace: "阶段流转、工具调用、产物记录"
  },
  deep: {
    label: "Plan-Execute 深度任务",
    executionMode: "Plan-Execute",
    route: "规划 -> 搜索/文件 -> 报告 -> 反思",
    hint: "复杂任务会展示任务拆解、协作角色、工具调用和必要时的重规划。",
    agents: "任务规划 / 搜索或文件工具 / 报告生成",
    outputs: "结构化报告、证据列表、风险和待确认项",
    trace: "任务分析、计划版本、重规划、诊断"
  },
  image: {
    label: "图像产物生成",
    executionMode: "ReAct",
    route: "提示词整理 -> 图像生成 -> 产物记录",
    hint: "图像生成逻辑保持原样，只统一进入执行过程和产物展示。",
    agents: "提示词整理 / 图像工具 / 产物记录",
    outputs: "图片、提示词、下载和复用记录",
    trace: "参数整理、生成状态、产物落库"
  },
  "manual-skills": {
    label: "技能自动化流程",
    executionMode: "Skill Orchestration",
    route: "技能发现 -> 步骤读取 -> 工具组合执行",
    hint: "第一版支持本地技能发现、读取、执行和产物记录。",
    agents: "Skill 路由 / 本地技能 / 工具运行时",
    outputs: "编排结果、文件或报告产物",
    trace: "技能选择、步骤执行、工具输入输出"
  }
};

const COMPOSER_PLACEHOLDERS = {
  chat: "直接提问，或说明要完成的任务",
  ppt: "例如：做一份 8 页项目汇报 PPT，包含架构和业务闭环",
  deep: "例如：调研某个技术方案，输出对比、结论和依据",
  image: "描述要生成或编辑的图片",
  "manual-skills": "例如：使用 tech-report 生成项目技术报告"
};
const WEB_SEARCH_STYLE_INSTRUCTION = [
  "本轮已开启联网搜索，请围绕用户问题生成合适关键词，必要时检索公开网页。",
  "基于网页来源回答，并区分搜索得到的事实、自己的归纳和仍需进一步确认的信息。"
].join("\n");

const COMPOSER_AGENT_ICONS = {
  chat: MessageCircle,
  ppt: BarChart3,
  deep: Search,
  image: ImagePlus,
  "manual-skills": Settings
};

const EMPTY_MESSAGES = [];

const normalizeUserMessage = normalizeApiMessage;

const DEMO_AUTH_FORM = {
  username: "demo",
  password: "123456",
  nickname: "演示用户",
  email: "demo@example.com"
};


function App() {
  return (
    <Routes>
      <Route
        path={APP_ROUTES.admin}
        element={(
          <Suspense fallback={<div className="route-loading">管理后台加载中…</div>}>
            <AdminDashboard />
          </Suspense>
        )}
      />
      <Route path="/*" element={<AgentWorkspaceApp />} />
    </Routes>
  );
}

function AgentWorkspaceApp() {
  const location = useLocation();
  const navigate = useNavigate();
  const routeWorkspace = workspaceFromPath(location.pathname);
  const [theme, setTheme] = useState(() => getStoredTheme());
  const [auth, setAuth] = useState(() => getUserAuth());
  const [loginOpen, setLoginOpen] = useState(() => !getUserAuth()?.token);
  const [rechargeOpen, setRechargeOpen] = useState(false);
  const [rechargeTab, setRechargeTab] = useState("packages");
  const [groupPreviewPackage, setGroupPreviewPackage] = useState(null);
  const [groupMarketConfig, setGroupMarketConfig] = useState(null);
  const [groupTeamsLoading, setGroupTeamsLoading] = useState(false);
  const [paymentDialog, setPaymentDialog] = useState(null);
  const [modelConfigOpen, setModelConfigOpen] = useState(false);
  const [modelConfig, setModelConfig] = useState(() => getModelConfig());
  const [authMode, setAuthMode] = useState("login");
  const [authForm, setAuthForm] = useState({ username: "", password: "", nickname: "", email: "" });
  const [authError, setAuthError] = useState("");
  const [authLoading, setAuthLoading] = useState(false);
  const [adminForm, setAdminForm] = useState(() => {
    const saved = getAdminAuth();
    return { username: saved?.username || "", password: saved?.password || "" };
  });
  const [chatList, setChatList] = useState([]);
  const [currentChatId, setCurrentChatId] = useState(() => getSessionId());
  const [academicProjects, setAcademicProjects] = useState([]);
  const [activeAcademicProjectId, setActiveAcademicProjectId] = useState("");
  const [academicProjectLoading, setAcademicProjectLoading] = useState(false);
  const [academicProjectError, setAcademicProjectError] = useState("");
  const [inputMessage, setInputMessage] = useState("");
  const [activeWorkspace, setActiveWorkspace] = useState(() => routeWorkspace);
  const [workspaceHistory, setWorkspaceHistory] = useState(() => ({ workspaceId: routeWorkspace, items: [] }));
  const [workspaceHistoryLoading, setWorkspaceHistoryLoading] = useState(false);
  const [workspaceHistoryError, setWorkspaceHistoryError] = useState("");
  const [workspaceRunDetail, setWorkspaceRunDetail] = useState(null);
  const [workspaceRunDetailLoading, setWorkspaceRunDetailLoading] = useState(false);
  const [workspaceRunDetailError, setWorkspaceRunDetailError] = useState("");
  const [imageWorkspaceDraft, setImageWorkspaceDraft] = useState({
    mode: "generate",
    model: getModelConfig().imageModel || "gpt-image-2",
    quality: "auto",
    ratioPreset: "16:9-4k",
    aspectRatio: "16:9",
    size: "3840x2160",
    batchCount: 1,
    maskImageUrlsText: ""
  });
  const [dataWorkspaceDraft, setDataWorkspaceDraft] = useState({
    rowsJson: "",
    columnsText: "",
    modelCodeText: "",
    schemaInfoJson: "",
    businessKnowledge: ""
  });
  const [dataWorkspaceCatalog, setDataWorkspaceCatalog] = useState(null);
  const [dataWorkspaceCatalogLoading, setDataWorkspaceCatalogLoading] = useState(false);
  const [dataWorkspaceCatalogError, setDataWorkspaceCatalogError] = useState("");
  const [knowledgeDocuments, setKnowledgeDocuments] = useState([]);
  const [knowledgeLoading, setKnowledgeLoading] = useState(false);
  const [knowledgeAction, setKnowledgeAction] = useState("");
  const [knowledgeError, setKnowledgeError] = useState("");
  const [activeKnowledgeBaseId, setActiveKnowledgeBaseId] = useState("");
  const [activeKnowledgeDocumentId, setActiveKnowledgeDocumentId] = useState("");
  const [knowledgeFragments, setKnowledgeFragments] = useState([]);
  const [knowledgeFragmentsLoading, setKnowledgeFragmentsLoading] = useState(false);
  const [knowledgeFragmentsError, setKnowledgeFragmentsError] = useState("");
  const [knowledgeWebUrl, setKnowledgeWebUrl] = useState("");
  const [knowledgeFullContent, setKnowledgeFullContent] = useState(null);
  const [selectedAgent, setSelectedAgent] = useState(() => workspaceAgentMode(routeWorkspace));
  const [selectedSkillName, setSelectedSkillName] = useState("");
  const [webSearchEnabled, setWebSearchEnabled] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [runningChatIds, setRunningChatIds] = useState({});
  const [connectionError, setConnectionError] = useState("");
  const [quota, setQuota] = useState(null);
  const [membership, setMembership] = useState(null);
  const [billingPolicy, setBillingPolicy] = useState(null);
  const [quotaFlows, setQuotaFlows] = useState([]);
  const [packages, setPackages] = useState([]);
  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [taskStatusByChat, setTaskStatusByChat] = useState({});
  const [checkpointByChat, setCheckpointByChat] = useState({});
  const [buyingKey, setBuyingKey] = useState("");
  const [toast, setToast] = useState("");
  const [copiedId, setCopiedId] = useState("");
  const [agentCapabilities, setAgentCapabilities] = useState(null);
  const [, setAgentCapabilitiesError] = useState("");
  const messagesContainer = useRef(null);
  const fileInputRef = useRef(null);
  const knowledgeFileInputRef = useRef(null);
  const streamControllersRef = useRef({});
  const selectedFilePreviewUrlRef = useRef("");

  const replaceSelectedFile = useCallback((nextFile) => {
    const nextPreviewUrl = nextFile?.localPreviewUrl || "";
    if (selectedFilePreviewUrlRef.current && selectedFilePreviewUrlRef.current !== nextPreviewUrl) {
      revokeLocalPreviewUrl(selectedFilePreviewUrlRef.current);
    }
    selectedFilePreviewUrlRef.current = nextPreviewUrl;
    setSelectedFile(nextFile);
  }, []);

  const clearSelectedFile = useCallback(() => {
    replaceSelectedFile(null);
  }, [replaceSelectedFile]);

  useEffect(() => () => {
    revokeLocalPreviewUrl(selectedFilePreviewUrlRef.current);
    selectedFilePreviewUrlRef.current = "";
  }, []);

  const currentChat = useMemo(() => chatList.find((item) => item.id === currentChatId), [chatList, currentChatId]);
  const visibleConnectionError = useMemo(() => (
    isOperatorAuthText(connectionError) ? "" : connectionError
  ), [connectionError]);
  const activeAcademicProject = useMemo(() => (
    academicProjects.find((project) => project.projectId === activeAcademicProjectId) || academicProjects[0] || null
  ), [academicProjects, activeAcademicProjectId]);
  const academicProjectWorkspace = useMemo(() => (
    buildAcademicProjectWorkspace(activeAcademicProject)
  ), [activeAcademicProject]);
  const currentWorkspace = useMemo(() => (
    WORKSPACES.find((workspace) => workspace.id === activeWorkspace) || WORKSPACES[0]
  ), [activeWorkspace]);
  const currentWorkspacePage = useMemo(() => (
    buildWorkspacePageModel(currentWorkspace.id, agentCapabilities)
  ), [agentCapabilities, currentWorkspace.id]);
  const currentWorkspaceProfile = currentWorkspacePage.profile;
  const showComposerWorkspaceSettings = currentWorkspace.id === "image";
  const backendText = auth?.token ? `已登录：${auth.nickname || auth.username || auth.userId}` : "未登录";
  const currentTaskStatus = taskStatusByChat[currentChatId] || {};
  const isSending = Boolean(runningChatIds[currentChatId]);
  const canResumeCurrentChat = Boolean((currentTaskStatus.stopped || currentChat?.stopped) && !isSending);
  const canUseFile = workspaceAcceptsFile(currentWorkspace.id, selectedAgent);
  const academicProjectContextEnabled = currentWorkspace.id === "agent" && ["deep", "file"].includes(selectedAgent);
  const activeAcademicProjectForRequest = academicProjectContextEnabled ? activeAcademicProject : null;
  const showAcademicProjectPanel = academicProjectContextEnabled && (academicProjects.length > 0 || activeAcademicProject);
  const manualSkills = useMemo(() => (
    Array.isArray(agentCapabilities?.manualSkills) ? agentCapabilities.manualSkills : []
  ), [agentCapabilities]);
  const selectedManualSkill = useMemo(() => (
    manualSkills.find((skill) => skill.name === selectedSkillName) || null
  ), [manualSkills, selectedSkillName]);
  const selectedManualSkillHelp = selectedManualSkill
    ? (selectedManualSkill.description || `${selectedManualSkill.name || "当前 Skill"} 已选中，请输入目标、素材路径和约束。`)
    : "选择“自动”时，系统会根据任务内容匹配合适的 Skill；选择具体 Skill 后，这里会显示该 Skill 的说明。";
  const selectedModeIntent = COMPOSER_MODE_INTENTS[selectedAgent] || COMPOSER_MODE_INTENTS.chat;
  const composerPlaceholder = webSearchEnabled
    ? "输入要联网查证的问题"
    : COMPOSER_PLACEHOLDERS[selectedAgent] || COMPOSER_PLACEHOLDERS.chat;
  const tradeWorkspaceSummary = useMemo(() => (
    summarizeTradeWorkspace({ quota, flows: quotaFlows, orders })
  ), [quota, quotaFlows, orders]);
  const knowledgeBaseCatalog = useMemo(() => (
    buildKnowledgeBaseCatalog(knowledgeDocuments)
  ), [knowledgeDocuments]);
  const visibleKnowledgeDocuments = useMemo(() => {
    if (!activeKnowledgeBaseId) return knowledgeDocuments;
    return knowledgeDocuments.filter((doc) => knowledgeBaseCatalogKey(doc) === activeKnowledgeBaseId);
  }, [activeKnowledgeBaseId, knowledgeDocuments]);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    if (activeKnowledgeBaseId && !knowledgeBaseCatalog.some((item) => item.id === activeKnowledgeBaseId)) {
      setActiveKnowledgeBaseId("");
    }
  }, [activeKnowledgeBaseId, knowledgeBaseCatalog]);

  useEffect(() => {
    if (!selectedSkillName) return;
    if (!manualSkills.some((skill) => skill.name === selectedSkillName)) {
      setSelectedSkillName("");
    }
  }, [manualSkills, selectedSkillName]);

  useEffect(() => {
    setActiveWorkspace(routeWorkspace);
    setSelectedAgent(workspaceAgentMode(routeWorkspace));
    const targetPath = workspacePath(routeWorkspace);
    if (location.pathname !== targetPath) {
      navigate(targetPath, { replace: true });
    }
  }, [location.pathname, navigate, routeWorkspace]);

  const toggleTheme = () => {
    setTheme((prev) => applyTheme(nextTheme(prev)));
  };


  const ensureChat = useCallback((sessionId = currentChatId) => {
    setChatList((prev) => {
      if (prev.some((item) => item.id === sessionId)) return prev;
      return [{ id: sessionId, title: "新对话", messages: EMPTY_MESSAGES, isNew: true }, ...prev];
    });
  }, [currentChatId]);

  const updateChat = useCallback((chatId, updater) => {
    setChatList((prev) => prev.map((chat) => {
      if (chat.id !== chatId) return chat;
      return typeof updater === "function" ? updater(chat) : { ...chat, ...updater };
    }));
  }, []);

  const setChatRunning = useCallback((sessionId, running, extraStatus = {}) => {
    setRunningChatIds((prev) => {
      const next = { ...prev };
      if (running) {
        next[sessionId] = true;
      } else {
        delete next[sessionId];
      }
      return next;
    });
    setTaskStatusByChat((prev) => ({
      ...prev,
      [sessionId]: { ...(prev[sessionId] || {}), ...extraStatus, running }
    }));
  }, []);

  const loadQuota = useCallback(async () => {
    if (!getUserAuth()?.token) return;
    const res = await getQuotaSummary(20);
    if (res.code === "0000") {
      setQuota(res.data?.account || null);
      setMembership(res.data?.membership || null);
      setBillingPolicy(res.data?.billingPolicy || null);
      setQuotaFlows(res.data?.flows || []);
    }
  }, []);

  const loadModelConfig = useCallback(async () => {
    if (!getUserAuth()?.token) return;
    const previousImageModel = getModelConfig().imageModel || "gpt-image-2";
    const res = await getUserModelConfig();
    if (res.code === "0000") {
      const nextConfig = { ...getModelConfig(), ...(res.data || {}), apiKey: "", textApiKey: "", imageApiKey: "" };
      setModelConfig(nextConfig);
      setImageWorkspaceDraft((prev) => {
        if (prev.model && prev.model !== previousImageModel) {
          return prev;
        }
        return { ...prev, model: nextConfig.imageModel || "gpt-image-2" };
      });
    }
  }, []);

  const loadSessions = useCallback(async () => {
    if (!getUserAuth()?.token) return;
    const res = await queryAcademicSessions(30);
    if (res.code !== "0000") return;
    setChatList((prev) => {
      const previousById = new Map(prev.map((item) => [item.id, item]));
      const current = previousById.get(currentChatId) || {
        id: currentChatId,
        title: "新对话",
        messages: EMPTY_MESSAGES,
        isNew: true
      };
      const remote = (res.data || []).map((item) => ({
        id: item.sessionId,
        title: item.title || "任务会话",
        lastMessage: item.lastMessage || "",
        messages: previousById.get(item.sessionId)?.messages || EMPTY_MESSAGES,
        isNew: false
      }));
      const remoteIds = new Set(remote.map((item) => item.id));
      const localOnly = prev.filter((item) => !remoteIds.has(item.id));
      const merged = [current, ...localOnly, ...remote].filter((item, index, list) => list.findIndex((other) => other.id === item.id) === index);
      return merged;
    });
  }, [currentChatId]);

  const loadAcademicProjects = useCallback(async () => {
    if (!getUserAuth()?.token) return;
    setAcademicProjectLoading(true);
    setAcademicProjectError("");
    try {
      const res = await queryAcademicProjects(20);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "任务项目读取失败"));
      }
      const projects = res.data || [];
      setAcademicProjects(projects);
      setActiveAcademicProjectId((prev) => (
        projects.some((project) => project.projectId === prev) ? prev : projects[0]?.projectId || ""
      ));
    } catch (error) {
      setAcademicProjectError(normalizeUserMessage(error.message, "任务项目读取失败"));
    } finally {
      setAcademicProjectLoading(false);
    }
  }, []);

  const createDefaultAcademicProject = async () => {
    if (!auth?.token) {
      setLoginOpen(true);
      return null;
    }
    setAcademicProjectLoading(true);
    setAcademicProjectError("");
    try {
      const res = await createAcademicProject({
        title: "熊博士Agent 项目",
        researchQuestion: "请描述任务目标",
        targetVenue: "待定",
        writingStatus: "DRAFTING",
        progressNote: "已创建项目，可继续上传材料、补充资料并生成阶段性结果"
      });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "任务项目创建失败"));
      }
      const project = res.data;
      setAcademicProjects((prev) => [project, ...prev.filter((item) => item.projectId !== project.projectId)]);
      setActiveAcademicProjectId(project.projectId);
      setToast("任务项目已创建");
      return project;
    } catch (error) {
      setAcademicProjectError(normalizeUserMessage(error.message, "任务项目创建失败"));
      return null;
    } finally {
      setAcademicProjectLoading(false);
    }
  };

  const applyPendingAcademicPatch = async (patch) => {
    if (!activeAcademicProject?.projectId || !patch?.patchId) return;
    setAcademicProjectLoading(true);
    setAcademicProjectError("");
    try {
      const res = await applyAcademicProjectPatch(activeAcademicProject.projectId, patch.patchId);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "补丁确认失败"));
      }
      const project = res.data;
      setAcademicProjects((prev) => [project, ...prev.filter((item) => item.projectId !== project.projectId)]);
      setActiveAcademicProjectId(project.projectId);
      setToast("补丁已确认并应用");
    } catch (error) {
      setAcademicProjectError(normalizeUserMessage(error.message, "补丁确认失败"));
    } finally {
      setAcademicProjectLoading(false);
    }
  };

  const loadPackages = useCallback(async () => {
    const res = await queryQuotaPackages("", 20);
    if (res.code === "0000") {
      setPackages(res.data?.packages || []);
    }
  }, []);

  const loadAgentCapabilities = useCallback(async () => {
    const res = await queryAgentCapabilities();
    if (apiSucceeded(res)) {
      setAgentCapabilities(res.data || {});
      setAgentCapabilitiesError("");
      return;
    }
    setAgentCapabilitiesError(normalizeUserMessage(res.message || res.info, "能力状态读取失败"));
  }, []);

  const loadOrders = useCallback(async () => {
    if (!getUserAuth()?.token) return;
    setOrdersLoading(true);
    try {
      const res = await queryUserOrderList({ pageSize: 20 });
      if (res.code === "0000") {
        setOrders(res.data?.orderList || []);
      }
    } finally {
      setOrdersLoading(false);
    }
  }, []);

  const loadWorkspaceHistory = useCallback(async (workspaceId = activeWorkspace) => {
    const targetWorkspaceId = workspaceSupportsHistory(workspaceId) ? workspaceId : "";
    if (!targetWorkspaceId || !getUserAuth()?.token) {
      setWorkspaceHistory({ workspaceId, items: [] });
      setWorkspaceHistoryError("");
      setWorkspaceHistoryLoading(false);
      setWorkspaceRunDetail(null);
      setWorkspaceRunDetailLoading(false);
      setWorkspaceRunDetailError("");
      return;
    }
    setWorkspaceHistoryLoading(true);
    setWorkspaceHistoryError("");
    setWorkspaceRunDetail(null);
    setWorkspaceRunDetailLoading(false);
    setWorkspaceRunDetailError("");
    try {
      if (targetWorkspaceId === "trade") {
        const ordersRes = await queryUserOrderList({ pageSize: 8 });
        if (!apiSucceeded(ordersRes)) {
          throw new Error(normalizeUserMessage(ordersRes?.info || ordersRes?.message, "工作区历史读取失败"));
        }
        setWorkspaceHistory({
          workspaceId: targetWorkspaceId,
          items: normalizeWorkspaceHistoryItems(targetWorkspaceId, ordersRes.data?.orderList || [], 8)
        });
        return;
      }
      const query = {
        image: queryWorkspaceImageHistory,
        data: queryWorkspaceDataHistory,
        mrag: queryWorkspaceMragHistory
      }[targetWorkspaceId];
      if (!query) {
        throw new Error("工作区历史暂不可用");
      }
      const res = await query({ limit: 8 });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "工作区历史读取失败"));
      }
      const historyItems = targetWorkspaceId === "image" && Array.isArray(res.data?.batches) && res.data.batches.length
          ? res.data.batches
          : res.data?.items || [];
      setWorkspaceHistory({
        workspaceId: targetWorkspaceId,
        items: normalizeWorkspaceHistoryItems(targetWorkspaceId, historyItems, 8)
      });
    } catch (error) {
      setWorkspaceHistory({ workspaceId: targetWorkspaceId, items: [] });
      setWorkspaceHistoryError(normalizeUserMessage(error.message, "工作区历史读取失败"));
    } finally {
      setWorkspaceHistoryLoading(false);
    }
  }, [activeWorkspace]);

  const loadDataWorkspaceCatalog = useCallback(async () => {
    if (!getUserAuth()?.token) {
      setDataWorkspaceCatalog(null);
      setDataWorkspaceCatalogError("");
      setDataWorkspaceCatalogLoading(false);
      return;
    }
    setDataWorkspaceCatalogLoading(true);
    setDataWorkspaceCatalogError("");
    try {
      const res = await queryWorkspaceDataCatalog();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "数据模型目录读取失败"));
      }
      const catalog = res.data || null;
      setDataWorkspaceCatalog(catalog);
      setDataWorkspaceDraft((prev) => {
        if (prev.modelCodeText || prev.schemaInfoJson || prev.columnsText || prev.businessKnowledge) {
          return prev;
        }
        return { ...prev, ...buildWorkspaceDataCatalogDraft(catalog) };
      });
    } catch (error) {
      setDataWorkspaceCatalog(null);
      setDataWorkspaceCatalogError(normalizeUserMessage(error.message, "数据模型目录读取失败"));
    } finally {
      setDataWorkspaceCatalogLoading(false);
    }
  }, []);

  const loadKnowledgeDocuments = useCallback(async () => {
    const adminAuth = getAdminAuth();
    if (!adminAuth?.username || !adminAuth?.password) {
      setKnowledgeDocuments([]);
      setActiveKnowledgeDocumentId("");
      setKnowledgeFragments([]);
      setKnowledgeFullContent(null);
      setKnowledgeError("");
      setKnowledgeLoading(false);
      return;
    }
    setKnowledgeLoading(true);
    setKnowledgeError("");
    try {
      const res = await getKnowledgeDocuments();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "知识文档读取失败"));
      }
      const docs = res.data || [];
      setKnowledgeDocuments(docs);
      setActiveKnowledgeDocumentId((prev) => (
        docs.some((doc) => doc.documentId === prev) ? prev : ""
      ));
      setKnowledgeFullContent((prev) => (
        prev && docs.some((doc) => doc.documentId === prev.documentId) ? prev : null
      ));
    } catch (error) {
      setKnowledgeError(normalizeUserMessage(error.message, "知识文档读取失败"));
    } finally {
      setKnowledgeLoading(false);
    }
  }, []);

  const loadKnowledgeFragments = useCallback(async (documentId) => {
    if (!documentId) {
      setActiveKnowledgeDocumentId("");
      setKnowledgeFragments([]);
      setKnowledgeFullContent(null);
      setKnowledgeFragmentsError("");
      return;
    }
    const adminAuth = getAdminAuth();
    if (!adminAuth?.username || !adminAuth?.password) {
      setKnowledgeFragmentsError("请先保存后台权限");
      return;
    }
    setActiveKnowledgeDocumentId(documentId);
    setKnowledgeFullContent(null);
    setKnowledgeFragmentsLoading(true);
    setKnowledgeFragmentsError("");
    try {
      const res = await getKnowledgeFragments(documentId);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "知识片段读取失败"));
      }
      setKnowledgeFragments(res.data || []);
    } catch (error) {
      setKnowledgeFragments([]);
      setKnowledgeFragmentsError(normalizeUserMessage(error.message, "知识片段读取失败"));
    } finally {
      setKnowledgeFragmentsLoading(false);
    }
  }, []);

  const runKnowledgeAction = useCallback(async (actionKey, apiCall, successMessage) => {
    const adminAuth = getAdminAuth();
    if (!adminAuth?.username || !adminAuth?.password) {
      setKnowledgeError("请先保存后台权限");
      return;
    }
    setKnowledgeAction(actionKey);
    setKnowledgeError("");
    try {
      const res = await apiCall();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "知识库操作失败"));
      }
      setToast(successMessage);
      await loadKnowledgeDocuments();
    } catch (error) {
      setKnowledgeError(normalizeUserMessage(error.message, "知识库操作失败"));
    } finally {
      setKnowledgeAction("");
    }
  }, [loadKnowledgeDocuments]);

  const toUiMessages = useCallback((items = []) => items.map((item, index) => ({
    id: item.messageId ? `${item.role || "MSG"}_${item.messageId}` : `${item.role || "MSG"}_${index}_${item.createTime || "local"}`,
    recordId: item.messageId || "",
    role: item.role === "USER" ? "user" : "assistant",
    content: item.content || "",
    timeline: [],
    reference: (item.references || item.reference || []).map(toUiReference),
    recommend: normalizeRecommendItems(item.recommend || item.recommends || item.recommendations || []),
    artifacts: (item.artifacts || []).map(toUiArtifact),
    resultPanels: [],
    showTimeline: false,
    showReference: Boolean((item.references || item.reference || []).length)
  })).filter((message) => message.role !== "assistant" || hasAssistantPayload(message)), []);

  const refreshSessionDetail = useCallback(async (sessionId, keepMessageId = "") => {
    if (!getUserAuth()?.token || !sessionId) return;
    const res = await queryAcademicSessionDetail(sessionId);
    if (res.code !== "0000") return;
    let replays = res.data?.replays || [];
    if (!replays.length) {
      const replayRes = await queryAcademicReplay(sessionId).catch(() => null);
      if (replayRes?.code === "0000") {
        replays = replayRes.data || [];
      }
    }
    const replayTimeline = replayEventsToTimeline(replays, normalizeUserMessage);
    const replayArtifacts = replayEventsToArtifacts(replays);
    const replayResultPanels = replayEventsToResultPanels(replays);
    const remoteMessages = attachReplayTimeline(toUiMessages(res.data?.messages || []), replayTimeline, replayArtifacts, replayResultPanels);
    if (!remoteMessages.length) {
      return;
    }
    setChatList((prev) => prev.map((chat) => {
      if (chat.id !== sessionId) return chat;
      const runningMessage = keepMessageId
        ? chat.messages.find((message) => message.id === keepMessageId)
        : null;
      const remoteHasAssistant = remoteMessages.some((message) => message.role === "assistant" && hasAssistantPayload(message));
      const localAssistant = remoteHasAssistant ? null : latestAssistantWithPayload(chat.messages);
      const shouldKeepRunning = runningMessage && hasAssistantPayload(runningMessage);
      return {
        ...chat,
        isNew: false,
        memory: res.data?.memory || chat.memory,
        messages: shouldKeepRunning
          ? [...remoteMessages, runningMessage]
          : localAssistant
            ? [...remoteMessages, localAssistant]
            : remoteMessages
      };
    }));
  }, [toUiMessages]);

  const updateAssistantInChat = useCallback((chatId, messageId, updater) => {
    updateChat(chatId, (chat) => ({
      ...chat,
      messages: chat.messages.map((message) => message.id === messageId ? updater(message) : message)
    }));
  }, [updateChat]);

  const updateAssistant = useCallback((messageId, updater) => {
    updateAssistantInChat(currentChatId, messageId, updater);
  }, [currentChatId, updateAssistantInChat]);

  const appendAssistantTextInChat = useCallback((chatId, messageId, text) => {
    updateAssistantInChat(chatId, messageId, (message) => ({ ...message, content: `${message.content}${text}` }));
  }, [updateAssistantInChat]);

  const closeAssistantTimelineInChat = useCallback((chatId, messageId) => {
    updateAssistantInChat(chatId, messageId, (message) => {
      const hasAttention = (message.timeline || []).some(isTimelineAttentionItem);
      return { ...message, showTimeline: hasAttention };
    });
  }, [updateAssistantInChat]);

  const processStreamEvent = useCallback((chatId, messageId, event) => {
    const data = event.data || {};
    if (event.event === "checkpoint") {
      const continueTraceId = String(data.continueTraceId || "");
      if (!continueTraceId) return;
      const checkpoint = {
        continueTraceId,
        round: Number(data.round || 0) || 0
      };
      setCheckpointByChat((prev) => ({ ...prev, [chatId]: checkpoint }));
      setTaskStatusByChat((prev) => ({
        ...prev,
        [chatId]: { ...(prev[chatId] || {}), ...checkpoint }
      }));
      return;
    }
    if (event.event === "answer_delta") {
      appendAssistantTextInChat(chatId, messageId, data.content || "");
      return;
    }
    if (["task_analysis", "mode_selection", "agent_routing", "run_start", "project_context", "plan_delta", "replan_delta", "flow_delta", "tool_call", "tool_result", "llm_delta", "diagnosis_delta", "run_done", "run_error", "quota_delta", "usage_metric"].includes(event.event)) {
      const timelineItem = streamEventToTimelineItem(event, normalizeUserMessage);
      const artifacts = eventArtifacts(event);
      const resultPanels = event.event === "tool_result" ? toolResultPanels(event) : [];
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        timeline: mergeTimelineEvent(message.timeline, timelineItem),
        artifacts: artifacts.length ? mergeArtifacts(message.artifacts, artifacts) : message.artifacts,
        resultPanels: resultPanels.length ? mergeResultPanels(message.resultPanels, resultPanels) : message.resultPanels,
        showTimeline: true
      }));
      if (event.event === "quota_delta") {
        setQuota(data);
      }
      return;
    }
    if (event.event === "task_status") {
      const statusMessage = normalizeUserMessage(data.message || data.stage, "正在处理");
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        timeline: mergeThinking(message.timeline, statusMessage)
      }));
      return;
    }
    if (event.event === "reference_delta") {
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        reference: [...(message.reference || []), toUiReference(data)],
        showReference: true
      }));
      return;
    }
    if (event.event === "artifact_delta") {
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        artifacts: mergeArtifacts(message.artifacts, [toUiArtifact(data)])
      }));
      return;
    }
    if (event.event === "recommend_delta") {
      const items = normalizeRecommendItems(data.items ?? data.content ?? data);
      if (!items.length) return;
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        recommend: [...(message.recommend || []), ...items]
      }));
      return;
    }
    if (event.event === "error") {
      const errorMessage = normalizeUserMessage(data.message, "处理失败");
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        timeline: [...(message.timeline || []), { type: "error", message: errorMessage }]
      }));
      appendAssistantTextInChat(chatId, messageId, `\n\n${errorMessage}`);
    }
  }, [appendAssistantTextInChat, updateAssistantInChat]);

  const refreshTaskStatus = useCallback(async (sessionId) => {
    if (!getUserAuth()?.token || !sessionId) return null;
    const res = await queryAcademicTaskStatus(sessionId);
    if (res.code !== "0000") return null;
    const status = res.data || {};
    setTaskStatusByChat((prev) => ({ ...prev, [sessionId]: status }));
    return status;
  }, []);

  const attachRunningStream = useCallback((sessionId) => {
    if (!sessionId || streamControllersRef.current[sessionId]) return;
    const assistantId = createRuntimeId("A_ATTACH_");
    const assistantMsg = {
      id: assistantId,
      role: "assistant",
      content: "",
      timeline: [{ type: "thinking", content: "正在接回后台任务..." }],
      reference: [],
      recommend: [],
      artifacts: [],
      resultPanels: [],
      showTimeline: true,
      showReference: false
    };
    updateChat(sessionId, (chat) => ({
      ...chat,
      isNew: false,
      messages: chat.messages.some((message) => message.id === assistantId)
        ? chat.messages
        : [...chat.messages, assistantMsg]
    }));
    refreshSessionDetail(sessionId, assistantId).catch(() => {});
    setChatRunning(sessionId, true, { stopped: false });
    streamControllersRef.current[sessionId] = requestAcademicAttachStream(
      sessionId,
      (event) => processStreamEvent(sessionId, assistantId, event),
      () => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        closeAssistantTimelineInChat(sessionId, assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        refreshTaskStatus(sessionId).catch(() => {});
        refreshSessionDetail(sessionId).catch(() => {});
      },
      (error) => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        appendAssistantTextInChat(sessionId, assistantId, `\n\n接回后台任务失败：${normalizeUserMessage(error.message, "服务暂不可用")}`);
        refreshTaskStatus(sessionId).catch(() => {});
      }
    );
  }, [
    appendAssistantTextInChat,
    closeAssistantTimelineInChat,
    loadQuota,
    loadSessions,
    processStreamEvent,
    refreshSessionDetail,
    refreshTaskStatus,
    setChatRunning,
    updateChat
  ]);

  const loadTaskStatus = useCallback(async (sessionId = currentChatId) => {
    const status = await refreshTaskStatus(sessionId);
    if (status?.running && !streamControllersRef.current[sessionId]) {
      attachRunningStream(sessionId);
    }
  }, [attachRunningStream, currentChatId, refreshTaskStatus]);

  const openWorkspaceHistoryItem = useCallback(async (item) => {
    const sessionId = item?.sessionId;
    if (item?.workspaceId === "trade" && !sessionId) {
      setConnectionError("");
      return;
    }
    if (!sessionId) return;
    setWorkspaceRunDetail({ item, detail: null });
    setWorkspaceRunDetailLoading(false);
    setWorkspaceRunDetailError("");
    localStorage.setItem("agentGroupSessionId", sessionId);
    setCurrentChatId(sessionId);
    setChatList((prev) => {
      if (prev.some((chat) => chat.id === sessionId)) {
        return prev;
      }
      return [{
        id: sessionId,
        title: item.title || item.artifactName || "历史任务",
        lastMessage: item.summary || "",
        messages: EMPTY_MESSAGES,
        isNew: false
      }, ...prev];
    });
    try {
      await refreshSessionDetail(sessionId);
      await loadTaskStatus(sessionId);
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "会话历史读取失败"));
    }
    if (item?.runId) {
      setWorkspaceRunDetailLoading(true);
      try {
        const res = await queryAcademicRunDetail(item.runId);
        if (!apiSucceeded(res)) {
          throw new Error(normalizeUserMessage(res?.info || res?.message, "运行详情读取失败"));
        }
        setWorkspaceRunDetail({ item, detail: res.data || null });
      } catch (error) {
        setWorkspaceRunDetail({ item, detail: null });
        setWorkspaceRunDetailError(normalizeUserMessage(error.message, "运行详情读取失败"));
      } finally {
        setWorkspaceRunDetailLoading(false);
      }
    }
  }, [loadTaskStatus, refreshSessionDetail]);

  const refreshRecharge = useCallback(async () => {
    await Promise.all([
      loadQuota().catch(() => {}),
      loadOrders().catch(() => {}),
      loadPackages().catch((error) => console.warn("额度包读取失败", error))
    ]);
    if (activeWorkspace === "trade") {
      await loadWorkspaceHistory("trade").catch(() => {});
    }
  }, [activeWorkspace, loadOrders, loadPackages, loadQuota, loadWorkspaceHistory]);

  useEffect(() => {
    ensureChat(currentChatId);
  }, [currentChatId, ensureChat]);

  useEffect(() => {
    loadPackages().catch((error) => console.warn("额度包读取失败", error));
  }, [loadPackages]);

  useEffect(() => {
    if (!auth?.token) {
      setAgentCapabilities(null);
      setAgentCapabilitiesError("");
      return;
    }
    loadAgentCapabilities().catch((error) => {
      console.warn("Agent 能力读取失败", error);
      setAgentCapabilitiesError("能力状态读取失败");
    });
  }, [auth?.token, loadAgentCapabilities]);

  useEffect(() => {
    if (!auth?.token) return;
    loadQuota().catch((error) => setConnectionError(normalizeUserMessage(error.message, "额度读取失败")));
    loadModelConfig().catch(() => {});
    loadSessions().catch(() => {});
    loadAcademicProjects().catch(() => {});
    loadOrders().catch(() => {});
  }, [auth, loadAcademicProjects, loadModelConfig, loadOrders, loadQuota, loadSessions]);

  useEffect(() => {
    loadWorkspaceHistory(activeWorkspace).catch(() => {});
  }, [activeWorkspace, auth?.token, loadWorkspaceHistory]);

  useEffect(() => {
    if (activeWorkspace !== "data") return;
    loadDataWorkspaceCatalog().catch(() => {});
  }, [activeWorkspace, auth?.token, loadDataWorkspaceCatalog]);

  useEffect(() => {
    if (activeWorkspace !== "mrag") return;
    loadKnowledgeDocuments().catch(() => {});
  }, [activeWorkspace, loadKnowledgeDocuments]);

  useEffect(() => {
    if (!auth?.token) return;
    loadTaskStatus(currentChatId).catch(() => {});
  }, [auth, currentChatId, loadTaskStatus]);

  useEffect(() => {
    if (!messagesContainer.current) return;
    messagesContainer.current.scrollTop = messagesContainer.current.scrollHeight;
  }, [chatList, currentChatId, isSending]);

  const createNewChat = () => {
    const id = createRuntimeId("AS");
    localStorage.setItem("agentGroupSessionId", id);
    setCurrentChatId(id);
    clearSelectedFile();
    setChatList((prev) => [{ id, title: "新对话", messages: EMPTY_MESSAGES, isNew: true }, ...prev]);
  };

  const deleteChat = async (chatId) => {
    const target = chatList.find((item) => item.id === chatId);
    if (!target) return;
    setConnectionError("");
    try {
      if (auth?.token && !target.isNew) {
        if (runningChatIds[chatId] || taskStatusByChat[chatId]?.running) {
          streamControllersRef.current[chatId]?.abort();
          delete streamControllersRef.current[chatId];
          await stopAcademicStream(chatId);
        }
        const res = await deleteAcademicSession(chatId);
        if (res.code !== "0000") throw new Error(normalizeUserMessage(res.info, "会话删除失败"));
      }
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "会话删除失败"));
      return;
    }
    const nextList = chatList.filter((item) => item.id !== chatId);
    if (currentChatId === chatId) {
      const next = nextList[0];
      if (next) {
        setCurrentChatId(next.id);
        localStorage.setItem("agentGroupSessionId", next.id);
      } else {
        const id = createRuntimeId("AS");
        localStorage.setItem("agentGroupSessionId", id);
        setCurrentChatId(id);
        nextList.push({ id, title: "新对话", messages: EMPTY_MESSAGES, isNew: true });
      }
    }
    setChatList(nextList);
    setToast("会话已删除");
  };

  const selectChat = async (chatId) => {
    setCurrentChatId(chatId);
    if (!auth?.token) return;
    const chat = chatList.find((item) => item.id === chatId);
    if (!chat || chat.isNew || chat.messages.length > 0) return;
    try {
      await refreshSessionDetail(chatId);
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "会话详情读取失败"));
    }
    loadTaskStatus(chatId).catch(() => {});
  };

  const selectAgent = (agentId) => {
    setSelectedAgent(agentId);
    if (agentId === "image" || agentId === "data" || agentId === "mrag") {
      const workspace = WORKSPACES.find((item) => item.agentId === agentId);
      if (workspace) {
        setActiveWorkspace(workspace.id);
        const path = workspacePath(workspace.id);
        if (location.pathname !== path) {
          navigate(path);
        }
      }
    } else if (activeWorkspace === "image" || activeWorkspace === "data" || activeWorkspace === "mrag") {
      setActiveWorkspace("agent");
      if (location.pathname !== "/") {
        navigate("/");
      }
    }
  };

  const quickPrompt = (prompt) => {
    setInputMessage(prompt);
  };

  const openTradeOrderRecords = () => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    setRechargeTab("orders");
    setRechargeOpen(true);
    loadOrders().catch(() => {});
    setConnectionError("");
  };

  const openRecharge = () => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    setGroupPreviewPackage(null);
    setGroupMarketConfig(null);
    setRechargeTab("packages");
    setRechargeOpen(true);
    refreshRecharge().catch(() => {});
  };

  const loadGroupMarketConfig = useCallback(async (requestedPkg) => {
    const pkg = requestedPkg ?? groupPreviewPackage;
    if (!pkg || !getUserAuth()?.token) return null;
    setGroupTeamsLoading(true);
    try {
      const userId = auth?.userId || quota?.userId;
      const res = await queryGroupBuyMarketConfig(pkg, userId);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "拼团信息读取失败"));
      }
      setGroupMarketConfig(res.data || null);
      return res.data || null;
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "拼团信息读取失败"));
      return null;
    } finally {
      setGroupTeamsLoading(false);
    }
  }, [auth, groupPreviewPackage, quota]);

  const openGroupPreview = async (pkg) => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    setConnectionError("");
    setGroupPreviewPackage(pkg);
    setGroupMarketConfig(null);
    await loadGroupMarketConfig(pkg);
  };

  const validateAuthForm = (form, mode) => {
    const username = String(form.username || "").trim();
    const password = String(form.password || "");
    if (!username || !password) {
      return "请填写账号和密码";
    }
    if (mode === "register" && password.length < 6) {
      return "密码长度不能少于 6 位";
    }
    const email = String(form.email || "").trim();
    if (mode === "register" && email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return "邮箱格式不正确";
    }
    return "";
  };

  const submitAuth = async (nextForm = authForm, nextMode = authMode) => {
    setAuthError("");
    const validationError = validateAuthForm(nextForm, nextMode);
    if (validationError) {
      setAuthError(validationError);
      return;
    }
    setAuthLoading(true);
    try {
      const payload = {
        ...nextForm,
        username: String(nextForm.username || "").trim(),
        password: String(nextForm.password || ""),
        nickname: String(nextForm.nickname || "").trim(),
        email: String(nextForm.email || "").trim()
      };
      const res = nextMode === "login" ? await login(payload.username, payload.password) : await register(payload);
      if (apiSucceeded(res) && res.data?.token) {
        setAuth(res.data);
        setLoginOpen(false);
        setToast(nextMode === "login" ? "登录成功" : "注册成功，已登录");
      } else {
        setAuthError(normalizeUserMessage(res.info || res.message, nextMode === "login" ? "登录失败" : "注册失败"));
      }
    } catch (error) {
      setAuthError(normalizeUserMessage(error.message, nextMode === "login" ? "登录失败" : "注册失败"));
    } finally {
      setAuthLoading(false);
    }
  };

  const handleAuthSubmit = async (event) => {
    event.preventDefault();
    await submitAuth();
  };

  const handleDemoAuth = async () => {
    setAuthMode("login");
    setAuthForm(DEMO_AUTH_FORM);
    await submitAuth(DEMO_AUTH_FORM, "login");
  };

  const handleLogout = async () => {
    Object.values(streamControllersRef.current).forEach((controller) => controller?.abort?.());
    streamControllersRef.current = {};
    setRunningChatIds({});
    await logout();
    setAuth(null);
    setQuota(null);
    setQuotaFlows([]);
    setAcademicProjects([]);
    setActiveAcademicProjectId("");
    setLoginOpen(true);
    setToast("已退出登录");
  };

  const handleFileSelect = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    const imageFile = isImageUpload(file);
    const localPreviewUrl = createLocalPreviewUrl(file);
    replaceSelectedFile({
      name: file.name,
      fileType: file.type || "",
      contentType: file.type || "",
      size: file.size,
      status: "uploading",
      isImage: imageFile,
      previewUrl: localPreviewUrl,
      localPreviewUrl
    });
    setIsUploading(true);
    try {
      const res = await uploadAcademicFile(file, currentChatId);
      if (res.code === "0000") {
        const parsedFile = {
          fileId: res.data.fileId,
          name: res.data.fileName,
          fileType: res.data.fileType,
          contentType: file.type || "",
          size: res.data.fileSize,
          summary: res.data.summary,
          status: "parsed",
          isImage: imageFile,
          previewUrl: localPreviewUrl,
          localPreviewUrl
        };
        replaceSelectedFile(parsedFile);
        if (imageFile) {
          setInputMessage((prev) => (prev.trim() ? prev : "这个图上是什么内容呢"));
        }
        const project = academicProjectContextEnabled ? (activeAcademicProject || await createDefaultAcademicProject()) : null;
        if (project?.projectId) {
          const bindRes = await bindAcademicProjectFile(project.projectId, {
            fileId: parsedFile.fileId,
            fileName: parsedFile.name,
            fileType: parsedFile.fileType,
            folderType: selectedAgent === "file" ? "draftManuscripts" : "coreReferences",
            summary: parsedFile.summary,
            contentPreview: parsedFile.summary
          });
          if (apiSucceeded(bindRes)) {
            setAcademicProjects((prev) => [bindRes.data, ...prev.filter((item) => item.projectId !== bindRes.data.projectId)]);
            setActiveAcademicProjectId(bindRes.data.projectId);
          }
        }
        setToast("文件解析完成");
      } else {
        setConnectionError(normalizeUserMessage(res.info, "文件上传失败"));
        clearSelectedFile();
      }
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "文件上传失败"));
      clearSelectedFile();
    } finally {
      setIsUploading(false);
    }
  };

  const sendMessage = async (options = {}) => {
    const text = String(options.text ?? inputMessage).trim();
    const sessionId = currentChatId;
    const file = Object.prototype.hasOwnProperty.call(options, "file") ? options.file : selectedFile;
    if (runningChatIds[sessionId] || isUploading || (!text && !file)) return;
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    const modelScope = currentWorkspace.id === "image" ? "image" : "text";
    if (!modelConfigReady(modelConfig, modelScope)) {
      setConnectionError(modelScope === "image" ? "请先配置可用的图像模型 API" : "请先配置可用的文本模型 API");
      setModelConfigOpen(true);
      return;
    }
    const fileIsImage = file?.isImage || isImageArtifact({ fileName: file?.name, contentType: file?.contentType || file?.fileType, previewUrl: file?.previewUrl });
    const displayQuestion = text || (file ? (fileIsImage ? "这个图上是什么内容呢" : "请分析这个文件") : "");
    const skillInstruction = selectedAgent === "manual-skills" && selectedSkillName
      ? `请使用 ${selectedSkillName} 技能`
      : "";
    const pptInstruction = selectedAgent === "ppt" ? PPT_IMAGE2_SKILL_INSTRUCTION : "";
    const deepResearchInstruction = selectedAgent === "deep" ? DEEP_RESEARCH_STYLE_INSTRUCTION : "";
    const webSearchInstruction = webSearchEnabled ? WEB_SEARCH_STYLE_INSTRUCTION : "";
    const instructionPrefix = [skillInstruction, pptInstruction, deepResearchInstruction, webSearchInstruction].filter(Boolean).join("\n\n");
    const streamDraft = buildWorkspaceStreamDraft({
      workspaceId: currentWorkspace.id,
      agentId: selectedAgent,
      question: instructionPrefix ? `${instructionPrefix}\n\n${displayQuestion}` : displayQuestion,
      fileId: file?.fileId || "",
      imageUrl: file?.imageUrl || "",
      imageName: file?.name || ""
    });
    let dataWorkspacePayload = null;
    if (currentWorkspace.id === "data" && !file) {
      try {
        dataWorkspacePayload = buildWorkspaceDataRunPayload({
          sessionId,
          question: streamDraft.question,
          ...dataWorkspaceDraft
        });
      } catch (error) {
        setConnectionError(normalizeUserMessage(error.message, "数据工作区参数格式不正确"));
        return;
      }
    }
    let imageWorkspacePayload = null;
    if (currentWorkspace.id === "image") {
      const sourceFileIds = file?.fileId && isImageArtifact({ fileName: file.name, contentType: file.fileType })
        ? [file.fileId]
        : [];
      const sourceImageUrls = streamDraft.imageUrl ? [streamDraft.imageUrl] : [];
      try {
        imageWorkspacePayload = buildWorkspaceImageGeneratePayload({
          sessionId,
          prompt: streamDraft.question,
          ...imageWorkspaceDraft,
          sourceFileIds,
          sourceImageUrls,
          maskImageUrls: imageWorkspaceDraft.maskImageUrlsText
        });
      } catch (error) {
        setConnectionError(normalizeUserMessage(error.message, "图像工作区参数格式不正确"));
        return;
      }
    }

    if (options.rollbackToMessageId) {
      const chat = chatList.find((item) => item.id === sessionId);
      const anchorMessage = chat?.messages?.find((message) => message.id === options.rollbackToMessageId);
      if (anchorMessage?.recordId) {
        try {
          const rollbackRes = await rollbackAcademicSession(sessionId, anchorMessage.recordId);
          if (!apiSucceeded(rollbackRes)) {
            throw new Error(normalizeUserMessage(rollbackRes?.info || rollbackRes?.message, "回溯失败"));
          }
        } catch (error) {
          setConnectionError(normalizeUserMessage(error.message, "回溯失败，请稍后重试"));
          return;
        }
      }
    }

    const userMsg = {
      id: createRuntimeId("U"),
      recordId: "",
      role: "user",
      content: displayQuestion,
      file: Boolean(file),
      fileName: file?.name || ""
    };
    const assistantId = createRuntimeId("A");
    const assistantMsg = {
      id: assistantId,
      recordId: "",
      role: "assistant",
      content: "",
      timeline: [{ type: "thinking", content: "正在理解任务并准备工具..." }],
      reference: [],
      recommend: [],
      artifacts: [],
      resultPanels: [],
      showTimeline: true,
      showReference: false
    };

    updateChat(sessionId, (chat) => {
      let baseMessages = chat.messages;
      if (options.rollbackToMessageId) {
        const rollbackIndex = chat.messages.findIndex((message) => message.id === options.rollbackToMessageId);
        if (rollbackIndex >= 0) {
          baseMessages = chat.messages.slice(0, rollbackIndex);
        }
      }
      return {
        ...chat,
        title: chat.isNew && text ? `${text.slice(0, 20)}${text.length > 20 ? "..." : ""}` : chat.title,
        isNew: false,
        stopped: false,
        messages: [...baseMessages, userMsg, assistantMsg]
      };
    });
    setChatRunning(sessionId, true, { stopped: false });
    setInputMessage("");
    setConnectionError("");

    if (currentWorkspace.id === "image") {
      const invocationId = `workspace-image-${assistantId}`;
      const controller = {
        aborted: false,
        abort() {
          this.aborted = true;
        }
      };
      streamControllersRef.current[sessionId] = controller;
      processStreamEvent(sessionId, assistantId, {
        event: "run_start",
        data: { taskType: "image", model: "workspace-image" }
      });
      processStreamEvent(sessionId, assistantId, {
        event: "tool_call",
        data: {
          invocationId,
          toolName: "image_generation",
          action: imageWorkspacePayload.mode,
          argumentsJson: `${streamDraft.question} · ${imageWorkspacePayload.size} · ${imageWorkspacePayload.batchCount}`
        }
      });
      generateWorkspaceImage(imageWorkspacePayload)
        .then((res) => {
          if (controller.aborted) return;
          if (!apiSucceeded(res)) {
            throw new Error(normalizeUserMessage(res?.info, "后端绘图模型异常，请检查图像模型配置后重试"));
          }
          const data = res.data || {};
          const summary = data.summary || data.title || "图片生成完成";
          const artifacts = workspaceImageArtifacts(data);
          processStreamEvent(sessionId, assistantId, workspaceImageToolResultEvent(data, invocationId));
          updateAssistantInChat(sessionId, assistantId, (message) => ({
            ...message,
            content: summary,
            artifacts: artifacts.length ? mergeArtifacts(message.artifacts, artifacts) : message.artifacts,
            showTimeline: true
          }));
          processStreamEvent(sessionId, assistantId, {
            event: "run_done",
            data: {}
          });
        })
        .catch((error) => {
          if (controller.aborted) return;
          const message = normalizeUserMessage(error.message, "后端绘图模型异常，请检查图像模型配置后重试");
          processStreamEvent(sessionId, assistantId, {
            event: "run_error",
            data: { message }
          });
          appendAssistantTextInChat(sessionId, assistantId, `\n\n${message}`);
        })
        .finally(() => {
          if (streamControllersRef.current[sessionId] === controller) {
            delete streamControllersRef.current[sessionId];
          }
          setChatRunning(sessionId, false);
          closeAssistantTimelineInChat(sessionId, assistantId);
          loadQuota().catch(() => {});
          loadSessions().catch(() => {});
          loadWorkspaceHistory("image").catch(() => {});
          refreshTaskStatus(sessionId).catch(() => {});
          window.setTimeout(() => refreshSessionDetail(sessionId).catch(() => {}), 300);
        });
      return;
    }

    if (currentWorkspace.id === "data" && !file) {
      const controller = {
        aborted: false,
        abort() {
          this.aborted = true;
        }
      };
      streamControllersRef.current[sessionId] = controller;
      processStreamEvent(sessionId, assistantId, {
        event: "run_start",
        data: { taskType: "data", model: "workspace-data" }
      });
      processStreamEvent(sessionId, assistantId, {
        event: "tool_call",
        data: {
          invocationId: `workspace-data-${assistantId}`,
          toolName: "data_workspace",
          action: "run",
          argumentsJson: streamDraft.question
        }
      });
      runWorkspaceData(dataWorkspacePayload)
        .then((res) => {
          if (controller.aborted) return;
          if (!apiSucceeded(res)) {
            throw new Error(normalizeUserMessage(res?.info, "data workspace failed"));
          }
          const data = res.data || {};
          (data.toolResults || []).forEach((result) => {
            processStreamEvent(sessionId, assistantId, workspaceDataToolResultEvent(result));
          });
          processStreamEvent(sessionId, assistantId, {
            event: "tool_result",
            data: {
              invocationId: `workspace-data-${assistantId}`,
              toolName: "data_workspace",
              status: "SUCCESS",
              resultSummary: data.summary || ""
            }
          });
          const missing = (data.missingTools || []).length
            ? `\n\nMissing tools: ${(data.missingTools || []).join(", ")}`
            : "";
          updateAssistantInChat(sessionId, assistantId, (message) => ({
            ...message,
            content: `${data.summary || "data workspace completed"}${missing}`,
            showTimeline: true
          }));
          processStreamEvent(sessionId, assistantId, {
            event: "run_done",
            data: {}
          });
        })
        .catch((error) => {
          if (controller.aborted) return;
          const message = normalizeUserMessage(error.message, "data workspace failed");
          processStreamEvent(sessionId, assistantId, {
            event: "tool_result",
            data: {
              invocationId: `workspace-data-${assistantId}`,
              toolName: "data_workspace",
              status: "FAILED",
              errorMessage: message
            }
          });
          processStreamEvent(sessionId, assistantId, {
            event: "run_error",
            data: { message }
          });
          appendAssistantTextInChat(sessionId, assistantId, `\n\n${message}`);
        })
        .finally(() => {
          if (streamControllersRef.current[sessionId] === controller) {
            delete streamControllersRef.current[sessionId];
          }
          setChatRunning(sessionId, false);
          closeAssistantTimelineInChat(sessionId, assistantId);
          loadQuota().catch(() => {});
          loadSessions().catch(() => {});
          loadWorkspaceHistory("data").catch(() => {});
          refreshTaskStatus(sessionId).catch(() => {});
          window.setTimeout(() => refreshSessionDetail(sessionId).catch(() => {}), 300);
        });
      return;
    }

    if (currentWorkspace.id === "mrag" && !file) {
      const controller = {
        aborted: false,
        abort() {
          this.aborted = true;
        }
      };
      streamControllersRef.current[sessionId] = controller;
      processStreamEvent(sessionId, assistantId, {
        event: "run_start",
        data: { taskType: "mrag", model: "workspace-mrag" }
      });
      processStreamEvent(sessionId, assistantId, {
        event: "tool_call",
        data: {
          invocationId: `workspace-mrag-${assistantId}`,
          toolName: "mrag_workspace",
          action: "run",
          argumentsJson: streamDraft.question
        }
      });
      runWorkspaceMrag({
        sessionId,
        question: streamDraft.question,
        text: streamDraft.question
      })
        .then((res) => {
          if (controller.aborted) return;
          if (!apiSucceeded(res)) {
            throw new Error(normalizeUserMessage(res?.info, "mrag workspace failed"));
          }
          const data = res.data || {};
          (data.toolResults || []).forEach((result) => {
            processStreamEvent(sessionId, assistantId, workspaceMragToolResultEvent(result));
          });
          processStreamEvent(sessionId, assistantId, {
            event: "tool_result",
            data: {
              invocationId: `workspace-mrag-${assistantId}`,
              toolName: "mrag_workspace",
              status: "SUCCESS",
              resultSummary: data.summary || ""
            }
          });
          const missing = (data.missingTools || []).length
            ? `\n\nMissing tools: ${(data.missingTools || []).join(", ")}`
            : "";
          updateAssistantInChat(sessionId, assistantId, (message) => ({
            ...message,
            content: `${data.summary || "mrag workspace completed"}${missing}`,
            showTimeline: true
          }));
          processStreamEvent(sessionId, assistantId, {
            event: "run_done",
            data: {}
          });
        })
        .catch((error) => {
          if (controller.aborted) return;
          const message = normalizeUserMessage(error.message, "mrag workspace failed");
          processStreamEvent(sessionId, assistantId, {
            event: "tool_result",
            data: {
              invocationId: `workspace-mrag-${assistantId}`,
              toolName: "mrag_workspace",
              status: "FAILED",
              errorMessage: message
            }
          });
          processStreamEvent(sessionId, assistantId, {
            event: "run_error",
            data: { message }
          });
          appendAssistantTextInChat(sessionId, assistantId, `\n\n${message}`);
        })
        .finally(() => {
          if (streamControllersRef.current[sessionId] === controller) {
            delete streamControllersRef.current[sessionId];
          }
          setChatRunning(sessionId, false);
          closeAssistantTimelineInChat(sessionId, assistantId);
          loadQuota().catch(() => {});
          loadSessions().catch(() => {});
          loadWorkspaceHistory("mrag").catch(() => {});
          refreshTaskStatus(sessionId).catch(() => {});
          window.setTimeout(() => refreshSessionDetail(sessionId).catch(() => {}), 300);
        });
      return;
    }

    streamControllersRef.current[sessionId] = requestAcademicStream(
      {
        sessionId,
        projectId: activeAcademicProjectForRequest?.projectId || "",
        threadId: sessionId,
        question: streamDraft.question,
        taskType: streamDraft.taskType,
        taskMode: selectedAgent,
        fileId: streamDraft.fileId,
        selectedFileIds: streamDraft.fileId ? [streamDraft.fileId] : [],
        imageUrl: streamDraft.imageUrl,
        imageName: streamDraft.imageName,
        webSearchEnabled,
        modelConfig
      },
      (event) => processStreamEvent(sessionId, assistantId, event),
      () => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        closeAssistantTimelineInChat(sessionId, assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        if (currentWorkspace.id === "trade") {
          loadWorkspaceHistory("trade").catch(() => {});
        }
        refreshTaskStatus(sessionId).catch(() => {});
        window.setTimeout(() => refreshSessionDetail(sessionId).catch(() => {}), 300);
      },
      (error) => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        appendAssistantTextInChat(sessionId, assistantId, `\n\n请求出错：${normalizeUserMessage(error.message, "服务暂不可用")}`);
        refreshTaskStatus(sessionId).catch(() => {});
      }
    );
  };

  const stopMessage = async () => {
    const sessionId = currentChatId;
    streamControllersRef.current[sessionId]?.abort();
    delete streamControllersRef.current[sessionId];
    await stopAcademicStream(sessionId);
    updateChat(sessionId, (chat) => ({ ...chat, stopped: true }));
    setChatRunning(sessionId, false, { stopped: true, resumable: true });
  };

  const resumeMessage = () => {
    const sessionId = currentChatId;
    if (runningChatIds[sessionId] || !auth?.token) return;
    const modelScope = currentWorkspace.id === "image" ? "image" : "text";
    if (!modelConfigReady(modelConfig, modelScope)) {
      setConnectionError(modelScope === "image" ? "请先配置可用的图像模型 API" : "请先配置可用的文本模型 API");
      setModelConfigOpen(true);
      return;
    }
    const assistantId = createRuntimeId("A");
    const assistantMsg = {
      id: assistantId,
      role: "assistant",
      content: "",
      timeline: [{ type: "thinking", content: "正在从上次停止处继续生成..." }],
      reference: [],
      recommend: [],
      artifacts: [],
      resultPanels: [],
      showTimeline: true,
      showReference: false
    };
    updateChat(sessionId, (chat) => ({
      ...chat,
      stopped: false,
      messages: [...chat.messages, assistantMsg]
    }));
    setChatRunning(sessionId, true, { stopped: false });
    setConnectionError("");
    streamControllersRef.current[sessionId] = requestAcademicResumeStream(
      sessionId,
      modelConfig,
      webSearchEnabled,
      checkpointByChat[sessionId]?.continueTraceId
        || taskStatusByChat[sessionId]?.continueTraceId
        || "",
      (event) => processStreamEvent(sessionId, assistantId, event),
      () => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        closeAssistantTimelineInChat(sessionId, assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        refreshTaskStatus(sessionId).catch(() => {});
        window.setTimeout(() => refreshSessionDetail(sessionId).catch(() => {}), 300);
      },
      (error) => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        appendAssistantTextInChat(sessionId, assistantId, `\n\n继续生成失败：${normalizeUserMessage(error.message, "服务暂不可用")}`);
        refreshTaskStatus(sessionId).catch(() => {});
      }
    );
  };

  const toggleTimeline = (msgId) => {
    updateAssistant(msgId, (message) => ({ ...message, showTimeline: !message.showTimeline }));
  };

  const toggleReference = (msgId) => {
    updateAssistant(msgId, (message) => ({ ...message, showReference: !message.showReference }));
  };

  const copyMessage = async (message) => {
    await navigator.clipboard.writeText(message.content || "");
    setCopiedId(message.id);
    setTimeout(() => setCopiedId(""), 1400);
  };

  const hasRetryQuota = () => {
    const accountQuota = Number(quota?.quotaBalance || 0);
    const memberQuota = membership?.active ? Number(membership?.remainingMonthlyQuota || 0) : 0;
    return accountQuota + memberQuota > 0;
  };

  const editAndRetryUserMessage = (message, nextContent) => {
    const nextText = String(nextContent || "").trim();
    if (!nextText || isSending) return;
    if (message.recordId && !hasRetryQuota()) {
      setConnectionError("额度不足，先购买额度包或开通会员后再重试");
      return;
    }
    sendMessage({
      text: nextText,
      file: null,
      rollbackToMessageId: message.id
    });
  };

  const retryAssistantMessage = (message) => {
    if (isSending) return;
    const chat = chatList.find((item) => item.id === currentChatId);
    const messageIndex = chat?.messages?.findIndex((item) => item.id === message.id) ?? -1;
    if (!chat || messageIndex < 0) return;
    const userMessage = [...chat.messages.slice(0, messageIndex)].reverse().find((item) => item.role === "user");
    if (!userMessage?.content) return;
    if (userMessage.recordId && !hasRetryQuota()) {
      setConnectionError("额度不足，先购买额度包或开通会员后再重试");
      return;
    }
    sendMessage({
      text: userMessage.content,
      file: null,
      rollbackToMessageId: userMessage.id
    });
  };

  const handleArtifactDownload = async (artifact) => {
    try {
      await downloadAcademicArtifact(artifact.downloadUrl, artifact.fileName || artifact.title || "artifact");
      setToast("文件已下载");
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "文件下载失败"));
    }
  };

  const handleSaveAdminAuth = () => {
    saveAdminAuth(adminForm.username, adminForm.password);
    setToast("后台权限已保存");
  };

  const handleSaveModelConfig = (nextConfig) => {
    saveModelConfig(nextConfig)
      .then((res) => {
        if (!apiSucceeded(res)) {
          throw new Error(normalizeUserMessage(res.info || res.message, "模型配置保存失败"));
        }
        const nextSavedConfig = { ...getModelConfig(), ...(res.data || {}), apiKey: "", textApiKey: "", imageApiKey: "" };
        setModelConfig(nextSavedConfig);
        setImageWorkspaceDraft((prev) => ({ ...prev, model: nextSavedConfig.imageModel || "gpt-image-2" }));
        setModelConfigOpen(false);
        setToast("模型配置已保存");
      })
      .catch((error) => {
        setConnectionError(normalizeUserMessage(error.message, "模型配置保存失败"));
      });
  };

  const handleSaveKnowledgeAdminAuth = () => {
    handleSaveAdminAuth();
    loadKnowledgeDocuments().catch(() => {});
  };

  const handleKnowledgeFileSelect = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    await runKnowledgeAction(
      "upload",
      () => uploadKnowledgeDocument(file, "global", file.name, "MRAG Knowledge"),
      "知识文档已上传"
    );
  };

  const handleKnowledgeWebUrlImport = async () => {
    const url = knowledgeWebUrl.trim();
    if (!url) {
      setKnowledgeError("请输入网页地址");
      return;
    }
    const adminAuth = getAdminAuth();
    if (!adminAuth?.username || !adminAuth?.password) {
      setKnowledgeError("请先保存后台权限");
      return;
    }
    setKnowledgeAction("web-url");
    setKnowledgeError("");
    try {
      const res = await uploadKnowledgeWebUrl({ url, goodsId: "global" });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "网页资料导入失败"));
      }
      setKnowledgeWebUrl("");
      setToast("网页资料已加入知识库");
      await loadKnowledgeDocuments();
    } catch (error) {
      setKnowledgeError(normalizeUserMessage(error.message, "网页资料导入失败"));
    } finally {
      setKnowledgeAction("");
    }
  };

  const handleKnowledgeFullContent = async (documentId) => {
    if (!documentId) return;
    const adminAuth = getAdminAuth();
    if (!adminAuth?.username || !adminAuth?.password) {
      setKnowledgeFragmentsError("请先保存后台权限");
      return;
    }
    setActiveKnowledgeDocumentId(documentId);
    setKnowledgeAction(`full-${documentId}`);
    setKnowledgeFragmentsLoading(true);
    setKnowledgeFragmentsError("");
    try {
      const res = await getKnowledgeDocumentFullContent(documentId);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "知识文档全文读取失败"));
      }
      const content = res.data || null;
      setKnowledgeFullContent(content);
      setKnowledgeFragments(content?.fragments || []);
    } catch (error) {
      setKnowledgeFullContent(null);
      setKnowledgeFragmentsError(normalizeUserMessage(error.message, "知识文档全文读取失败"));
    } finally {
      setKnowledgeFragmentsLoading(false);
      setKnowledgeAction("");
    }
  };

  const handleDisableKnowledgeDocument = async (documentId) => {
    if (!documentId) return;
    const adminAuth = getAdminAuth();
    if (!adminAuth?.username || !adminAuth?.password) {
      setKnowledgeError("请先保存后台权限");
      return;
    }
    setKnowledgeAction(`disable-${documentId}`);
    setKnowledgeError("");
    try {
      const res = await deleteKnowledgeDocument(documentId);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "知识文档下线失败"));
      }
      if (activeKnowledgeDocumentId === documentId) {
        setActiveKnowledgeDocumentId("");
        setKnowledgeFragments([]);
        setKnowledgeFullContent(null);
      }
      setToast("知识文档已下线");
      await loadKnowledgeDocuments();
    } catch (error) {
      setKnowledgeError(normalizeUserMessage(error.message, "知识文档下线失败"));
    } finally {
      setKnowledgeAction("");
    }
  };

  const handleRebuildKnowledgeVector = () => {
    runKnowledgeAction("rebuild", rebuildKnowledgeVector, "知识向量已重建").catch(() => {});
  };

  const handleCompensateKnowledgeVector = () => {
    runKnowledgeAction("compensate", compensateKnowledgeVector, "知识向量已补偿").catch(() => {});
  };


  const buyPackage = async (pkg, buyType, options = {}) => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    const key = `${pkg.goodsId}-${buyType}${options.teamId ? `-${options.teamId}` : ""}`;
    setBuyingKey(key);
    setConnectionError("");
    try {
      const product = {
        ...pkg,
        activityId: pkg.activityId || groupMarketConfig?.activityId,
        groupPrice: groupMarketConfig?.goods?.payPrice || pkg.groupPrice,
        originPrice: groupMarketConfig?.goods?.originalPrice || pkg.originPrice,
        teamId: options.teamId || ""
      };
      if (buyType === "group" && !product.activityId) {
        throw new Error("当前额度包缺少拼团活动信息");
      }
      const userId = auth.userId || quota?.userId;
      const orderRes = buyType === "group"
        ? await lockMarketPayOrder(product, userId, { teamId: options.teamId || "" })
        : await createDirectOrder(product, userId);
      if (orderRes.code !== "0000") throw new Error(normalizeUserMessage(orderRes.info, "订单创建失败"));
      const data = orderRes.data || {};
      setPaymentDialog({
        orderId: data.orderId,
        productName: data.goodsName || product.goodsName || "额度订单",
        amount: data.payAmount || data.payPrice || data.lockAmount || (buyType === "group" ? product.groupPrice : product.originPrice),
        marketType: buyType === "group" ? 1 : 0,
        teamId: data.teamId || options.teamId || "",
        teamSize: data.teamSize || product.teamSize || groupMarketConfig?.discount?.target,
        quotaAmount: product.quotaAmount,
        productType: product.productType,
        payUrl: data.payUrl || "",
        payFormHtml: data.payFormHtml || (data.paymentType === "PAGE_FORM" ? data.payUrl : ""),
        paymentType: data.paymentType || (isPaymentFormHtml(data.payUrl) ? "PAGE_FORM" : ""),
        payChannel: data.payChannel || preferredFrontendPayChannel(),
        gatewayTradeNo: data.gatewayTradeNo || "",
        source: "new"
      });
      setRechargeTab("orders");
      setToast("订单已创建，请继续支付");
      await loadOrders().catch(() => {});
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "购买失败"));
    } finally {
      setBuyingKey("");
    }
  };

  const payExistingOrder = async (order) => {
    if (!order?.orderId) return;
  const payUrl = order.payUrl || "";
  setPaymentDialog({
    orderId: order.orderId,
    productName: order.productName || order.goodsName || order.productId || "额度订单",
    amount: order.payAmount || order.totalAmount,
    marketType: order.marketType,
    teamId: order.teamId || order.groupTeamId || "",
    teamSize: order.teamSize || order.targetCount || "",
    quotaAmount: 0,
    productType: order.productType || order.goodsType || "",
    payUrl,
    payFormHtml: isPaymentFormHtml(payUrl) ? payUrl : "",
    paymentType: isPaymentFormHtml(payUrl) ? "PAGE_FORM" : "",
    payChannel: order.payChannel || preferredFrontendPayChannel(),
    gatewayTradeNo: order.gatewayTradeNo || order.outTradeNo || "",
    source: "existing"
  });
};

  const confirmPayment = async () => {
    if (!paymentDialog?.orderId) return;
    setBuyingKey(`pay-${paymentDialog.orderId}`);
    setConnectionError("");
    try {
      const preparedPayment = {
        ...paymentDialog,
        payFormHtml: paymentDialog.payFormHtml || (isPaymentFormHtml(paymentDialog.payUrl) ? paymentDialog.payUrl : "")
      };
      const payWindow = window.open("", "_blank");
      if (payWindow && !payWindow.closed) {
        payWindow.document.write("<!doctype html><html><head><meta charset=\"UTF-8\"><title>支付宝支付</title></head><body>正在进入支付宝...</body></html>");
        payWindow.document.close();
      }
      if (openGatewayPayment(preparedPayment, payWindow)) {
        setPaymentDialog(null);
        setToast("已打开支付宝支付页，支付完成后订单会通过回调更新");
        await loadOrders().catch(() => {});
        return;
      }
      const payRes = await createPayment(paymentDialog.orderId, {
        payChannel: paymentDialog.payChannel || "",
        returnUrl: paymentReturnUrl(paymentDialog.orderId)
      });
      if (!apiSucceeded(payRes)) {
        throw new Error(normalizeUserMessage(payRes?.info || payRes?.message, "支付宝支付创建失败"));
      }
      if (!openGatewayPayment(payRes.data || {}, payWindow)) {
        throw new Error("支付宝支付表单为空");
      }
      setPaymentDialog(null);
      setToast("已打开支付宝支付页，支付完成后订单会通过回调更新");
      await loadOrders().catch(() => {});
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "支付宝支付创建失败"));
    } finally {
      setBuyingKey("");
    }
  };

  // 支付宝支付完成回跳（returnUrl 带 paymentReturn=1）：清理地址参数，
  // 刷新订单和额度，并把充值面板切到订单页让用户看到最新支付状态
  useEffect(() => {
    if (typeof window === "undefined") return;
    const params = new URLSearchParams(window.location.search);
    if (params.get("paymentReturn") !== "1") return;
    params.delete("paymentReturn");
    params.delete("orderId");
    const rest = params.toString();
    window.history.replaceState({}, "", `${window.location.pathname}${rest ? `?${rest}` : ""}`);
    if (!getUserAuth()?.token) return;
    setRechargeTab("orders");
    setRechargeOpen(true);
    loadOrders().catch(() => {});
    loadQuota().catch(() => {});
    refreshRecharge().catch(() => {});
  }, [loadOrders, loadQuota, refreshRecharge]);

  return (
    <div className="bear-doctor-app" data-theme={theme}>
      <div className="glow-effect glow-effect-1" />
      <div className="glow-effect glow-effect-2" />
      <div className="container">
        <aside className="sidebar">
          <div className="sidebar-header">
            <div className="app-title">
              <img className="logo-icon" src="/bear-doctor-logo.png" alt="熊博士Agent" />
              <span className="title-text">熊博士Agent</span>
            </div>
            <button className="new-chat-btn" onClick={createNewChat}>
              <Plus size={16} />
              <span>新对话</span>
            </button>
          </div>

          <div className="chat-list-title">会话</div>
          <div className="chat-list">
            {chatList.map((chat) => (
              <div key={chat.id} className={`chat-item ${currentChatId === chat.id ? "active" : ""}`} onClick={() => selectChat(chat.id)}>
                <span className="chat-title">{chat.title}</span>
                {!chat.isNew && (
                  <button className="delete-btn" onClick={(event) => { event.stopPropagation(); deleteChat(chat.id); }}>
                    <Trash2 size={14} />
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className="sidebar-footer">
            <div className="model-info">
              <span>🔗</span>
              <span>{backendText}</span>
            </div>
          </div>
        </aside>

        <main className="main-content">
          <div className="top-decoration">
            <div className="decoration-line" />
            <div className="top-actions">
              <ThemeToggle theme={theme} onToggle={toggleTheme} />
              <button className="account-btn" onClick={() => setModelConfigOpen(true)}>
                <Settings size={15} />
                <span>模型</span>
              </button>
              <button className="quota-chip" onClick={openRecharge}>
                <Wallet size={15} />
                <span>{Number(quota?.quotaBalance || 0).toFixed(2)} 点</span>
              </button>
              {auth?.token ? (
                <button className="account-btn" onClick={handleLogout}>
                  <LogOut size={15} />
                  <span>退出</span>
                </button>
              ) : (
                <button className="account-btn" onClick={() => setLoginOpen(true)}>
                  <LogIn size={15} />
                  <span>登录</span>
                </button>
              )}
            </div>
          </div>

          <div className="messages-container" ref={messagesContainer}>
            {showAcademicProjectPanel && (
              <AcademicProjectPanel
                projects={academicProjects}
                model={academicProjectWorkspace}
                activeProjectId={activeAcademicProject?.projectId || ""}
                loading={academicProjectLoading}
                error={academicProjectError}
                onRefresh={loadAcademicProjects}
                onCreate={createDefaultAcademicProject}
                onSelect={setActiveAcademicProjectId}
                onApplyPatch={applyPendingAcademicPatch}
              />
            )}
            {currentWorkspacePage.supportsHistory && !showComposerWorkspaceSettings && (
              <WorkspaceHistoryPanel
                workspace={currentWorkspace}
                items={workspaceHistory.workspaceId === currentWorkspace.id ? workspaceHistory.items : []}
                loading={workspaceHistoryLoading}
                error={workspaceHistoryError}
                runDetail={workspaceRunDetail}
                runDetailLoading={workspaceRunDetailLoading}
                runDetailError={workspaceRunDetailError}
                currentSessionId={currentChatId}
                onRefresh={() => loadWorkspaceHistory(currentWorkspace.id)}
                onOpen={openWorkspaceHistoryItem}
                onDownloadArtifact={handleArtifactDownload}
              />
            )}
            {currentWorkspace.id === "data" && (
              <DataWorkspacePanel
                draft={dataWorkspaceDraft}
                onChange={setDataWorkspaceDraft}
                catalog={dataWorkspaceCatalog}
                catalogLoading={dataWorkspaceCatalogLoading}
                catalogError={dataWorkspaceCatalogError}
              />
            )}
            {currentWorkspace.id === "mrag" && (
              <MragKnowledgePanel
                adminForm={adminForm}
                setAdminForm={setAdminForm}
                knowledgeBases={knowledgeBaseCatalog}
                activeKnowledgeBaseId={activeKnowledgeBaseId}
                onSelectKnowledgeBase={setActiveKnowledgeBaseId}
                documents={visibleKnowledgeDocuments}
                fragments={knowledgeFragments}
                loading={knowledgeLoading}
                fragmentsLoading={knowledgeFragmentsLoading}
                actionKey={knowledgeAction}
                error={knowledgeError}
                fragmentsError={knowledgeFragmentsError}
                activeDocumentId={activeKnowledgeDocumentId}
                webUrl={knowledgeWebUrl}
                fullContent={knowledgeFullContent}
                fileInputRef={knowledgeFileInputRef}
                onWebUrlChange={setKnowledgeWebUrl}
                onWebUrlImport={handleKnowledgeWebUrlImport}
                onSaveAuth={handleSaveKnowledgeAdminAuth}
                onRefresh={loadKnowledgeDocuments}
                onOpenFragments={loadKnowledgeFragments}
                onFullContent={handleKnowledgeFullContent}
                onDisableDocument={handleDisableKnowledgeDocument}
                onUploadClick={() => knowledgeFileInputRef.current?.click()}
                onFileChange={handleKnowledgeFileSelect}
                onRebuild={handleRebuildKnowledgeVector}
                onCompensate={handleCompensateKnowledgeVector}
              />
            )}
            {currentWorkspace.id === "trade" && (
              <TradeWorkspacePanel
                summary={tradeWorkspaceSummary}
                loading={ordersLoading}
                onRefresh={() => loadOrders().catch(() => {})}
                onOpenRecharge={openRecharge}
                onOpenOrderRecords={openTradeOrderRecords}
              />
            )}
            {(!currentChat || currentChat.messages.length === 0) ? (
              <WorkspaceEmptyState
                workspace={currentWorkspace}
                profile={currentWorkspaceProfile}
                capabilities={agentCapabilities}
                pageModel={currentWorkspacePage}
                onPrompt={quickPrompt}
                onOpenRecharge={openRecharge}
              />
            ) : (
              <>
                <SessionMemoryPanel memory={currentChat.memory} />
                {currentChat.messages.map((msg) => (
                  <MessageItem
                    key={msg.id}
                    msg={msg}
                    copied={copiedId === msg.id}
                    isSending={isSending}
                    isLast={currentChat.messages[currentChat.messages.length - 1]?.id === msg.id}
                    onCopy={copyMessage}
                    onEditUser={editAndRetryUserMessage}
                    onRetryAssistant={retryAssistantMessage}
                    onToggleTimeline={toggleTimeline}
                    onToggleReference={toggleReference}
                    onRecommendClick={quickPrompt}
                    onDownloadArtifact={handleArtifactDownload}
                  />
                ))}
              </>
            )}
          </div>

          <div className="input-area">
            {selectedFile && (
              <div className="file-preview">
                {selectedFile.isImage || isImageArtifact({ fileName: selectedFile.name, contentType: selectedFile.contentType || selectedFile.fileType, previewUrl: selectedFile.previewUrl }) ? (
                  <div className="image-preview-item">
                    <div className="image-preview-thumb">
                      {selectedFile.previewUrl ? (
                        <img src={selectedFile.previewUrl} alt={selectedFile.name || "上传图片"} />
                      ) : (
                        <div className="image-preview-empty"><ImagePlus size={24} /></div>
                      )}
                      {isUploading && (
                        <div className="image-upload-mask">
                          <Loader2 size={17} className="spin" />
                          <span>解析中</span>
                        </div>
                      )}
                      <button type="button" className="image-preview-remove" onClick={clearSelectedFile} aria-label="移除图片">
                        <X size={15} />
                      </button>
                    </div>
                    <div className="image-preview-meta">
                      <div className="image-preview-name">{selectedFile.name}</div>
                      <div className="image-preview-size">{formatFileSize(selectedFile.size)}</div>
                    </div>
                  </div>
                ) : (
                <div className="file-preview-item">
                  <div className="file-icon-wrapper"><FileText size={22} /></div>
                  <div className="file-info">
                    <div className="file-name">{selectedFile.name}</div>
                    <div className="file-size">{formatFileSize(selectedFile.size)}</div>
                    {isUploading && <div className="upload-parsing"><Loader2 size={14} className="spin" /><span>解析中...</span></div>}
                  </div>
                  <button type="button" className="remove-file" onClick={clearSelectedFile} aria-label="移除文件"><Trash2 size={16} /></button>
                </div>
                )}
              </div>
            )}

            {canResumeCurrentChat && (
              <div className="resume-bar">
                <span>上次生成已停止，可以继续这个任务</span>
                <button type="button" onClick={resumeMessage}>
                  <RotateCcw size={15} />
                  <span>继续生成</span>
                </button>
              </div>
            )}

            <div className="input-container composer-panel">
              <textarea
                value={inputMessage}
                onChange={(event) => setInputMessage(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    sendMessage();
                  }
                }}
                placeholder={composerPlaceholder}
                rows={1}
              />
              <input ref={fileInputRef} type="file" accept=".md,.txt,.pdf,.docx,.png,.jpg,.jpeg,.webp" onChange={handleFileSelect} hidden />
              <div className="composer-toolbar">
                <div className="composer-tool-left">
                  {canUseFile && !selectedFile && (
                    <button className="file-btn composer-icon-btn" disabled={isUploading} onClick={() => fileInputRef.current?.click()} title="上传文件">
                      <Plus size={19} />
                    </button>
                  )}
                  {AGENTS.map((agent) => {
                    const Icon = COMPOSER_AGENT_ICONS[agent.id] || MessageCircle;
                    return (
                      <button
                        key={agent.id}
                        type="button"
                        className={`composer-tool-pill ${selectedAgent === agent.id ? "active" : ""}`}
                        onClick={() => selectAgent(agent.id)}
                        title={agent.summary}
                      >
                        <Icon size={16} />
                        <span>{COMPOSER_AGENT_LABELS[agent.id] || agent.name}</span>
                      </button>
                    );
                  })}
                  <button
                    type="button"
                    className={`web-search-toggle composer-tool-pill ${webSearchEnabled ? "active" : ""}`}
                    aria-pressed={webSearchEnabled}
                    onClick={() => setWebSearchEnabled((prev) => !prev)}
                    title={webSearchEnabled ? "关闭联网搜索" : "开启联网搜索"}
                  >
                    <Globe2 size={16} />
                    <span>搜索</span>
                  </button>
                </div>
                <div className="composer-tool-right">
                  <button type="button" className="composer-mode-button" title="本轮执行方式">
                    <span>{selectedModeIntent.executionMode || selectedModeIntent.label}</span>
                  </button>
                  <button
                    className={`send-btn ${isSending ? "stop" : ""} ${!isSending && (!inputMessage.trim() && !selectedFile) ? "disabled" : ""}`}
                    onClick={isSending ? stopMessage : sendMessage}
                    disabled={!isSending && (!inputMessage.trim() && !selectedFile)}
                    title={isSending ? "停止生成" : "发送"}
                  >
                    {isSending ? <Square size={17} /> : <ArrowUp size={18} />}
                  </button>
                </div>
              </div>
            </div>
            <div className="composer-mode-intent">
              <div className="composer-mode-intent-main">
                <span>{selectedModeIntent.label}</span>
                <em>{selectedModeIntent.route}</em>
                <small>{selectedModeIntent.hint}</small>
              </div>
              <div className="composer-mode-intent-grid" aria-label="本轮执行说明">
                <div><b>模式</b><span>{selectedModeIntent.executionMode}</span></div>
                <div><b>协作</b><span>{selectedModeIntent.agents}</span></div>
                <div><b>产出</b><span>{selectedModeIntent.outputs}</span></div>
                <div><b>过程</b><span>{selectedModeIntent.trace}</span></div>
              </div>
            </div>
            {selectedAgent === "ppt" && (
              <div className="composer-ppt-skill-note">
                <div>
                  <FileText size={15} />
                  <strong>PPT 生成 / 图片重建</strong>
                </div>
                <span>
                  直接写清楚主题、页数、受众、风格和重点；也可以上传文档或图片素材。系统会先整理大纲，再生成可编辑 PPT，复杂图片会作为图片元素保留。
                </span>
              </div>
            )}
            {selectedAgent === "deep" && (
              <div className="composer-research-style-note">
                <div>
                  <Search size={15} />
                  <strong>深度任务</strong>
                </div>
                <span>
                  适合技术调研、竞品对比、方案选型和复杂资料整理。输入目标、范围和重点，系统会先拆解计划，再整理来源、对比证据并输出结果。
                </span>
              </div>
            )}
            {webSearchEnabled && (
              <div className="composer-web-search-note">
                <div>
                  <Globe2 size={15} />
                  <strong>联网搜索对话</strong>
                </div>
                <span>
                  适合查最新资料、公开网页和需要来源的问题。输入要查证的内容，系统会先搜索网页，再结合来源回答并说明不确定点。
                </span>
              </div>
            )}
            {selectedAgent === "manual-skills" && (
              <div className="composer-skill-settings">
                <div className="composer-skill-settings-head">
                  <strong>Skill</strong>
                  <span>{selectedSkillName || "自动"}</span>
                </div>
                <p className="composer-skill-help">
                  {selectedManualSkillHelp}
                </p>
                <div className="skill-picker composer-skill-picker" aria-label="选择 Skill">
                  <button
                    type="button"
                    className={!selectedSkillName ? "active" : ""}
                    onClick={() => setSelectedSkillName("")}
                  >
                    自动
                  </button>
                  {manualSkills.map((skill) => (
                    <button
                      type="button"
                      key={skill.name || skill.description}
                      className={selectedSkillName === skill.name ? "active" : ""}
                      onClick={() => setSelectedSkillName(skill.name || "")}
                      title={skill.description || skill.name || ""}
                    >
                      {skill.name || "技能"}
                    </button>
                  ))}
                  {manualSkills.length === 0 && (
                    <em>
                      {agentCapabilities?.skillsDirectory
                        ? `未发现技能：${agentCapabilities.skillsDirectory}`
                        : "未配置本地技能目录"}
                    </em>
                  )}
                </div>
              </div>
            )}
            {currentWorkspace.id === "image" && (
              <ImageWorkspacePanel
                compact
                draft={imageWorkspaceDraft}
                onChange={setImageWorkspaceDraft}
                hasReference={Boolean(selectedFile?.fileId && isImageArtifact({ fileName: selectedFile.name, contentType: selectedFile.fileType }))}
              />
            )}
          </div>
        </main>
      </div>

      {visibleConnectionError && (
        <div className="connection-error">
          <AlertTriangle size={18} />
          <span>{visibleConnectionError}</span>
          <button className="retry-btn" onClick={() => setConnectionError("")}>关闭</button>
        </div>
      )}

      {toast && (
        <div className="toast">
          <Check size={16} />
          <span>{toast}</span>
          <button type="button" className="toast-close" aria-label="关闭提示" onClick={() => setToast("")}>
            <X size={16} />
          </button>
        </div>
      )}

      {loginOpen && (
        <AuthDialog
          mode={authMode}
          setMode={setAuthMode}
          form={authForm}
          setForm={setAuthForm}
          error={authError}
          loading={authLoading}
          onSubmit={handleAuthSubmit}
          onDemoLogin={handleDemoAuth}
          onClose={() => setLoginOpen(false)}
        />
      )}

      {rechargeOpen && (
        <RechargeDialog
          quota={quota}
          membership={membership}
          billingPolicy={billingPolicy}
          flows={quotaFlows}
          orders={orders}
          ordersLoading={ordersLoading}
          packages={packages}
          buyingKey={buyingKey}
          activeTab={rechargeTab}
          setActiveTab={setRechargeTab}
          groupPreviewPackage={groupPreviewPackage}
          groupMarketConfig={groupMarketConfig}
          groupTeamsLoading={groupTeamsLoading}
          currentUserId={auth?.userId || quota?.userId}
          onBuy={buyPackage}
          onOpenGroupPreview={openGroupPreview}
          onBackToPackages={() => {
            setGroupPreviewPackage(null);
            setGroupMarketConfig(null);
          }}
          onRefresh={refreshRecharge}
          onPayOrder={payExistingOrder}
          onClose={() => {
            setGroupPreviewPackage(null);
            setRechargeOpen(false);
          }}
        />
      )}

      {modelConfigOpen && (
        <ModelConfigDialog
          config={modelConfig}
          onSave={handleSaveModelConfig}
          onClose={() => setModelConfigOpen(false)}
        />
      )}

      {paymentDialog && (
        <PaymentConfirmDialog
          payment={paymentDialog}
          buyingKey={buyingKey}
          onConfirm={confirmPayment}
          onCancel={() => setPaymentDialog(null)}
        />
      )}
    </div>
  );
}

function formatWorkspaceHistoryTime(value = "") {
  const text = String(value || "").replace("T", " ").trim();
  return text.length > 19 ? text.slice(0, 19) : text;
}


function WorkspaceHistoryPanel({
  workspace,
  items = [],
  loading,
  error,
  runDetail,
  runDetailLoading,
  runDetailError,
  currentSessionId,
  onRefresh,
  onOpen,
  onDownloadArtifact
}) {
  if (!workspaceSupportsHistory(workspace.id)) return null;
  const selectedItem = runDetail?.item;
  const detail = runDetail?.detail;
  const run = detail?.run || {};
  const tools = Array.isArray(detail?.toolInvocations) ? detail.toolInvocations : [];
  const artifacts = Array.isArray(detail?.artifacts) ? detail.artifacts : [];
  const runResultPanels = runDetailToResultPanels(detail);
  const runEvidence = buildAgentRunEvidenceSummary(detail);
  return (
    <section className="workspace-history-panel">
      <div className="workspace-history-head">
        <div>
          <strong>工作区历史</strong>
          <span>{workspace.name} 最近任务</span>
        </div>
        <button type="button" onClick={onRefresh} disabled={loading}>
          {loading ? <Loader2 size={14} className="spin" /> : <RotateCcw size={14} />}
          <span>{loading ? "刷新中" : "刷新"}</span>
        </button>
      </div>
      {error && <div className="workspace-history-error">{error}</div>}
      {loading && items.length === 0 && <div className="workspace-history-empty">正在读取历史</div>}
      {!loading && items.length === 0 && !error && <div className="workspace-history-empty">暂无历史任务</div>}
      {items.length > 0 && (
        <div className="workspace-history-list">
          {items.map((item) => {
            const isActive = item.sessionId && item.sessionId === currentSessionId;
            const createdAt = formatWorkspaceHistoryTime(item.createdAt);
            const canOpen = Boolean(item.sessionId) || item.workspaceId === "trade";
            return (
              <button
                type="button"
                key={item.id}
                className={`workspace-history-item ${isActive ? "active" : ""}`}
                onClick={() => onOpen(item)}
                disabled={!canOpen}
              >
                {item.artifactUrl ? (
                  <img src={item.artifactUrl} alt={item.artifactName || item.title} />
                ) : (
                  <span className="workspace-history-icon">
                    {item.workspaceId === "image"
                      ? <ImagePlus size={16} />
                      : item.workspaceId === "trade"
                        ? <Wallet size={16} />
                        : <FileText size={16} />}
                  </span>
                )}
                <span className="workspace-history-body">
                  <b>{item.title}</b>
                  {item.summary && <em>{item.summary}</em>}
                  <small>
                    {item.status || "SUCCESS"}
                    {createdAt ? ` · ${createdAt}` : ""}
                    {item.durationMillis ? ` · ${item.durationMillis} ms` : ""}
                  </small>
                </span>
              </button>
            );
          })}
        </div>
      )}
      {(selectedItem || runDetailLoading || runDetailError) && (
        <div className="workspace-run-detail">
          <div className="workspace-run-detail-head">
            <div>
              <strong>运行详情</strong>
              <span>{run.runId || selectedItem?.runId || selectedItem?.title || "当前任务"}</span>
            </div>
            {run.status && <em>{run.status}</em>}
          </div>
          {runDetailLoading && <div className="workspace-history-empty">正在读取运行详情</div>}
          {runDetailError && <div className="workspace-history-error">{runDetailError}</div>}
          {detail && !runDetailLoading && (
            <>
              <div className="workspace-run-stats">
                <span>{run.taskType || selectedItem?.workspaceId || workspace.id}</span>
                <span>{tools.length} 个工具</span>
                <span>{artifacts.length} 个产物</span>
                {run.durationMillis ? <span>{run.durationMillis} ms</span> : null}
              </div>
              {runEvidence.visible && (
                <>
                  <div className="workspace-run-tools">
                    {runEvidence.metrics.map((metric) => (
                      <div key={metric.key}>
                        <b>{metric.value}</b>
                        <span>{metric.label}</span>
                      </div>
                    ))}
                  </div>
                  {runEvidence.highlights.length > 0 && (
                    <div className="workspace-run-artifacts">
                      {runEvidence.highlights.map((item, index) => (
                        <span key={`${item}-${index}`}>{item}</span>
                      ))}
                    </div>
                  )}
                </>
              )}
              {run.finalSummary && <p>{run.finalSummary}</p>}
              {run.errorMessage && <p className="danger">{run.errorMessage}</p>}
              {tools.length > 0 && (
                <div className="workspace-run-tools">
                  {tools.slice(0, 4).map((tool) => (
                    <div key={tool.invocationId || `${tool.toolName}-${tool.startedAt}`}>
                      <b>{tool.toolName || "tool"}</b>
                      <span>{tool.status || "-"}{tool.latencyMillis ? ` · ${tool.latencyMillis} ms` : ""}</span>
                      {tool.resultSummary && <em>{tool.resultSummary}</em>}
                    </div>
                  ))}
                </div>
              )}
              {artifacts.length > 0 && (
                <div className="workspace-run-artifacts">
                  {artifacts.slice(0, 4).map((artifact) => (
                    <span key={artifact.artifactId || artifact.downloadUrl || artifact.fileName}>
                      {artifact.title || artifact.fileName || artifact.artifactType || "artifact"}
                    </span>
                  ))}
                </div>
              )}
              <ResultPanelList panels={runResultPanels} onDownloadArtifact={onDownloadArtifact} />
            </>
          )}
        </div>
      )}
    </section>
  );
}

function MragKnowledgePanel({
  adminForm,
  setAdminForm,
  knowledgeBases = [],
  activeKnowledgeBaseId,
  onSelectKnowledgeBase,
  documents = [],
  fragments = [],
  loading,
  fragmentsLoading,
  actionKey,
  error,
  fragmentsError,
  activeDocumentId,
  webUrl = "",
  fullContent,
  fileInputRef,
  onWebUrlChange,
  onWebUrlImport,
  onSaveAuth,
  onRefresh,
  onOpenFragments,
  onFullContent,
  onDisableDocument,
  onUploadClick,
  onFileChange,
  onRebuild,
  onCompensate
}) {
  const hasAdminAuth = Boolean(adminForm.username && adminForm.password);
  return (
    <section className="mrag-knowledge-panel">
      <div className="mrag-knowledge-head">
        <div>
          <strong>MRAG 知识库</strong>
          <span>上传资料后可进入向量检索和多模态问答链路</span>
        </div>
        <button type="button" onClick={onRefresh} disabled={loading || !hasAdminAuth}>
          {loading ? <Loader2 size={14} className="spin" /> : <RotateCcw size={14} />}
          <span>{loading ? "刷新中" : "刷新"}</span>
        </button>
      </div>

      <div className="mrag-knowledge-auth">
        <input
          value={adminForm.username}
          onChange={(event) => setAdminForm({ ...adminForm, username: event.target.value })}
          placeholder="后台账号"
        />
        <input
          value={adminForm.password}
          onChange={(event) => setAdminForm({ ...adminForm, password: event.target.value })}
          type="password"
          placeholder="后台密码"
        />
        <button type="button" onClick={onSaveAuth}>保存</button>
      </div>

      {error && <div className="mrag-knowledge-error"><AlertTriangle size={14} /> <span>{error}</span></div>}

      <div className="mrag-knowledge-web">
        <Globe2 size={15} />
        <input
          value={webUrl}
          onChange={(event) => onWebUrlChange?.(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              onWebUrlImport?.();
            }
          }}
          placeholder="https://example.com/article"
        />
        <button type="button" onClick={onWebUrlImport} disabled={!hasAdminAuth || Boolean(actionKey)}>
          <span>{actionKey === "web-url" ? "导入中" : "导入网页"}</span>
        </button>
      </div>

      <div className="mrag-knowledge-actions">
        <input
          ref={fileInputRef}
          type="file"
          accept=".md,.txt,.pdf,.docx"
          onChange={onFileChange}
          hidden
        />
        <button type="button" onClick={onUploadClick} disabled={!hasAdminAuth || Boolean(actionKey)}>
          <Paperclip size={15} />
          <span>{actionKey === "upload" ? "上传中" : "上传文档"}</span>
        </button>
        <button type="button" onClick={onRebuild} disabled={!hasAdminAuth || Boolean(actionKey)}>
          <RotateCcw size={15} />
          <span>{actionKey === "rebuild" ? "重建中" : "重建向量"}</span>
        </button>
        <button type="button" onClick={onCompensate} disabled={!hasAdminAuth || Boolean(actionKey)}>
          <AlertTriangle size={15} />
          <span>{actionKey === "compensate" ? "补偿中" : "补偿向量"}</span>
        </button>
      </div>

      {knowledgeBases.length > 0 && (
        <div className="mrag-kb-catalog">
          <button
            type="button"
            className={!activeKnowledgeBaseId ? "active" : ""}
            onClick={() => onSelectKnowledgeBase?.("")}
          >
            <strong>全部知识</strong>
            <span>{knowledgeBases.reduce((sum, item) => sum + item.documentCount, 0)} 文档</span>
          </button>
          {knowledgeBases.slice(0, 4).map((kb) => (
            <button
              type="button"
              className={activeKnowledgeBaseId === kb.id ? "active" : ""}
              key={kb.id}
              onClick={() => onSelectKnowledgeBase?.(kb.id)}
            >
              <strong>{kb.name}</strong>
              <span>{kb.documentCount} 文档 · {kb.fragmentCount} 段 · {kb.version}</span>
              {kb.failedCount > 0 && <em>{kb.failedCount} 失败</em>}
            </button>
          ))}
        </div>
      )}

      <div className="mrag-knowledge-list">
        {documents.slice(0, 6).map((doc) => (
          <article
            className={`mrag-knowledge-row ${activeDocumentId === doc.documentId ? "active" : ""}`}
            key={doc.documentId || doc.documentName}
          >
            <button
              type="button"
              className="mrag-knowledge-row-main"
              onClick={() => onOpenFragments?.(doc.documentId)}
              disabled={!doc.documentId || fragmentsLoading}
            >
              <FileText size={15} />
              <div>
                <strong>{doc.documentName || doc.documentId}</strong>
                <span>
                  {doc.documentType || "Document"} · {doc.documentStatus || "-"} · {doc.fragmentCount || 0} 段
                </span>
              </div>
              <em>{formatWorkspaceHistoryTime(doc.updateTime || doc.createTime)}</em>
            </button>
            <div className="mrag-knowledge-row-actions">
              <button
                type="button"
                onClick={() => onFullContent?.(doc.documentId)}
                disabled={!doc.documentId || actionKey === `full-${doc.documentId}`}
              >
                {actionKey === `full-${doc.documentId}` ? "读取中" : "全文"}
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => onDisableDocument?.(doc.documentId)}
                disabled={!doc.documentId || actionKey === `disable-${doc.documentId}`}
              >
                {actionKey === `disable-${doc.documentId}` ? "下线中" : "下线"}
              </button>
            </div>
          </article>
        ))}
        {!loading && documents.length === 0 && (
          <div className="mrag-knowledge-empty">
            {hasAdminAuth ? "暂无知识文档" : "请先保存后台账号"}
          </div>
        )}
      </div>
      {(activeDocumentId || fragmentsError) && (
        <div className="mrag-fragment-panel">
          <div className="mrag-fragment-head">
            <strong>文档片段</strong>
            <span>{fragmentsLoading ? "读取中" : `${fragments.length} 段`}</span>
          </div>
          {fragmentsError && (
            <div className="mrag-knowledge-error"><AlertTriangle size={14} /> <span>{fragmentsError}</span></div>
          )}
          {!fragmentsLoading && !fragmentsError && fragments.length === 0 && (
            <div className="mrag-knowledge-empty">暂无可查看片段</div>
          )}
          {fragments.slice(0, 5).map((fragment) => (
            <article className="mrag-fragment-card" key={fragment.fragmentId || `${fragment.documentId}-${fragment.rankNo}`}>
              <div>
                <b>#{fragment.rankNo ?? "-"}</b>
                <span>{fragment.fragmentStatus || "-"} · {fragment.chunkType || "chunk"}</span>
              </div>
              <p>{fragment.content}</p>
            </article>
          ))}
          {fullContent?.content && (
            <article className="mrag-full-content-card">
              <div>
                <strong>{fullContent.documentName || fullContent.documentId}</strong>
                <span>{fullContent.fragmentCount || 0} 段</span>
              </div>
              <pre>{fullContent.content}</pre>
            </article>
          )}
        </div>
      )}
    </section>
  );
}

function ResultPanelList({ panels = [], onDownloadArtifact }) {
  if (!panels.length) return null;
  return (
    <div className="result-panel-section">
      {panels.map((panel) => (
        <section className={`result-panel result-panel-${panel.kind}`} key={panel.id}>
          <div className="result-panel-head">
            <div>
              <strong>{panel.title || panel.toolName || "工具结果"}</strong>
              {panel.summary && <span>{panel.summary}</span>}
            </div>
            <div className="result-panel-tags">
              <em>{TOOL_LABELS[panel.toolName] || panel.toolName || panel.kind}</em>
              <span>{resultPanelKindLabel(panel.kind)}</span>
            </div>
          </div>

          {panel.kind === "audit" && (
            <div className="result-audit-panel">
              {(panel.findings || []).length > 0 && (
                <div className="result-audit-findings">
                  {panel.findings.slice(0, 6).map((finding, index) => (
                    <div className={`result-audit-finding result-audit-${String(finding.severity || "info").toLowerCase()}`} key={`${panel.id}-finding-${index}`}>
                      <strong>{formatPanelValue(finding.code || "FINDING")}</strong>
                      <span>{formatPanelValue(finding.severity || "INFO")}</span>
                      {finding.message && <p>{formatPanelValue(finding.message)}</p>}
                    </div>
                  ))}
                </div>
              )}
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
            </div>
          )}

          {panel.kind === "data" && (
            <>
              {(() => {
                const chart = buildDataChartPreview(panel);
                if (!chart) return null;
                return (
                  <div className="result-chart-preview">
                    <div className="result-chart-meta">
                      <span>维度 {chart.dimension}</span>
                      <span>指标 {chart.measure}</span>
                    </div>
                    <div className="result-chart-bars">
                      {chart.points.map((point, index) => (
                        <div className="result-chart-row" key={`${panel.id}-chart-${index}`}>
                          <span>{point.label}</span>
                          <div>
                            <i style={{ width: `${Math.max(4, Math.round(Math.abs(point.value) / chart.maxValue * 100))}%` }} />
                          </div>
                          <b>{formatPanelValue(point.value)}</b>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })()}
              {Object.keys(panel.numericStats || {}).length > 0 && (
                <div className="result-stat-grid">
                  {Object.entries(panel.numericStats || {}).slice(0, 4).map(([name, stats]) => (
                    <div className="result-stat-card" key={name}>
                      <b>{name}</b>
                      <span>{Object.entries(stats || {}).map(([key, value]) => `${key}: ${formatPanelValue(value)}`).join(" · ")}</span>
                    </div>
                  ))}
                </div>
              )}
              {panel.rows.length > 0 && (
                <div className="result-table-wrap">
                  <table className="result-table">
                    <thead>
                      <tr>
                        {panel.columns.slice(0, 6).map((column) => <th key={column}>{column}</th>)}
                      </tr>
                    </thead>
                    <tbody>
                      {panel.rows.slice(0, 5).map((row, rowIndex) => (
                        <tr key={`${panel.id}-row-${rowIndex}`}>
                          {panel.columns.slice(0, 6).map((column) => (
                            <td key={`${panel.id}-${rowIndex}-${column}`}>{formatPanelValue(row[column])}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}

          {panel.kind === "sql" && (
            <div className="result-sql-list">
              {(panel.candidates.length ? panel.candidates : [{ query: panel.title, sql: panel.content }]).slice(0, 3).map((candidate, index) => (
                <div className="result-sql-card" key={`${panel.id}-sql-${index}`}>
                  {candidate.query && <span>{formatPanelValue(candidate.query)}</span>}
                  <pre>{formatPanelValue(candidate.sql || candidate.SQL || panel.content)}</pre>
                </div>
              ))}
            </div>
          )}

          {panel.kind === "schema" && (
            <div className="result-schema-list">
              {panel.matches.slice(0, 4).map((match, index) => (
                <div className="result-schema-card" key={`${panel.id}-schema-${index}`}>
                  <strong>{formatPanelValue(match.modelCode || match.tableName || match.name)}</strong>
                  {match.score !== undefined && <span>匹配度 {formatPanelValue(match.score)}</span>}
                  {Array.isArray(match.schemaList) && match.schemaList.length > 0 && (
                    <pre>{JSON.stringify(match.schemaList.slice(0, 3), null, 2)}</pre>
                  )}
                </div>
              ))}
            </div>
          )}

          {panel.kind === "search" && (
            <div className="result-source-list">
              {(panel.sources || []).slice(0, 6).map((source, index) => {
                const href = safeExternalUrl(source.url);
                return (
                  <article className="result-source-card" key={`${panel.id}-source-${index}`}>
                    <div className="result-source-icon"><Globe2 size={15} /></div>
                    <div>
                      <strong>{source.title || source.url || "搜索来源"}</strong>
                      {source.content && <p>{source.content}</p>}
                      {(href || source.metaLabel || source.source) && (
                        <span>{hostFromUrl(href || source.url) || source.metaLabel || source.source}</span>
                      )}
                    </div>
                    {href && (
                      <a href={href} target="_blank" rel="noreferrer" aria-label="打开来源">
                        <Globe2 size={14} />
                      </a>
                    )}
                  </article>
                );
              })}
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
            </div>
          )}

          {panel.kind === "web" && (
            <div className="result-web-panel">
              {panel.url && (
                <a className="result-web-url" href={safeExternalUrl(panel.url) || undefined} target="_blank" rel="noreferrer">
                  <Globe2 size={14} />
                  <span>{panel.url}</span>
                </a>
              )}
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
              {(panel.fileRefs || []).length > 0 && (
                <div className="result-file-list compact">
                  {panel.fileRefs.slice(0, 4).map((file, index) => (
                    <div className="result-file-card" key={`${panel.id}-web-file-${index}`}>
                      <FileText size={15} />
                      <div>
                        <strong>{file.title || file.fileName || "网页文件"}</strong>
                        <span>{artifactMetaLabel(file)}</span>
                      </div>
                      {file.downloadUrl && (
                        <button type="button" onClick={() => onDownloadArtifact?.(file)}>
                          <Download size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {panel.kind === "code" && (
            <div className="result-code-panel">
              <div className="result-code-meta">
                {["language", "runtime", "exitCode", "success"].map((key) => (
                  panel.metadata?.[key] !== undefined && panel.metadata?.[key] !== "" ? (
                    <span key={key}>
                      <b>{key}</b>
                      {formatPanelValue(panel.metadata[key])}
                    </span>
                  ) : null
                ))}
              </div>
              {panel.metadata?.code && (
                <div className="result-code-block">
                  <span>code</span>
                  <pre>{formatPanelValue(panel.metadata.code)}</pre>
                </div>
              )}
              {panel.metadata?.stdout && (
                <div className="result-code-block">
                  <span>stdout</span>
                  <pre>{formatPanelValue(panel.metadata.stdout)}</pre>
                </div>
              )}
              {panel.metadata?.stderr && (
                <div className="result-code-block danger">
                  <span>stderr</span>
                  <pre>{formatPanelValue(panel.metadata.stderr)}</pre>
                </div>
              )}
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
              {(panel.fileRefs || []).length > 0 && (
                <div className="result-file-list compact">
                  {panel.fileRefs.slice(0, 4).map((file, index) => (
                    <div className="result-file-card" key={`${panel.id}-code-file-${index}`}>
                      <FileText size={15} />
                      <div>
                        <strong>{file.title || file.fileName || "执行产物"}</strong>
                        <span>{artifactMetaLabel(file)}</span>
                      </div>
                      {file.downloadUrl && (
                        <button type="button" onClick={() => onDownloadArtifact?.(file)}>
                          <Download size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {panel.kind === "quota" && (
            <div className="result-quota-panel">
              <div className="result-quota-grid">
                {[
                  ["estimatedConsumedQuota", "预估消耗"],
                  ["remainingQuota", "剩余额度"],
                  ["usedQuota", "已用额度"],
                  ["frozenQuota", "冻结额度"]
                ].map(([key, label]) => (
                  panel.metadata?.[key] !== undefined && panel.metadata?.[key] !== "" ? (
                    <span key={key}>
                      <b>{label}</b>
                      {formatPanelValue(panel.metadata[key])}
                    </span>
                  ) : null
                ))}
              </div>
              <div className="result-code-meta">
                {[
                  ["taskType", "任务"],
                  ["model", "模型"],
                  ["userId", "用户"]
                ].map(([key, label]) => (
                  panel.metadata?.[key] !== undefined && panel.metadata?.[key] !== "" ? (
                    <span key={key}>
                      <b>{label}</b>
                      {formatPanelValue(panel.metadata[key])}
                    </span>
                  ) : null
                ))}
              </div>
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
            </div>
          )}

          {panel.kind === "image" && (
            <div className="result-image-panel">
              <div className="result-code-meta">
                {["model", "quality", "aspectRatio", "mode", "size", "batchCount", "provider"].map((key) => (
                  panel.metadata?.[key] !== undefined && panel.metadata?.[key] !== "" ? (
                    <span key={key}>
                      <b>{key}</b>
                      {formatPanelValue(panel.metadata[key])}
                    </span>
                  ) : null
                ))}
              </div>
              {panel.metadata?.prompt && <p>{formatPanelValue(panel.metadata.prompt)}</p>}
              {(panel.fileRefs || []).length > 0 && (
                <div className="result-image-grid">
                  {panel.fileRefs.slice(0, 6).map((file, index) => {
                    const imageUrl = safeResourceUrl(file.previewUrl || file.downloadUrl);
                    const sourceLabel = artifactSourceLabel(file);
                    return (
                      <div className="result-image-card" key={`${panel.id}-image-${index}`}>
                        {imageUrl ? (
                          <a href={imageUrl} target="_blank" rel="noreferrer">
                            <img src={imageUrl} alt={file.title || file.fileName || "generated image"} />
                          </a>
                        ) : (
                          <FileText size={24} />
                        )}
                        <div>
                          <div className="result-artifact-title">
                            <strong>{file.title || file.fileName || "生成图片"}</strong>
                            {sourceLabel && <span className="result-artifact-source">来源 {sourceLabel}</span>}
                          </div>
                          {file.downloadUrl && (
                            <button type="button" onClick={() => onDownloadArtifact?.(file)}>
                              <Download size={14} />
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
            </div>
          )}

          {panel.kind === "multimodal" && (
            <div className="result-multimodal-panel">
              <div className="result-code-meta">
                {["imageCount", "fileCount", "task"].map((key) => (
                  panel.metadata?.[key] !== undefined && panel.metadata?.[key] !== "" ? (
                    <span key={key}>
                      <b>{key}</b>
                      {formatPanelValue(panel.metadata[key])}
                    </span>
                  ) : null
                ))}
              </div>
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
              {(panel.fileRefs || []).length > 0 && (
                <div className="result-file-list compact">
                  {panel.fileRefs.slice(0, 4).map((file, index) => (
                    <div className="result-file-card" key={`${panel.id}-multimodal-file-${index}`}>
                      <FileText size={15} />
                      <div>
                        <strong>{file.title || file.fileName || "文件"}</strong>
                        <span>{artifactMetaLabel(file)}</span>
                      </div>
                      {file.downloadUrl && (
                        <button type="button" onClick={() => onDownloadArtifact?.(file)}>
                          <Download size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {panel.kind === "file" && (
            <div className="result-file-panel">
              {(panel.fileRefs || []).length > 0 && (
                <div className="result-file-list">
                  {panel.fileRefs.slice(0, 6).map((file, index) => {
                    const previewUrl = safeResourceUrl(file.previewUrl || file.downloadUrl);
                    return (
                      <div className="result-file-card" key={`${panel.id}-file-${index}`}>
                        <FileText size={15} />
                        <div>
                          <strong>{file.title || file.fileName || "文件"}</strong>
                          <span>{artifactMetaLabel(file)}</span>
                        </div>
                        {previewUrl && (
                          <a href={previewUrl} target="_blank" rel="noreferrer" aria-label="预览文件">
                            <Globe2 size={14} />
                          </a>
                        )}
                        {file.downloadUrl && (
                          <button type="button" onClick={() => onDownloadArtifact?.(file)}>
                            <Download size={14} />
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
              {panel.content && <pre className="result-panel-content">{panel.content}</pre>}
            </div>
          )}

          {panel.kind === "summary" && panel.content && (
            <pre className="result-panel-content">{panel.content}</pre>
          )}
        </section>
      ))}
    </div>
  );
}

function ArtifactInlinePreview({ preview }) {
  if (!preview?.canPreview) return null;
  const title = preview.title || preview.fileName || "artifact preview";

  if (preview.kind === "image" && preview.url) {
    return (
      <a className="artifact-inline-preview image" href={preview.url} target="_blank" rel="noreferrer">
        <img src={preview.url} alt={title} />
      </a>
    );
  }

  if (preview.kind === "html" && preview.url) {
    return (
      <div className="artifact-inline-preview html">
        <iframe title={title} src={preview.url} sandbox="allow-scripts allow-same-origin allow-forms allow-popups" />
      </div>
    );
  }

  if (preview.kind === "text") {
    return (
      <div className="artifact-inline-preview text">
        {preview.inlineText ? (
          <pre>{preview.inlineText}</pre>
        ) : (
          preview.url && <iframe title={title} src={preview.url} sandbox="allow-same-origin" />
        )}
      </div>
    );
  }

  return null;
}

function TimelineContent({ timeline = [] }) {
  return (
    <div className="timeline-content">
      {timeline.map((item, index) => {
        const statusClass = timelineItemStatus(item);
        const statusLabel = timelineItemStatusLabel(item);
        return (
          <div className="timeline-item" key={`${item.type}-${index}`}>
            <div className={`timeline-dot ${statusClass}`} title={statusLabel} />
            <div className="timeline-item-body">
              {item.type === "thinking" && <div className="timeline-thinking">{item.content}</div>}
              {item.type === "task_analysis" && (
                <div className="timeline-reasoning">
                  <strong>{item.title || "任务分析"}</strong>
                  <span>{item.taskType || "任务"} · {item.difficulty || "中等"} · 预计 {item.estimatedSteps || 0} 步</span>
                  {item.needsMultipleSources && <em>需要多源对比</em>}
                  {item.content && <small>{item.content}</small>}
                </div>
              )}
              {item.type === "mode_selection" && (
                <div className="timeline-reasoning">
                  <strong>{item.executionMode || "ReAct"}</strong>
                  <span>{item.agentType || "chat"} · {item.modeFamily || "react"}</span>
                  {item.content && <small>{item.content}</small>}
                </div>
              )}
              {item.type === "agent_routing" && (
                <div className="timeline-routing">
                  <strong>{item.title || "Agent 协作"}</strong>
                  <div>
                    {(item.selectedAgents || []).map((agentName) => (
                      <span key={agentName}>{agentName}</span>
                    ))}
                  </div>
                  {item.content && <small>{item.content}</small>}
                </div>
              )}
              {item.type === "run" && (
                <div className="timeline-run">
                  <span className="timeline-tool-name">{item.title}</span>
                  <span className={`timeline-inline-status ${statusClass}`}>{statusLabel}</span>
                  {item.content && <small>{item.content}</small>}
                </div>
              )}
              {item.type === "plan" && (
                <div className="timeline-plan">
                  <strong>{item.title || "执行计划"}</strong>
                  {(item.flowStages || []).length > 0 ? (
                    <div className="timeline-flow-stages">
                      {(item.flowStages || []).map((stage, stageIndex) => (
                        <div className="timeline-flow-stage" key={`stage-${stage.stageIndex ?? stageIndex}`}>
                          <small>阶段 {(stage.stageIndex ?? stageIndex) + 1}</small>
                          {(stage.steps || []).map((step, stepIndex) => (
                            <span key={`${step.stepId || stepIndex}-${planStepLabel(step)}`}>
                              {planStepLabel(step)}
                              {planStepMeta(step) && <em>{planStepMeta(step)}</em>}
                            </span>
                          ))}
                        </div>
                      ))}
                    </div>
                  ) : (
                    (item.steps || []).map((step, stepIndex) => (
                      <span key={`${step.stepId || stepIndex}-${planStepLabel(step)}`}>
                        {planStepLabel(step)}
                        {planStepMeta(step) && <em>{planStepMeta(step)}</em>}
                      </span>
                    ))
                  )}
                </div>
              )}
              {item.type === "replan" && (
                <div className="timeline-replan">
                  <strong>{item.title || "动态重规划"}</strong>
                  {item.content && <small>{item.content}</small>}
                  <span>旧计划 {(item.oldPlan || []).length} 步 · 新计划 {(item.newPlan || []).length} 步</span>
                </div>
              )}
              {item.type === "flow" && (
                <div className="timeline-flow">
                  <span className={`timeline-flow-status ${statusClass}`}>
                    阶段 {(item.stageIndex ?? 0) + 1} · {statusLabel}
                  </span>
                  {item.message && <small>{item.message}</small>}
                  {(item.steps || []).map((step, stepIndex) => (
                    <em key={`${step.stepId || stepIndex}-${planStepLabel(step)}`}>
                      {planStepLabel(step)}
                    </em>
                  ))}
                </div>
              )}
              {item.type === "tool" && (
                <div className="timeline-tool">
                  <span>🔧</span>
                  <span className="timeline-tool-name">{item.toolName}</span>
                  <span className={`timeline-inline-status ${statusClass}`}>{statusLabel}</span>
                  {item.detail && <small>{item.detail}</small>}
                  {item.latencyMillis ? <small>{item.latencyMillis} ms</small> : null}
                  {(item.argumentsJson || item.resultJson || item.errorMessage) && (
                    <details className="timeline-tool-io">
                      <summary>输入 / 输出</summary>
                      {item.argumentsJson && <pre>{String(item.argumentsJson).slice(0, 1600)}</pre>}
                      {item.resultJson && <pre>{String(item.resultJson).slice(0, 2400)}</pre>}
                      {item.errorMessage && <pre>{String(item.errorMessage).slice(0, 800)}</pre>}
                    </details>
                  )}
                </div>
              )}
              {item.type === "llm" && (
                <div className="timeline-llm">
                  <span className="timeline-tool-name">{item.modelName}</span>
                  <span className={`timeline-inline-status ${statusClass}`}>{statusLabel}</span>
                  <small>{item.tokens || 0} tokens · {item.latencyMillis || 0} ms</small>
                </div>
              )}
              {item.type === "diagnosis" && (
                <div className="timeline-diagnosis">
                  <strong>{item.title || "运行诊断"} · {item.level || "OK"}</strong>
                  {item.content && <small>{item.content}</small>}
                  {item.metrics && (
                    <div>
                      <span>耗时 {item.metrics.elapsedMs || 0} ms</span>
                      <span>工具 {item.metrics.toolCallCount || 0} 次</span>
                      <span>失败 {item.metrics.failedToolCount || 0} 次</span>
                      <span>重规划 {item.metrics.replanCount || 0} 次</span>
                    </div>
                  )}
                </div>
              )}
              {item.type === "error" && <div className="timeline-error"><AlertTriangle size={14} /><span>{item.message}</span></div>}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function AssistantReasoningPanel({ msg, digest, plannerHistory = [], onToggle }) {
  const timeline = msg.timeline || [];
  const visible = Boolean(digest?.visible || plannerHistory.length || timeline.length);
  if (!visible) return null;
  const open = Boolean(msg.showTimeline);
  const status = digest?.status || "completed";
  const title = status === "running" ? "思考中" : status === "attention" ? "需关注" : "已思考";
  const meta = assistantReasoningMeta(msg, plannerHistory);
  return (
    <section className={`assistant-reasoning ${status} ${open ? "open" : ""}`}>
      <button type="button" className="assistant-reasoning-toggle" onClick={onToggle} aria-expanded={open}>
        <span className="assistant-reasoning-icon" aria-hidden="true">
          {status === "running" ? <Loader2 size={14} /> : status === "attention" ? <AlertTriangle size={14} /> : <Check size={14} />}
        </span>
        <span className="assistant-reasoning-title">{title}</span>
        {meta && <span className="assistant-reasoning-meta">（{meta}）</span>}
        <span className="assistant-reasoning-caret">{open ? "⌃" : "⌄"}</span>
      </button>
      {open && (
        <div className="assistant-reasoning-body">
          {digest?.metrics?.length > 0 && (
            <div className="assistant-reasoning-metrics">
              {digest.metrics.map((metric) => (
                <span className={metric.tone || "normal"} key={metric.key}>
                  <b>{metric.value}</b>{metric.label}
                </span>
              ))}
            </div>
          )}
          {digest?.highlights?.length > 0 && (
            <div className="assistant-reasoning-highlights">
              {digest.highlights.map((item, index) => <p key={`${item}-${index}`}>{item}</p>)}
            </div>
          )}
          {plannerHistory.length > 0 && (
            <div className="assistant-reasoning-plans">
              <span className="assistant-reasoning-subtitle">计划历史</span>
              {plannerHistory.map((version, index) => (
                <div className={`assistant-reasoning-plan ${version.latest ? "latest" : ""}`} key={version.id}>
                  <strong>{index + 1}. {version.title}</strong>
                  <small>
                    第 {version.revision || index + 1} 版
                    {" · "}
                    {version.stageCount > 0 ? `${version.stageCount} 阶段 · ` : ""}
                    {version.stepCount} 步{version.flowUpdates > 0 ? `，${version.flowUpdates} 次更新` : ""}
                    {" · "}
                    {version.status}
                  </small>
                  {version.replanReason && <em>原因：{version.replanReason}</em>}
                  {version.summary && <em>{version.summary}</em>}
                </div>
              ))}
            </div>
          )}
          {timeline.length > 0 && <TimelineContent timeline={timeline} />}
        </div>
      )}
    </section>
  );
}

function MessageItem({
  msg,
  copied,
  isSending,
  isLast,
  onCopy,
  onEditUser,
  onRetryAssistant,
  onToggleTimeline,
  onToggleReference,
  onRecommendClick,
  onDownloadArtifact
}) {
  const isUser = msg.role === "user";
  const plannerHistory = !isUser ? buildPlannerHistory(msg.timeline || []) : [];
  const runDigest = !isUser ? buildAgentRunDigest(msg) : null;
  const [previewArtifactKey, setPreviewArtifactKey] = useState("");
  const [editingUser, setEditingUser] = useState(false);
  const [editingDraft, setEditingDraft] = useState(msg.content || "");
  useEffect(() => {
    if (!editingUser) {
      setEditingDraft(msg.content || "");
    }
  }, [editingUser, msg.content]);
  const submitUserEdit = () => {
    const nextText = editingDraft.trim();
    if (!nextText || nextText === msg.content || isSending) {
      setEditingUser(false);
      setEditingDraft(msg.content || "");
      return;
    }
    setEditingUser(false);
    onEditUser?.(msg, nextText);
  };
  return (
    <div className={`message ${msg.role}`}>
      <div className="message-content">
        {isUser ? (
          <div className="user-message-wrap">
            {editingUser ? (
              <div className="user-message-editor">
                <textarea
                  value={editingDraft}
                  autoFocus
                  rows={Math.min(8, Math.max(2, editingDraft.split("\n").length))}
                  onChange={(event) => setEditingDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
                      submitUserEdit();
                    }
                    if (event.key === "Escape") {
                      setEditingUser(false);
                      setEditingDraft(msg.content || "");
                    }
                  }}
                />
                <div className="user-message-editor-actions">
                  <button type="button" onClick={() => {
                    setEditingUser(false);
                    setEditingDraft(msg.content || "");
                  }}>取消</button>
                  <button type="button" className="primary" disabled={isSending || !editingDraft.trim()} onClick={submitUserEdit}>发送</button>
                </div>
              </div>
            ) : (
              <div className="user-message">
                {msg.file && <span className="file-attachment"><Paperclip size={14} />{msg.fileName}</span>}
                <div>{msg.content}</div>
              </div>
            )}
            {!editingUser && (
              <div className="message-actions user-actions">
                <button className="message-icon-btn" title="复制" onClick={() => onCopy(msg)}>
                  {copied ? <Check size={15} /> : <Copy size={15} />}
                </button>
                <button
                  className="message-icon-btn"
                  title="编辑并重试"
                  disabled={isSending}
                  onClick={() => {
                    setEditingDraft(msg.content || "");
                    setEditingUser(true);
                  }}
                >
                  <Pencil size={15} />
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="ai-message">
            <AssistantReasoningPanel
              msg={msg}
              digest={runDigest}
              plannerHistory={plannerHistory}
              onToggle={() => onToggleTimeline(msg.id)}
            />

            <MarkdownRenderer content={msg.content} />
            {isSending && isLast && (
              <div className="thinking-loading"><span className="dot" /><span className="dot" /><span className="dot" /></div>
            )}

            <ResultPanelList panels={msg.resultPanels || []} onDownloadArtifact={onDownloadArtifact} />

            {msg.reference?.length > 0 && (
              <div className="reference-section">
                <button className="reference-header" onClick={() => onToggleReference(msg.id)}>
                  <span className="reference-icon-wrapper">📎</span>
                  <span className="reference-title">参考来源({msg.reference.length})</span>
                  <span>{msg.showReference ? "⌃" : "⌄"}</span>
                </button>
                {msg.showReference && (
                  <div className="reference-content">
                    {msg.reference.map((ref, index) => (
                      <div className="reference-link" key={`${ref.title}-${index}`}>
                        <div className="ref-icon">→</div>
                        <div className="ref-info">
                          {safeExternalUrl(ref.url) ? (
                            <a className="ref-title-text" href={safeExternalUrl(ref.url)} target="_blank" rel="noreferrer">
                              <strong>{ref.title || ref.url || "来源"}</strong>
                            </a>
                          ) : (
                            <div className="ref-title-text">{ref.title || "来源"}</div>
                          )}
                          {ref.url && <div className="ref-url-text">{ref.url}</div>}
                          {ref.text && <div className="ref-snippet-text">{ref.text}</div>}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {msg.artifacts?.length > 0 && (
              <div className="artifact-section">
                {msg.artifacts.map((artifact, index) => {
                  const preview = buildArtifactPreviewModel(artifact);
                  const previewKey = `${preview.fileName || preview.title || "artifact"}-${index}`;
                  const isPreviewOpen = previewArtifactKey === previewKey;
                  return (
                    <div className="artifact-card" key={`${preview.title}-${index}`}>
                      <div className="artifact-title">
                        <div>
                          <strong>{preview.title}</strong>
                          <small>{preview.fileName}</small>
                        </div>
                        <span>{preview.type}</span>
                      </div>
                      <div className="artifact-actions">
                        {preview.canPreview && (
                          <button
                            type="button"
                            className="artifact-preview-toggle"
                            aria-expanded={isPreviewOpen}
                            onClick={() => setPreviewArtifactKey(isPreviewOpen ? "" : previewKey)}
                          >
                            <Eye size={15} />
                            <span>{isPreviewOpen ? "收起" : "预览"}</span>
                          </button>
                        )}
                        {artifact.downloadUrl && (
                          <button type="button" className="artifact-download" onClick={() => onDownloadArtifact(artifact)}>
                            <Download size={15} />
                            <span>下载 {artifact.type || "文件"}</span>
                          </button>
                        )}
                      </div>
                      {isPreviewOpen && <ArtifactInlinePreview preview={preview} />}
                      {!isPreviewOpen && <pre>{artifact.fileName || artifact.content}</pre>}
                      {artifact.fileSize ? <small>{formatFileSize(artifact.fileSize)}</small> : null}
                    </div>
                  );
                })}
              </div>
            )}

            {msg.recommend?.length > 0 && (
              <div className="recommend-section">
                {msg.recommend.map((item, index) => (
                  <button type="button" key={`${item}-${index}`} onClick={() => onRecommendClick?.(item)}>
                    {item}
                  </button>
                ))}
              </div>
            )}

            <div className="message-actions assistant-actions">
              <button className="message-icon-btn" title="复制" onClick={() => onCopy(msg)}>
                {copied ? <Check size={15} /> : <Copy size={15} />}
              </button>
              <button
                className="message-icon-btn"
                title="重试"
                disabled={isSending}
                onClick={() => onRetryAssistant?.(msg)}
              >
                <RotateCcw size={15} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function AuthDialog({ mode, setMode, form, setForm, error, loading = false, onSubmit, onDemoLogin, onClose }) {
  const passwordReady = String(form.password || "").length >= 6;
  return (
    <div className="modal-overlay">
      <form className="auth-dialog" onSubmit={onSubmit}>
        <button type="button" className="modal-close" onClick={onClose} disabled={loading}><X size={18} /></button>
        <img className="auth-logo" src="/bear-doctor-logo.png" alt="熊博士 Agent" />
        <h3>{mode === "login" ? "登录熊博士 Agent" : "创建用户账号"}</h3>
        <p className="auth-tip">这里是普通用户入口；后台管理请使用运营端账号认证。</p>
        <div className="auth-switch">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} disabled={loading}>登录</button>
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")} disabled={loading}>注册</button>
        </div>
        <label className="auth-field">
          <span>账号</span>
          <input name="username" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="请输入账号" autoComplete="username" disabled={loading} required />
        </label>
        <label className="auth-field">
          <span>密码</span>
          <input name="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} type="password" placeholder="请输入密码" autoComplete={mode === "login" ? "current-password" : "new-password"} disabled={loading} required />
        </label>
        {mode === "register" && (
          <>
            <label className="auth-field">
              <span>昵称</span>
              <input name="nickname" value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} placeholder="可选" autoComplete="nickname" disabled={loading} />
            </label>
            <label className="auth-field">
              <span>邮箱</span>
              <input name="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="可选" autoComplete="email" disabled={loading} />
            </label>
            <div className={`auth-password-rule ${passwordReady ? "ready" : ""}`}>密码至少 6 位</div>
          </>
        )}
        {error && <div className="auth-error">{error}</div>}
        <button className="auth-submit" type="submit" disabled={loading}>
          {loading ? "处理中..." : (mode === "login" ? "登录" : "注册并登录")}
        </button>
        {mode === "login" && (
          <button className="auth-demo" type="button" onClick={onDemoLogin} disabled={loading}>
            使用演示账号
          </button>
        )}
      </form>
    </div>
  );
}

function ModelConfigDialog({ config, onSave, onClose }) {
  const [draft, setDraft] = useState(() => getModelConfig());

  useEffect(() => {
    setDraft({ ...getModelConfig(), ...config });
  }, [config]);

  const update = (field, value) => {
    setDraft((prev) => ({ ...prev, [field]: value }));
  };

  const updateTextBaseUrl = (value) => {
    setDraft((prev) => ({ ...prev, baseUrl: value, textBaseUrl: value }));
  };

  const updateTextApiKey = (value) => {
    setDraft((prev) => ({ ...prev, apiKey: value, textApiKey: value }));
  };

  const updateTextModel = (value) => {
    setDraft((prev) => ({ ...prev, model: value, textModel: value }));
  };

  const submit = (event) => {
    event.preventDefault();
    onSave(draft);
  };

  return (
    <div className="modal-overlay model-config-overlay">
      <form className="model-config-dialog" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
        <div className="model-config-head">
          <Settings size={20} />
          <h3>模型配置</h3>
        </div>
        <label className="model-config-toggle">
          <input
            type="checkbox"
            checked={Boolean(draft.enabled)}
            onChange={(event) => update("enabled", event.target.checked)}
          />
          <span>使用自定义模型</span>
        </label>
        <section className="model-config-section">
          <strong>文本模型</strong>
          <label>
            <span>API 地址</span>
            <input
              value={draft.textBaseUrl || draft.baseUrl || ""}
              onChange={(event) => updateTextBaseUrl(event.target.value)}
              placeholder="https://dashscope.aliyuncs.com/compatible-mode"
              disabled={!draft.enabled}
            />
          </label>
          <label>
            <span>API 密钥</span>
            <input
              value={draft.textApiKey || draft.apiKey || ""}
              onChange={(event) => updateTextApiKey(event.target.value)}
              type="password"
              placeholder={draft.textKeyMasked || draft.keyMasked ? "留空则继续使用已保存密钥" : "sk-..."}
              disabled={!draft.enabled}
            />
            {(draft.textKeyMasked || draft.keyMasked) && (
              <em className="model-config-key-mask">已保存：{draft.textKeyMasked || draft.keyMasked}</em>
            )}
          </label>
          <label>
            <span>默认文本模型</span>
            <input
              value={draft.textModel || draft.model || ""}
              onChange={(event) => updateTextModel(event.target.value)}
              placeholder="qwen3.7-plus"
              disabled={!draft.enabled}
            />
          </label>
        </section>
        <section className="model-config-section">
          <strong>图像模型</strong>
          <label>
            <span>API 地址</span>
            <input
              value={draft.imageBaseUrl || ""}
              onChange={(event) => update("imageBaseUrl", event.target.value)}
              placeholder="https://api.openai.com"
              disabled={!draft.enabled}
            />
          </label>
          <label>
            <span>API 密钥</span>
            <input
              value={draft.imageApiKey || ""}
              onChange={(event) => update("imageApiKey", event.target.value)}
              type="password"
              placeholder={draft.imageKeyMasked ? "留空则继续使用已保存密钥" : "sk-..."}
              disabled={!draft.enabled}
            />
            {draft.imageKeyMasked && <em className="model-config-key-mask">已保存：{draft.imageKeyMasked}</em>}
          </label>
          <label>
            <span>默认图像模型</span>
            <input
              value={draft.imageModel || ""}
              onChange={(event) => update("imageModel", event.target.value)}
              placeholder="gpt-image-2"
              disabled={!draft.enabled}
            />
          </label>
        </section>
        <div className="model-config-actions">
          <button type="button" onClick={onClose}>取消</button>
          <button type="submit" className="primary">保存</button>
        </div>
      </form>
    </div>
  );
}

function RechargeDialog({
  quota,
  membership,
  billingPolicy,
  flows,
  orders,
  ordersLoading,
  packages,
  buyingKey,
  activeTab,
  setActiveTab,
  groupPreviewPackage,
  groupMarketConfig,
  groupTeamsLoading,
  currentUserId,
  onBuy,
  onOpenGroupPreview,
  onBackToPackages,
  onRefresh,
  onPayOrder,
  onClose
}) {
  const [now, setNow] = useState(() => Date.now());
  const formatMoney = (value) => Number(value || 0).toFixed(2);
  const groupTeamSizeLabel = (product = {}) => {
    const rawSize = Number(product.teamSize || 0);
    if (rawSize === 3 || rawSize === 5) return rawSize;
    const text = `${product.goodsId || ""} ${product.goodsName || ""}`.toUpperCase();
    if (text.includes("G10002") || text.includes("G10005") || text.includes("长文档") || text.includes("深度任务")) {
      return 5;
    }
    return 3;
  };
  const groupPriceLabel = (product = {}) => formatMoney(product.groupPrice || product.originPrice);
  const statusLabel = (status) => ({
    CREATE: "已创建",
    PAY_WAIT: "待支付",
    PAY_SUCCESS: "已支付",
    GROUP_SETTLED: "已成团",
    DEAL_DONE: "已到账",
    CLOSED: "已关闭",
    REFUNDED: "已退款"
  }[status] || status || "-");
  const maskUserId = (userId = "") => {
    const value = String(userId || "");
    if (value.length <= 4) return value || "-";
    return `${value.slice(0, 2)}****${value.slice(-2)}`;
  };

  useEffect(() => {
    if (!groupPreviewPackage) return undefined;
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [groupPreviewPackage]);

  const parseTime = (value) => {
    if (!value) return Number.NaN;
    if (Array.isArray(value)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = value;
      return new Date(year, Number(month) - 1, day, hour, minute, second).getTime();
    }
    if (typeof value === "number") return value;
    const text = String(value).trim();
    const normalized = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(text) ? text.replace(" ", "T") : text;
    return new Date(normalized).getTime();
  };

  const formatCountdown = (endTime, fallback = "-") => {
    const endAt = parseTime(endTime);
    if (!Number.isFinite(endAt)) return fallback || "-";
    const diff = endAt - now;
    if (!Number.isFinite(diff) || diff <= 0) return "00:00:00";
    const totalSeconds = Math.floor(diff / 1000);
    const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, "0");
    const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, "0");
    const seconds = String(totalSeconds % 60).padStart(2, "0");
    return `${hours}:${minutes}:${seconds}`;
  };
  const isExpired = (endTime) => {
    const endAt = parseTime(endTime);
    return Number.isFinite(endAt) && endAt - now <= 0;
  };
  const marketGoods = groupMarketConfig?.goods || {};
  const teamList = groupMarketConfig?.teamList || [];
  const teamSize = groupTeamSizeLabel({
    ...groupPreviewPackage,
    teamSize: groupMarketConfig?.discount?.target || groupPreviewPackage?.teamSize || teamList[0]?.targetCount
  });
  const isMembershipPreview = groupPreviewPackage?.productType === "MEMBERSHIP_PLAN";
  const quotaAmount = Number(groupPreviewPackage?.quotaAmount || 0).toFixed(0);
  const isGroupBuying = Boolean(groupPreviewPackage && buyingKey.startsWith(`${groupPreviewPackage.goodsId}-group`));
  const isDirectBuying = Boolean(groupPreviewPackage && buyingKey === `${groupPreviewPackage.goodsId}-direct`);
  const previewProduct = groupPreviewPackage ? {
    ...groupPreviewPackage,
    activityId: groupMarketConfig?.activityId || groupPreviewPackage.activityId,
    originPrice: marketGoods.originalPrice || groupPreviewPackage.originPrice,
    groupPrice: marketGoods.payPrice || groupPreviewPackage.groupPrice
  } : null;
  const memberActive = Boolean(membership?.active);
  const memberRemaining = Number(membership?.remainingMonthlyQuota || 0).toFixed(2);
  const memberTotal = Number(membership?.monthlyQuota || 0).toFixed(2);
  const promptCost = Number(billingPolicy?.platformPromptCostPer1k || 0).toFixed(2);
  const completionCost = Number(billingPolicy?.platformCompletionCostPer1k || 0).toFixed(2);
  const customRate = Math.round(Number(billingPolicy?.customModelServiceRate || 0) * 100);
  const blendedCostPer1k = Number(billingPolicy?.platformPromptCostPer1k || 0) * 0.7
    + Number(billingPolicy?.platformCompletionCostPer1k || 0) * 0.3;
  const membershipPlans = (packages || []).filter((pkg) => pkg.productType === "MEMBERSHIP_PLAN");
  const quotaPackages = (packages || []).filter((pkg) => pkg.productType !== "MEMBERSHIP_PLAN");
  const currentPlanCode = memberActive ? (membership?.planCode || "FREE") : "FREE";
  const currentPaidPlan = membershipPlans.find((plan) => plan.goodsId === currentPlanCode);
  const currentPlanPrice = Number(currentPaidPlan?.originPrice || 0);
  const formatCycleEnd = (value) => {
    const time = parseTime(value);
    if (!Number.isFinite(time)) return "";
    return new Date(time).toLocaleDateString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" });
  };
  const freePlan = {
    goodsId: "FREE",
    goodsName: "免费版",
    originPrice: 0,
    quotaAmount: 0,
    productType: "FREE_PLAN",
    specSummary: "适合轻量体验，按平台模型和额度包规则使用。"
  };
  const memberPlanCards = [freePlan, ...membershipPlans];
  const membershipPlanToneClass = (plan, current) => {
    if (!current) return "";
    if (plan.productType === "FREE_PLAN") return "current current-free";
    const text = `${plan.goodsId || ""} ${plan.goodsName || ""}`.toUpperCase();
    if (text.includes("PRO")) return "current current-pro";
    if (text.includes("PLUS")) return "current current-plus";
    return "current current-paid";
  };
  const memberPlanFeatures = (plan) => {
    if (plan.productType === "FREE_PLAN") {
      return ["基础对话体验", "平台模型按量扣费", "可单独购买额度包"];
    }
    const quota = Number(plan.quotaAmount || 0).toFixed(0);
    const isPro = String(plan.goodsId || "").includes("PRO");
    return [
      `每月 ${quota} 点会员额度`,
      "自定义模型会员免费",
      isPro ? "适合深度任务、PPT 和图像生成" : "适合文件问答、PPT 和常用 Skill"
    ];
  };
  const estimatedMixedTokensLabel = (quotaAmountValue) => {
    const quotaValue = Number(quotaAmountValue || 0);
    if (!Number.isFinite(quotaValue) || quotaValue <= 0 || blendedCostPer1k <= 0) {
      return "按实际模型 token 消耗扣减";
    }
    const tokens = Math.floor((quotaValue / blendedCostPer1k) * 1000);
    if (tokens >= 1000000) {
      return `约 ${(tokens / 1000000).toFixed(1)}M 混合 token`;
    }
    if (tokens >= 10000) {
      return `约 ${Math.floor(tokens / 10000)} 万混合 token`;
    }
    return `约 ${tokens.toLocaleString("zh-CN")} 混合 token`;
  };

  if (groupPreviewPackage) {
    return (
      <div className="modal-overlay">
        <div className="recharge-dialog group-detail-dialog">
          <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
          <div className="group-detail-topbar">
            <button type="button" className="group-back" onClick={onBackToPackages}>
              <ArrowLeft size={16} />
            </button>
            <strong>{isMembershipPreview ? "会员拼团详情" : "额度包详情"}</strong>
          </div>

          <div className="group-detail-main">
            <div className="group-product-icon">⚙</div>
            <h3>{groupPreviewPackage.goodsName || `可调用次数 - ${quotaAmount} 点`}</h3>
            <p>{groupPreviewPackage.specSummary || `执行任务时按模型消耗扣减，可用额度 ${quotaAmount} 点`}</p>

            <div className="group-team-panel">
              <h4>可加入拼团</h4>
              {groupTeamsLoading && <div className="group-empty">拼团列表读取中...</div>}
              {!groupTeamsLoading && teamList.length === 0 && (
                <div className="group-empty">暂无可加入队伍，可以先自己开团。</div>
              )}
              {!groupTeamsLoading && teamList.map((team) => {
                const remaining = team.progress?.remainingCount ?? Math.max(Number(team.targetCount || 0) - Number(team.lockCount || 0), 0);
                const complete = team.progress?.completeCount ?? team.completeCount ?? 0;
                const isMine = team.userId && currentUserId && team.userId === currentUserId;
                const expired = isExpired(team.validEndTime);
                return (
                  <div className="group-team-card" key={`${team.teamId}-${team.outTradeNo || ""}`}>
                    <div>
                      <b>{isMine ? "我的进行中团" : (team.userId ? `${maskUserId(team.userId)} 的团` : "其他用户的团")}</b>
                      <span>{team.teamId}</span>
                    </div>
                    <div>
                      <b>还差 {remaining} 人</b>
                      <span>已支付 {complete} 人，已占位 {team.lockCount || 0} 人</span>
                    </div>
                    <span>剩余时间: {formatCountdown(team.validEndTime, team.validTimeCountdown)}</span>
                    <button
                      type="button"
                      onClick={() => onBuy(previewProduct, "group", { teamId: team.teamId })}
                      disabled={Boolean(buyingKey) || remaining <= 0 || expired}
                    >
                      <UserPlus size={15} /> {buyingKey === `${groupPreviewPackage.goodsId}-group-${team.teamId}` ? "处理中" : "加入拼团"}
                    </button>
                  </div>
                );
              })}
            </div>

            <div className="group-detail-actions">
              <button type="button" onClick={() => onBuy(previewProduct, "direct")} disabled={Boolean(buyingKey)}>
                <CreditCard size={16} /> {isDirectBuying ? "处理中" : `直接购买 ￥${formatMoney(previewProduct.originPrice)}`}
              </button>
              <button className="primary" type="button" onClick={() => onBuy(previewProduct, "group")} disabled={Boolean(buyingKey)}>
                <UserPlus size={16} /> {isGroupBuying ? "处理中" : `自己开团 ￥${formatMoney(previewProduct.groupPrice)}`}
              </button>
            </div>
            <div className="group-detail-tip">
              {teamSize} 人成团，支付成功后需等待成团，成团后{isMembershipPreview ? "会员权益生效" : "额度到账"}。
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay">
      <div className="recharge-dialog recharge-upgrade-dialog">
        <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
        <div className="upgrade-title">
          <h3>升级套餐</h3>
          <p>选择额度包后可直接购买，也可以发起拼团；拼团成团后额度到账。</p>
        </div>
        <div className="upgrade-account-summary">
          <div className="upgrade-account-main">
            <div className="upgrade-avatar">{String(currentUserId || quota?.userId || "U").slice(0, 1).toUpperCase()}</div>
            <div>
              <strong>{maskUserId(currentUserId || quota?.userId)}</strong>
              <span>{memberActive ? `会员额度 ${memberRemaining} / ${memberTotal} 点` : "未开通会员"}</span>
            </div>
          </div>
          <div className="upgrade-account-stats">
            <div>
              <span>当前余额</span>
              <b>{Number(quota?.quotaBalance || 0).toFixed(2)} 点</b>
            </div>
            <div>
              <span>已用</span>
              <b>{Number(quota?.usedQuota || 0).toFixed(2)}</b>
            </div>
            <div>
              <span>冻结</span>
              <b>{Number(quota?.frozenQuota || 0).toFixed(2)}</b>
            </div>
          </div>
          <button className="refresh-btn upgrade-refresh" onClick={onRefresh}>刷新</button>
        </div>
        <div className="upgrade-billing-note">
          1 点约等于 0.01 元；参考 Qwen3.7-Plus 官方价格，平台按输入 {promptCost} 点/千 token、输出 {completionCost} 点/千 token 计费；自定义模型按 {customRate || 10}% 收取服务费。
        </div>
        <div className="recharge-head">
          <div>
            <h3>额度中心</h3>
            <p>购买额度后可用于对话、文件问答、生成 PPT、图像生成、深度任务和 Skill 调用</p>
          </div>
          <button className="refresh-btn" onClick={onRefresh}>刷新</button>
        </div>
        <div className="quota-card">
          <div>
            <span>当前余额</span>
            <strong>{Number(quota?.quotaBalance || 0).toFixed(2)} 点</strong>
          </div>
          <div>
            <span>已用</span>
            <b>{Number(quota?.usedQuota || 0).toFixed(2)}</b>
          </div>
          <div>
            <span>冻结</span>
            <b>{Number(quota?.frozenQuota || 0).toFixed(2)}</b>
          </div>
        </div>
        <div className="membership-card">
          <div>
            <span>{memberActive ? "会员额度" : "会员"}</span>
            <strong>{memberActive ? `${memberRemaining} / ${memberTotal} 点` : "未开通"}</strong>
          </div>
          <p>
            当前计费：1 点约等于 0.01 元；输入 {promptCost} 点/千 token，输出 {completionCost} 点/千 token，自定义模型按 {customRate || 10}% 计费。
          </p>
        </div>
        <div className="recharge-tabs">
          <button type="button" className={activeTab === "packages" ? "active" : ""} onClick={() => setActiveTab("packages")}>
            <Wallet size={15} /> 会员/额度包
          </button>
          <button type="button" className={activeTab === "orders" ? "active" : ""} onClick={() => setActiveTab("orders")}>
            <CreditCard size={15} /> 订单/拼团
          </button>
        </div>

        {activeTab === "packages" && (
          <>
            <section className="upgrade-section">
              <div className="upgrade-section-head">
                <strong>会员</strong>
                <span>{memberActive ? `当前 ${membership?.planName || "会员"}，有效期至 ${formatCycleEnd(membership?.cycleEndTime) || "-"}` : "开通后获得会员月额度和自定义模型权益"}</span>
              </div>
              <div className="membership-plan-grid">
                {memberPlanCards.map((plan) => {
                  const isFree = plan.productType === "FREE_PLAN";
                  const isCurrent = plan.goodsId === currentPlanCode || (!memberActive && isFree);
                  const planPrice = Number(plan.originPrice || 0);
                  const isLowerPlan = memberActive && !isFree && currentPlanPrice > 0 && planPrice < currentPlanPrice;
                  const disabled = Boolean(buyingKey) || isCurrent || isFree || isLowerPlan;
                  const canGroupBuy = !isFree;
                  const planName = plan.goodsName || "会员套餐";
                  const actionText = isCurrent
                    ? "当前套餐"
                    : isLowerPlan
                      ? "低于当前套餐"
                      : buyingKey === `${plan.goodsId}-direct`
                        ? "处理中"
                        : `升级至 ${planName.replace("会员", "").trim() || planName}`;
                  return (
                    <article className={`membership-plan-card ${membershipPlanToneClass(plan, isCurrent)}`} key={plan.goodsId}>
                      <div className="membership-plan-name">
                        <h4>{planName}</h4>
                        {isCurrent && <span>当前</span>}
                      </div>
                      <div className="membership-plan-price">
                        <small>￥</small>
                        <strong>{formatMoney(plan.originPrice)}</strong>
                        <em>/ 月</em>
                      </div>
                      <p>{plan.specSummary}</p>
                      <div className="pkg-token-estimate">{estimatedMixedTokensLabel(plan.quotaAmount)}</div>
                      <div className="membership-plan-actions">
                        <button type="button" onClick={() => onBuy(plan, "direct")} disabled={disabled}>
                          {actionText}
                        </button>
                        {canGroupBuy && (
                          <button type="button" onClick={() => onOpenGroupPreview(plan)} disabled={Boolean(buyingKey) || isCurrent || isLowerPlan}>
                            {buyingKey === `${plan.goodsId}-group` ? "处理中" : `${groupTeamSizeLabel(plan)} 人拼团 ¥${groupPriceLabel(plan)}`}
                          </button>
                        )}
                      </div>
                      <ul className="plan-features">
                        {memberPlanFeatures(plan).map((feature) => (
                          <li key={feature}><Check size={14} /> {feature}</li>
                        ))}
                      </ul>
                    </article>
                  );
                })}
                {membershipPlans.length === 0 && <div className="empty-package">暂无会员套餐</div>}
              </div>
            </section>

            <section className="upgrade-section">
              <div className="upgrade-section-head">
                <strong>额度包</strong>
                <span>直接购买立即到账；拼团支付成功后等待成团到账</span>
              </div>
              <div className="package-grid upgrade-plan-grid">
                {quotaPackages.map((pkg) => (
                    <article className="quota-package upgrade-plan-card" key={pkg.goodsId}>
                      <h4>{pkg.goodsName}</h4>
                      <p>{pkg.specSummary}</p>
                      <div className="pkg-amount">{Number(pkg.quotaAmount || 0).toFixed(0)} 点</div>
                      <div className="pkg-token-estimate">{estimatedMixedTokensLabel(pkg.quotaAmount)}</div>
                      <ul className="plan-features">
                        <li><Check size={14} /> 对话、文件问答和 Skill 调用</li>
                        <li><Check size={14} /> 生成 PPT、图像和深度任务</li>
                        <li><Check size={14} /> 支付后自动记录额度流水</li>
                      </ul>
                      <div className="pkg-actions">
                        <button onClick={() => onBuy(pkg, "direct")} disabled={Boolean(buyingKey)}>
                          ￥{Number(pkg.originPrice || 0).toFixed(2)}
                        </button>
                        <button className="group" onClick={() => onOpenGroupPreview(pkg)} disabled={Boolean(buyingKey)}>
                          {buyingKey === `${pkg.goodsId}-group` ? "处理中" : `${groupTeamSizeLabel(pkg)} 人团 ￥${groupPriceLabel(pkg)}`}
                        </button>
                      </div>
                    </article>
                ))}
                {quotaPackages.length === 0 && <div className="empty-package">暂无可购买额度包</div>}
              </div>
            </section>
            <details className="flow-details">
              <summary>最近额度流水</summary>
              {(flows || []).slice(0, 8).map((flow) => (
                <div className="flow-row" key={flow.flowId}>
                  <span>{flow.remark || flow.flowType}</span>
                  <b>{Number(flow.quotaAmount || 0).toFixed(2)}</b>
                </div>
              ))}
              {(!flows || flows.length === 0) && <p>暂无流水</p>}
            </details>
          </>
        )}

        {activeTab === "orders" && (
          <section className="order-details order-tab-panel">
            <div className="order-tab-head">
              <strong>我的订单</strong>
              <span>待支付订单可继续支付；拼团支付成功后等待成团到账。</span>
            </div>
            {ordersLoading && <p>订单读取中...</p>}
            {!ordersLoading && (!orders || orders.length === 0) && <p>暂无订单</p>}
            {!ordersLoading && (orders || []).slice(0, 10).map((order) => {
              const canPay = order.status === "CREATE" || order.status === "PAY_WAIT";
              return (
                <div className="order-row" key={order.orderId}>
                  <div>
                    <strong>{order.productName || order.productId || "额度订单"}</strong>
                    <span>{order.orderId}</span>
                  </div>
                  <div>
                    <b>{order.marketType === 1 ? "拼团" : "直购"}</b>
                    <span>￥{formatMoney(order.payAmount || order.totalAmount)}</span>
                  </div>
                  <div>
                    <em>{order.displayStatus || statusLabel(order.status)}</em>
                    <span>{order.orderTime ? String(order.orderTime).replace("T", " ") : ""}</span>
                  </div>
                  {canPay && (
                    <button type="button" onClick={() => onPayOrder(order)} disabled={Boolean(buyingKey)}>
                      {buyingKey === `pay-${order.orderId}` ? "处理中" : "支付"}
                    </button>
                  )}
                </div>
              );
            })}
          </section>
        )}
      </div>
    </div>
  );
}

function PaymentConfirmDialog({ payment, buyingKey, onConfirm, onCancel }) {
  const amount = Number(payment?.amount || 0).toFixed(2);
  const isGroupOrder = Number(payment?.marketType) === 1;
  const isMembershipOrder = payment?.productType === "MEMBERSHIP_PLAN";
  const paying = buyingKey === `pay-${payment?.orderId}`;

  return (
    <div className="modal-overlay payment-overlay">
      <div className="payment-confirm-dialog">
        <button type="button" className="modal-close" onClick={onCancel} disabled={paying}><X size={18} /></button>
        <div className="payment-icon">
          <CreditCard size={24} />
        </div>
        <h3>进入支付宝支付</h3>
        <p>{isGroupOrder
          ? (isMembershipOrder ? "支付完成后先等待成团，成团后会员才会生效。" : "支付完成后先等待成团，成团后额度才会到账。")
          : isMembershipOrder
            ? "支付完成并回调成功后会员会自动生效。"
            : "支付完成并回调成功后额度会自动到账。"}</p>
        <div className="payment-summary">
          <div>
            <span>订单</span>
            <strong>{payment?.productName || "额度订单"}</strong>
          </div>
          <div>
            <span>订单号</span>
            <strong>{payment?.orderId}</strong>
          </div>
          {isGroupOrder && payment?.teamId && (
            <div>
              <span>拼团</span>
              <strong>{payment.teamId}</strong>
            </div>
          )}
          <div>
            <span>金额</span>
            <b>￥{amount}</b>
          </div>
        </div>
        <div className="payment-confirm-actions">
          <button type="button" onClick={onCancel} disabled={paying}>取消</button>
          <button type="button" className="primary" onClick={onConfirm} disabled={paying}>
            {paying ? <Loader2 size={16} className="spin" /> : <CreditCard size={16} />}
            {paying ? "处理中" : "去支付宝支付"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default App;
