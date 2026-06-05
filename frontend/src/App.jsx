import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import {
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  BookOpen,
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
  Paperclip,
  Plus,
  RotateCcw,
  Send,
  Settings,
  ShieldCheck,
  Square,
  Trash2,
  UserPlus,
  Wallet,
  X
} from "lucide-react";
import AdminDashboard from "./components/AdminDashboard";
import AgentAdminPanel from "./components/AgentAdminPanel";
import McpManagementPanelV2 from "./components/McpManagementPanelV2";
import ThemeToggle from "./components/ThemeToggle";
import {
  OUTPUT_KIND_LABELS,
  TOOL_LABELS,
  WORKSPACE_PROMPTS,
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
  visibleAgentExecutionModes,
  visibleCapabilityMatrix as buildVisibleCapabilityMatrix,
  visibleToolCatalogGroups,
  visibleToolRuntimeReadiness,
  workspaceAcceptsFile,
  workspaceCapabilityStatus,
  workspaceDisplayProfile,
  workspaceToolReadiness,
  workspaceServiceProfile,
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
import {
  mergeArtifacts,
  mergeResultPanels,
  replayEventsToArtifacts,
  replayEventsToResultPanels,
  runDetailToResultPanels,
  toUiArtifact,
  toolResultArtifacts,
  toolResultPanels
} from "./taskArtifacts";
import { normalizeFileUrlForBrowser } from "./fileUrl";
import { buildArtifactPreviewModel } from "./artifactPreview";
import { OUTPUT_STYLE_OPTIONS, outputStylePayload } from "./outputStyles";
import {
  cacheMcpTools,
  callMcpTool,
  createDirectOrder,
  deleteAcademicSession,
  deleteKnowledgeDocument,
  discoverMcpTools,
  downloadAcademicArtifact,
  enableMcpServer,
  exportMcpState,
  generateWorkspaceImage,
  getKnowledgeDocumentFullContent,
  getAdminAuth,
  getKnowledgeFragments,
  getKnowledgeDocuments,
  getModelConfig,
  getQuotaSummary,
  getSessionId,
  getUserAuth,
  importMcpState,
  lockMarketPayOrder,
  login,
  logout,
  modelConfigReady,
  mockPaySuccess,
  normalizeApiMessage,
  queryAgentCapabilities,
  queryAcademicReplay,
  queryAcademicRunDetail,
  queryAcademicTaskStatus,
  queryAcademicSessionDetail,
  queryAcademicSessions,
  queryGroupBuyMarketConfig,
  queryMcpHealth,
  queryMcpServers,
  queryMcpTools,
  queryQuotaPackages,
  queryWorkspaceDataCatalog,
  queryWorkspaceDataHistory,
  queryWorkspaceImageHistory,
  queryWorkspaceMragHistory,
  queryUserOrderList,
  rebuildKnowledgeVector,
  register,
  registerMcpServer,
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
import { AGENT_MODES } from "./agentModes";
import {
  buildTradeAuditPrompt,
  buildTradeHistoryAuditPrompt,
  summarizeTradeWorkspace,
  tradeOrderStatusLabel
} from "./tradeWorkspace";
import {
  DEFAULT_MCP_SERVER_FORM,
  buildMcpServerPayload
} from "./mcpServerForm";

const AGENTS = AGENT_MODES;

const PROMPT_ICONS = {
  book: BookOpen,
  file: FileText,
  globe: Globe2,
  image: ImagePlus,
  chart: BarChart3,
  credit: CreditCard
};

const EMPTY_MESSAGES = [];

const normalizeUserMessage = normalizeApiMessage;

const DEFAULT_MCP_TOOLS_TEXT = JSON.stringify({
  tools: [
    {
      name: "web_fetch",
      description: "fetch and summarize web page",
      enabled: true
    },
    {
      name: "data_analysis",
      description: "analyze table data and return insight",
      enabled: true
    }
  ]
}, null, 2);

const DEFAULT_MCP_IMPORT_TEXT = JSON.stringify({
  replace: false,
  snapshot: {
    servers: [],
    toolsByServer: {},
    discoveredAtByServer: {}
  }
}, null, 2);

const DEFAULT_MCP_TOOL_CALL_TEXT = JSON.stringify({
  arguments: {}
}, null, 2);

function apiSucceeded(res) {
  return res?.code === "0000" || res?.code === 200 || res?.code === "200";
}

function createRuntimeId(prefix) {
  return `${prefix}${Date.now()}`;
}

function attachReplayTimeline(messages = [], timeline = [], artifacts = [], resultPanels = []) {
  if (!timeline.length && !artifacts.length && !resultPanels.length) return messages;
  const index = [...messages].reverse().findIndex((message) => message.role === "assistant");
  if (index < 0) return messages;
  const targetIndex = messages.length - 1 - index;
  return messages.map((message, messageIndex) => (
    messageIndex === targetIndex
      ? {
          ...message,
          timeline: timeline.length ? timeline : message.timeline,
          artifacts: artifacts.length ? mergeArtifacts(message.artifacts, artifacts) : message.artifacts,
          resultPanels: resultPanels.length ? mergeResultPanels(message.resultPanels, resultPanels) : message.resultPanels,
          showTimeline: false
        }
      : message
  ));
}

function hasAssistantPayload(message = {}) {
  return Boolean(
    String(message.content || "").trim()
      || (message.timeline || []).length
      || (message.reference || []).length
      || (message.artifacts || []).length
      || (message.resultPanels || []).length
      || (message.recommend || []).length
  );
}

function latestAssistantWithPayload(messages = []) {
  return [...messages].reverse().find((message) => message.role === "assistant" && hasAssistantPayload(message)) || null;
}

function hasSessionMemory(memory = {}) {
  return Boolean(
    (memory.runs || []).length
      || (memory.toolObservations || []).length
      || (memory.reusableArtifacts || []).length
  );
}

function toUiReference(data = {}) {
  return {
    title: data.title || data.fileId || "参考资料",
    url: data.url || data.link || "",
    text: data.content || data.snippet || data.summary || data.text || ""
  };
}

function safeExternalUrl(url = "") {
  const value = String(url || "").trim();
  if (!value) return "";
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : "";
  } catch {
    return "";
  }
}

function safeResourceUrl(url = "") {
  const value = normalizeFileUrlForBrowser(url);
  if (!value) return "";
  if (value.startsWith("/") && !value.startsWith("//")) return value;
  return safeExternalUrl(value);
}

function artifactPreviewUrl(artifact = {}) {
  return safeResourceUrl(artifact.previewUrl) || safeResourceUrl(artifact.downloadUrl);
}

function isImageArtifact(artifact = {}) {
  const type = String(artifact.contentType || artifact.type || "").toLowerCase();
  const name = String(artifact.fileName || artifact.title || artifact.previewUrl || artifact.downloadUrl || "").toLowerCase();
  return type.startsWith("image/") || /\.(png|jpe?g|webp|gif|svg)(\?.*)?$/.test(name);
}

function workspaceImageArtifacts(data = {}) {
  return [
    ...(Array.isArray(data.fileRefs) ? data.fileRefs : []),
    ...(Array.isArray(data.artifactRefs) ? data.artifactRefs : [])
  ].map(toUiArtifact).filter((artifact) => artifact.fileName || artifact.downloadUrl || artifact.previewUrl);
}

function workspaceImageToolResultEvent(data = {}, fallbackInvocationId = "") {
  return {
    event: "tool_result",
    data: {
      invocationId: data.invocationId || fallbackInvocationId,
      toolName: data.toolName || "image_generation",
      status: "SUCCESS",
      resultSummary: data.summary || data.title || "",
      fileRefs: Array.isArray(data.fileRefs) ? data.fileRefs : [],
      artifactRefs: Array.isArray(data.artifactRefs) ? data.artifactRefs : [],
      structuredOutput: {
        title: data.title || "image generation",
        summary: data.summary || "",
        metadata: data.metadata || {},
        fileRefs: Array.isArray(data.fileRefs) ? data.fileRefs : []
      }
    }
  };
}

function workspaceDataToolResultEvent(result = {}) {
  return {
    event: "tool_result",
    data: {
      invocationId: result.invocationId || `${result.toolName || "data"}_${Date.now()}`,
      toolName: result.toolName || "data_analysis",
      status: "SUCCESS",
      resultSummary: result.summary || result.title || "",
      structuredOutput: result.structuredOutput || {
        title: result.title || result.toolName || "data result",
        summary: result.summary || "",
        content: result.content || ""
      },
      fileRefs: Array.isArray(result.fileRefs) ? result.fileRefs : []
    }
  };
}

function workspaceMragToolResultEvent(result = {}) {
  return {
    event: "tool_result",
    data: {
      invocationId: result.invocationId || `${result.toolName || "mrag"}_${Date.now()}`,
      toolName: result.toolName || "multimodal_agent",
      status: "SUCCESS",
      resultSummary: result.summary || result.title || "",
      structuredOutput: result.structuredOutput || {
        title: result.title || result.toolName || "mrag result",
        summary: result.summary || "",
        content: result.content || ""
      },
      fileRefs: Array.isArray(result.fileRefs) ? result.fileRefs : []
    }
  };
}

const TABLE_SEPARATOR_RE = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/;
const UNORDERED_LIST_RE = /^\s*(?:[-*+]|\u2022)\s+(.+)$/;
const ORDERED_LIST_RE = /^\s*\d+\.\s+(.+)$/;

function splitTrailingUrlPunctuation(url) {
  let href = url || "";
  let suffix = "";
  while (/[.,;:!?，。；：！？、]$/.test(href)) {
    suffix = href.slice(-1) + suffix;
    href = href.slice(0, -1);
  }
  return { href, suffix };
}

function renderInlineMarkdown(text, keyPrefix) {
  const source = String(text || "");
  const nodes = [];
  const inlineTokenRe = /(\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s<)]+)|<br\s*\/?>|\*\*([^*]+)\*\*)/gi;
  let lastIndex = 0;
  let match;
  let tokenIndex = 0;

  while ((match = inlineTokenRe.exec(source)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(source.slice(lastIndex, match.index));
    }

    const tokenKey = `${keyPrefix}-inline-${tokenIndex++}`;
    const fullToken = match[0];

    if (/^<br\s*\/?>$/i.test(fullToken)) {
      nodes.push(<br key={tokenKey} />);
    } else if (match[2] && match[3]) {
      const href = safeExternalUrl(match[3]);
      nodes.push(
        href ? (
          <a className="markdown-link" key={tokenKey} href={href} target="_blank" rel="noreferrer">
            {renderInlineMarkdown(match[2], tokenKey)}
          </a>
        ) : (
          match[2]
        )
      );
    } else if (match[4]) {
      const { href: rawHref, suffix } = splitTrailingUrlPunctuation(match[4]);
      const href = safeExternalUrl(rawHref);
      nodes.push(
        href ? (
          <a className="markdown-link" key={tokenKey} href={href} target="_blank" rel="noreferrer">
            {rawHref}
          </a>
        ) : (
          match[4]
        )
      );
      if (suffix) nodes.push(suffix);
    } else if (match[5]) {
      nodes.push(
        <strong className="markdown-strong" key={tokenKey}>
          {renderInlineMarkdown(match[5], tokenKey)}
        </strong>
      );
    }

    lastIndex = inlineTokenRe.lastIndex;
  }

  if (lastIndex < source.length) {
    nodes.push(source.slice(lastIndex));
  }

  return nodes.length ? nodes : source;
}

function splitMarkdownTableRow(line) {
  return String(line || "")
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());
}

function isTableStart(lines, index) {
  return Boolean(lines[index]?.includes("|") && TABLE_SEPARATOR_RE.test(lines[index + 1] || ""));
}

function isMarkdownBlockStart(lines, index) {
  const line = lines[index] || "";
  return Boolean(
    /^#{1,6}\s+/.test(line) ||
      /^-{3,}$/.test(line.trim()) ||
      /^\s*>\s?/.test(line) ||
      UNORDERED_LIST_RE.test(line) ||
      ORDERED_LIST_RE.test(line) ||
      isTableStart(lines, index)
  );
}

function MarkdownRenderer({ content = "" }) {
  const lines = String(content || "").replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();
    const blockKey = `markdown-block-${index}`;

    if (!trimmed) {
      index += 1;
      continue;
    }

    if (/^-{3,}$/.test(trimmed)) {
      blocks.push(<hr className="markdown-divider" key={blockKey} />);
      index += 1;
      continue;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const HeadingTag = headingMatch[1].length === 1 ? "h3" : "h4";
      blocks.push(
        <HeadingTag className="markdown-heading" key={blockKey}>
          {renderInlineMarkdown(headingMatch[2], blockKey)}
        </HeadingTag>
      );
      index += 1;
      continue;
    }

    if (isTableStart(lines, index)) {
      const headers = splitMarkdownTableRow(lines[index]);
      const rows = [];
      index += 2;
      while (index < lines.length && lines[index].trim() && lines[index].includes("|")) {
        rows.push(splitMarkdownTableRow(lines[index]));
        index += 1;
      }
      blocks.push(
        <div className="markdown-table-wrap" key={blockKey}>
          <table className="markdown-table">
            <thead>
              <tr>
                {headers.map((header, cellIndex) => (
                  <th key={`${blockKey}-head-${cellIndex}`}>{renderInlineMarkdown(header, `${blockKey}-head-${cellIndex}`)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, rowIndex) => (
                <tr key={`${blockKey}-row-${rowIndex}`}>
                  {headers.map((_, cellIndex) => (
                    <td key={`${blockKey}-row-${rowIndex}-${cellIndex}`}>
                      {renderInlineMarkdown(row[cellIndex] || "", `${blockKey}-row-${rowIndex}-${cellIndex}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
      continue;
    }

    if (/^\s*>\s?/.test(line)) {
      const quoteLines = [];
      while (index < lines.length && /^\s*>\s?/.test(lines[index])) {
        quoteLines.push(lines[index].replace(/^\s*>\s?/, ""));
        index += 1;
      }
      blocks.push(
        <blockquote className="markdown-quote" key={blockKey}>
          {quoteLines.map((quoteLine, quoteIndex) => (
            <p key={`${blockKey}-quote-${quoteIndex}`}>
              {renderInlineMarkdown(quoteLine, `${blockKey}-quote-${quoteIndex}`)}
            </p>
          ))}
        </blockquote>
      );
      continue;
    }

    const unorderedMatch = line.match(UNORDERED_LIST_RE);
    if (unorderedMatch) {
      const items = [];
      while (index < lines.length) {
        const itemMatch = lines[index].match(UNORDERED_LIST_RE);
        if (!itemMatch) break;
        items.push(itemMatch[1]);
        index += 1;
      }
      blocks.push(
        <ul className="markdown-list" key={blockKey}>
          {items.map((item, itemIndex) => (
            <li key={`${blockKey}-item-${itemIndex}`}>
              {renderInlineMarkdown(item, `${blockKey}-item-${itemIndex}`)}
            </li>
          ))}
        </ul>
      );
      continue;
    }

    const orderedMatch = line.match(ORDERED_LIST_RE);
    if (orderedMatch) {
      const items = [];
      while (index < lines.length) {
        const itemMatch = lines[index].match(ORDERED_LIST_RE);
        if (!itemMatch) break;
        items.push(itemMatch[1]);
        index += 1;
      }
      blocks.push(
        <ol className="markdown-list" key={blockKey}>
          {items.map((item, itemIndex) => (
            <li key={`${blockKey}-item-${itemIndex}`}>
              {renderInlineMarkdown(item, `${blockKey}-item-${itemIndex}`)}
            </li>
          ))}
        </ol>
      );
      continue;
    }

    const paragraphLines = [];
    while (index < lines.length && lines[index].trim() && !isMarkdownBlockStart(lines, index)) {
      paragraphLines.push(lines[index].trim());
      index += 1;
    }
    blocks.push(
      <p className="markdown-paragraph" key={blockKey}>
        {renderInlineMarkdown(paragraphLines.join(" "), blockKey)}
      </p>
    );
  }

  return <div className="text-content markdown-body">{blocks}</div>;
}

function App() {
  return (
    <Routes>
      <Route path={APP_ROUTES.admin} element={<AdminDashboard />} />
      <Route path={APP_ROUTES.adminLegacy} element={<AdminDashboard />} />
      <Route path="/*" element={<BearDoctorAcademicApp />} />
    </Routes>
  );
}

function BearDoctorAcademicApp() {
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
  const [adminForm, setAdminForm] = useState(() => {
    const saved = getAdminAuth();
    return { username: saved?.username || "", password: saved?.password || "" };
  });
  const [chatList, setChatList] = useState([]);
  const [currentChatId, setCurrentChatId] = useState(() => getSessionId());
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
    size: "1024x1024",
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
  const [webSearchEnabled, setWebSearchEnabled] = useState(false);
  const [outputStyle, setOutputStyle] = useState("auto");
  const [selectedFile, setSelectedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [runningChatIds, setRunningChatIds] = useState({});
  const [connectionError, setConnectionError] = useState("");
  const [quota, setQuota] = useState(null);
  const [quotaFlows, setQuotaFlows] = useState([]);
  const [packages, setPackages] = useState([]);
  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [taskStatusByChat, setTaskStatusByChat] = useState({});
  const [buyingKey, setBuyingKey] = useState("");
  const [toast, setToast] = useState("");
  const [copiedId, setCopiedId] = useState("");
  const [agentCapabilities, setAgentCapabilities] = useState(null);
  const [agentCapabilitiesError, setAgentCapabilitiesError] = useState("");
  const [agentAdminPanelOpen, setAgentAdminPanelOpen] = useState(false);
  const [mcpPanelOpen, setMcpPanelOpen] = useState(false);
  const [mcpServers, setMcpServers] = useState([]);
  const [mcpTools, setMcpTools] = useState([]);
  const [mcpLoading, setMcpLoading] = useState(false);
  const [mcpActionKey, setMcpActionKey] = useState("");
  const [mcpError, setMcpError] = useState("");
  const [mcpServerForm, setMcpServerForm] = useState(DEFAULT_MCP_SERVER_FORM);
  const [mcpCacheServerId, setMcpCacheServerId] = useState("");
  const [mcpToolPayload, setMcpToolPayload] = useState(DEFAULT_MCP_TOOLS_TEXT);
  const [mcpHealth, setMcpHealth] = useState(null);
  const [mcpExportPayload, setMcpExportPayload] = useState("");
  const [mcpImportPayload, setMcpImportPayload] = useState(DEFAULT_MCP_IMPORT_TEXT);
  const [mcpToolCallName, setMcpToolCallName] = useState("");
  const [mcpToolCallPayload, setMcpToolCallPayload] = useState(DEFAULT_MCP_TOOL_CALL_TEXT);
  const [mcpToolCallResult, setMcpToolCallResult] = useState("");
  const messagesContainer = useRef(null);
  const fileInputRef = useRef(null);
  const knowledgeFileInputRef = useRef(null);
  const streamControllersRef = useRef({});

  const currentChat = useMemo(() => chatList.find((item) => item.id === currentChatId), [chatList, currentChatId]);
  const currentWorkspace = useMemo(() => (
    WORKSPACES.find((workspace) => workspace.id === activeWorkspace) || WORKSPACES[0]
  ), [activeWorkspace]);
  const currentWorkspaceProfile = useMemo(() => (
    workspaceDisplayProfile(currentWorkspace.id, agentCapabilities, workspaceServiceProfile(currentWorkspace.id))
  ), [agentCapabilities, currentWorkspace.id]);
  const backendText = auth?.token ? `已登录：${auth.nickname || auth.username || auth.userId}` : "未登录";
  const currentTaskStatus = taskStatusByChat[currentChatId] || {};
  const isSending = Boolean(runningChatIds[currentChatId]);
  const canResumeCurrentChat = Boolean((currentTaskStatus.stopped || currentChat?.stopped) && !isSending);
  const canUseFile = workspaceAcceptsFile(currentWorkspace.id, selectedAgent);
  const capabilitySummary = useMemo(() => {
    if (!agentCapabilities) return [];
    const toolCount = Number(agentCapabilities.academicToolCount || 0);
    const manualSkillCount = Number(agentCapabilities.manualSkillCount || 0);
    return [
      { key: "model", label: "模型", value: agentCapabilities.chatModelAvailable ? "可用" : "未配置", active: Boolean(agentCapabilities.chatModelAvailable) },
      { key: "tools", label: "工具", value: `${toolCount} 个`, active: toolCount > 0 },
      { key: "manual", label: "手动技能", value: `${manualSkillCount} 个`, active: manualSkillCount > 0 },
      { key: "reactor", label: "参考工具", value: agentCapabilities.reactorToolEnabled ? "已开启" : "未开启", active: Boolean(agentCapabilities.reactorToolEnabled) },
      { key: "web", label: "联网", value: agentCapabilities.webSearchAvailable ? "可用" : "本地", active: Boolean(agentCapabilities.webSearchAvailable) }
    ];
  }, [agentCapabilities]);
  const visibleAcademicTools = useMemo(() => (
    (agentCapabilities?.academicTools || []).slice(0, 7)
  ), [agentCapabilities]);
  const visibleToolGroups = useMemo(() => (
    visibleToolCatalogGroups(agentCapabilities, 5)
  ), [agentCapabilities]);
  const visibleToolReadiness = useMemo(() => (
    visibleToolRuntimeReadiness(agentCapabilities, 8)
  ), [agentCapabilities]);
  const visibleCapabilityMatrix = useMemo(() => (
    buildVisibleCapabilityMatrix(agentCapabilities, 6)
  ), [agentCapabilities]);
  const visibleExecutionModes = useMemo(() => (
    visibleAgentExecutionModes(agentCapabilities, 6)
  ), [agentCapabilities]);
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
    setActiveWorkspace(routeWorkspace);
    setSelectedAgent(workspaceAgentMode(routeWorkspace));
  }, [routeWorkspace]);

  const toggleTheme = () => {
    setTheme((prev) => applyTheme(nextTheme(prev)));
  };

  const openWorkspace = (workspaceId) => {
    const nextWorkspace = WORKSPACES.find((workspace) => workspace.id === workspaceId) || WORKSPACES[0];
    setActiveWorkspace(nextWorkspace.id);
    if (nextWorkspace.agentId) {
      setSelectedAgent(nextWorkspace.agentId);
      if (nextWorkspace.agentId !== "file" && nextWorkspace.agentId !== "data" && nextWorkspace.agentId !== "mrag" && nextWorkspace.agentId !== "skills" && nextWorkspace.agentId !== "manual-skills") {
        setSelectedFile(null);
      }
    }
    const path = workspacePath(nextWorkspace.id);
    if (location.pathname !== path) {
      navigate(path);
    }
    if (nextWorkspace.id === "trade") {
      if (!getUserAuth()?.token) {
        setLoginOpen(true);
      } else {
        setGroupPreviewPackage(null);
        setGroupMarketConfig(null);
        setRechargeTab("packages");
        setRechargeOpen(true);
      }
    }
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
      setQuotaFlows(res.data?.flows || []);
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
        title: item.title || "学术会话",
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

  const loadMcpState = useCallback(async () => {
    setMcpLoading(true);
    setMcpError("");
    try {
      const [serversRes, toolsRes, healthRes] = await Promise.all([
        queryMcpServers(),
        queryMcpTools({ enabledOnly: false }),
        queryMcpHealth()
      ]);
      if (!apiSucceeded(serversRes)) {
        throw new Error(normalizeUserMessage(serversRes.info || serversRes.message, "MCP 服务读取失败"));
      }
      if (!apiSucceeded(toolsRes)) {
        throw new Error(normalizeUserMessage(toolsRes.info || toolsRes.message, "MCP 工具读取失败"));
      }
      const servers = serversRes.data || [];
      const tools = toolsRes.data || [];
      setMcpServers(servers);
      setMcpTools(tools);
      setMcpHealth(apiSucceeded(healthRes) ? healthRes.data || null : null);
      setMcpCacheServerId((prev) => prev || servers[0]?.serverId || "");
      setMcpToolCallName((prev) => prev || tools.find((tool) => tool.enabled)?.qualifiedName || tools[0]?.qualifiedName || "");
    } catch (error) {
      setMcpError(normalizeUserMessage(error.message, "MCP 管理信息读取失败"));
    } finally {
      setMcpLoading(false);
    }
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
      const query = {
        image: queryWorkspaceImageHistory,
        data: queryWorkspaceDataHistory,
        mrag: queryWorkspaceMragHistory,
        trade: ({ limit }) => queryUserOrderList({ pageSize: limit || 8 })
      }[targetWorkspaceId];
      if (!query) {
        throw new Error("工作区历史暂不可用");
      }
      const res = await query({ limit: 8 });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res?.info || res?.message, "工作区历史读取失败"));
      }
      const historyItems = targetWorkspaceId === "trade"
        ? res.data?.orderList || []
        : targetWorkspaceId === "image" && Array.isArray(res.data?.batches) && res.data.batches.length
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
    id: `${item.role || "MSG"}_${index}_${item.createTime || "local"}`,
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
    if (event.event === "answer_delta") {
      appendAssistantTextInChat(chatId, messageId, data.content || "");
      return;
    }
    if (["run_start", "plan_delta", "flow_delta", "tool_call", "tool_result", "llm_delta", "run_done", "run_error", "quota_delta", "usage_metric"].includes(event.event)) {
      const timelineItem = streamEventToTimelineItem(event, normalizeUserMessage);
      const artifacts = event.event === "tool_result" ? toolResultArtifacts(event) : [];
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
      setSelectedAgent("trade-audit");
      setInputMessage(buildTradeHistoryAuditPrompt(item));
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
        title: item.title || item.artifactName || "工作区任务",
        lastMessage: item.summary || "",
        messages: EMPTY_MESSAGES,
        isNew: false
      }, ...prev];
    });
    try {
      await refreshSessionDetail(sessionId);
      await loadTaskStatus(sessionId);
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "工作区历史详情读取失败"));
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
      loadOrders().catch(() => {})
    ]);
    if (activeWorkspace === "trade") {
      await loadWorkspaceHistory("trade").catch(() => {});
    }
  }, [activeWorkspace, loadOrders, loadQuota, loadWorkspaceHistory]);

  useEffect(() => {
    ensureChat(currentChatId);
  }, [currentChatId, ensureChat]);

  useEffect(() => {
    loadPackages().catch((error) => console.warn("额度包读取失败", error));
  }, [loadPackages]);

  useEffect(() => {
    loadAgentCapabilities().catch((error) => {
      console.warn("Agent 能力状态读取失败", error);
      setAgentCapabilitiesError("能力状态读取失败");
    });
  }, [loadAgentCapabilities]);

  useEffect(() => {
    if (!auth?.token) return;
    loadQuota().catch((error) => setConnectionError(normalizeUserMessage(error.message, "额度读取失败")));
    loadSessions().catch(() => {});
    loadOrders().catch(() => {});
  }, [auth, loadOrders, loadQuota, loadSessions]);

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
    setSelectedFile(null);
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
    if (agentId !== "file" && agentId !== "data" && agentId !== "mrag" && agentId !== "skills" && agentId !== "manual-skills") {
      setSelectedFile(null);
    }
  };

  const quickPrompt = (prompt) => {
    setInputMessage(prompt);
  };

  const auditTradeOrder = (order) => {
    setSelectedAgent("trade-audit");
    setInputMessage(buildTradeAuditPrompt(order || {}));
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

  const openGroupPreview = async (pkg) => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    setConnectionError("");
    setGroupPreviewPackage(pkg);
    setGroupMarketConfig(null);
    setGroupTeamsLoading(true);
    try {
      const userId = auth.userId || quota?.userId;
      const res = await queryGroupBuyMarketConfig(pkg, userId);
      if (res.code !== "0000") throw new Error(normalizeUserMessage(res.info, "拼团信息读取失败"));
      setGroupMarketConfig(res.data || null);
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "拼团信息读取失败"));
    } finally {
      setGroupTeamsLoading(false);
    }
  };

  const handleAuthSubmit = async (event) => {
    event.preventDefault();
    setAuthError("");
    try {
      const res = authMode === "login" ? await login(authForm.username, authForm.password) : await register(authForm);
      if (res.code === "0000") {
        setAuth(res.data);
        setLoginOpen(false);
        setToast("登录成功");
      } else {
        setAuthError(normalizeUserMessage(res.info, "登录失败"));
      }
    } catch (error) {
      setAuthError(normalizeUserMessage(error.message, "登录失败"));
    }
  };

  const handleLogout = async () => {
    Object.values(streamControllersRef.current).forEach((controller) => controller?.abort?.());
    streamControllersRef.current = {};
    setRunningChatIds({});
    await logout();
    setAuth(null);
    setQuota(null);
    setQuotaFlows([]);
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
    setSelectedFile({ name: file.name, size: file.size, status: "uploading" });
    setIsUploading(true);
    try {
      const res = await uploadAcademicFile(file, currentChatId);
      if (res.code === "0000") {
        setSelectedFile({
          fileId: res.data.fileId,
          name: res.data.fileName,
          fileType: res.data.fileType,
          size: res.data.fileSize,
          summary: res.data.summary,
          status: "parsed"
        });
        setToast("文件解析完成");
      } else {
        setConnectionError(normalizeUserMessage(res.info, "文件上传失败"));
        setSelectedFile(null);
      }
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "文件上传失败"));
      setSelectedFile(null);
    } finally {
      setIsUploading(false);
    }
  };

  const sendMessage = () => {
    const text = inputMessage.trim();
    const sessionId = currentChatId;
    const file = selectedFile;
    if (runningChatIds[sessionId] || isUploading || (!text && !file)) return;
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    if (!modelConfigReady(modelConfig)) {
      setConnectionError("请先补全自定义模型的 API 地址和密钥");
      setModelConfigOpen(true);
      return;
    }
    const streamDraft = buildWorkspaceStreamDraft({
      workspaceId: currentWorkspace.id,
      agentId: selectedAgent,
      question: text || (file ? "请分析这个文件" : ""),
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

    const userMsg = {
      id: createRuntimeId("U"),
      role: "user",
      content: streamDraft.question,
      file: Boolean(file),
      fileName: file?.name || ""
    };
    const assistantId = createRuntimeId("A");
    const assistantMsg = {
      id: assistantId,
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

    updateChat(sessionId, (chat) => ({
      ...chat,
      title: chat.isNew && text ? `${text.slice(0, 20)}${text.length > 20 ? "..." : ""}` : chat.title,
      isNew: false,
      stopped: false,
      messages: [...chat.messages, userMsg, assistantMsg]
    }));
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
            throw new Error(normalizeUserMessage(res?.info, "image generation failed"));
          }
          const data = res.data || {};
          const summary = data.summary || data.title || "image generation completed";
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
          const message = normalizeUserMessage(error.message, "image generation failed");
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
        question: streamDraft.question,
        taskType: streamDraft.taskType,
        fileId: streamDraft.fileId,
        imageUrl: streamDraft.imageUrl,
        imageName: streamDraft.imageName,
        webSearchEnabled,
        outputStyle: outputStylePayload(outputStyle),
        modelConfig
      },
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
    if (!modelConfigReady(modelConfig)) {
      setConnectionError("请先补全自定义模型的 API 地址和密钥");
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
      outputStylePayload(outputStyle),
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

  const handleArtifactDownload = async (artifact) => {
    try {
      await downloadAcademicArtifact(artifact.downloadUrl, artifact.fileName || artifact.title || "artifact");
      setToast("文件已开始下载");
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "文件下载失败"));
    }
  };

  const handleSaveAdminAuth = () => {
    saveAdminAuth(adminForm.username, adminForm.password);
    setToast("模拟支付授权已保存");
  };

  const handleSaveModelConfig = (nextConfig) => {
    setModelConfig(saveModelConfig(nextConfig));
    setModelConfigOpen(false);
    setToast("模型配置已保存");
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
    runKnowledgeAction("rebuild", rebuildKnowledgeVector, "向量索引已触发重建").catch(() => {});
  };

  const handleCompensateKnowledgeVector = () => {
    runKnowledgeAction("compensate", compensateKnowledgeVector, "失败补偿已触发").catch(() => {});
  };

  const toggleMcpPanel = () => {
    const nextOpen = !mcpPanelOpen;
    setMcpPanelOpen(nextOpen);
    if (nextOpen) {
      setAgentAdminPanelOpen(false);
    }
    if (nextOpen && mcpServers.length === 0) {
      loadMcpState().catch(() => {});
    }
  };

  const toggleAgentAdminPanel = () => {
    const nextOpen = !agentAdminPanelOpen;
    setAgentAdminPanelOpen(nextOpen);
    if (nextOpen) {
      setMcpPanelOpen(false);
    }
  };

  const handleSaveMcpAdminAuth = () => {
    handleSaveAdminAuth();
    loadMcpState().catch(() => {});
  };

  const handleRegisterMcpServer = async (event) => {
    event.preventDefault();
    setMcpError("");
    setMcpActionKey("register");
    try {
      const payload = buildMcpServerPayload(mcpServerForm);
      if (!payload.serverId || !payload.endpoint) {
        throw new Error("请填写服务标识和服务地址");
      }
      const res = await registerMcpServer(payload);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 服务注册失败"));
      }
      setMcpCacheServerId(payload.serverId);
      setToast("MCP 服务已注册");
      await loadMcpState();
      await loadAgentCapabilities().catch(() => {});
    } catch (error) {
      setMcpError(normalizeUserMessage(error.message, "MCP 服务注册失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleToggleMcpServer = async (server) => {
    if (!server?.serverId) return;
    const nextEnabled = !server.enabled;
    setMcpError("");
    setMcpActionKey(`server-${server.serverId}`);
    try {
      const res = await enableMcpServer(server.serverId, nextEnabled);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 服务状态更新失败"));
      }
      setToast(nextEnabled ? "MCP 服务已启用" : "MCP 服务已停用");
      await loadMcpState();
      await loadAgentCapabilities().catch(() => {});
    } catch (error) {
      setMcpError(normalizeUserMessage(error.message, "MCP 服务状态更新失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleCacheMcpTools = async (event) => {
    event.preventDefault();
    setMcpError("");
    setMcpActionKey("cache-tools");
    try {
      const serverId = String(mcpCacheServerId || "").trim();
      if (!serverId) throw new Error("请先选择 MCP 服务");
      const payload = JSON.parse(mcpToolPayload || "{}");
      const tools = Array.isArray(payload.tools) ? payload.tools : [];
      if (tools.length === 0) throw new Error("请填写至少一个工具");
      const res = await cacheMcpTools(serverId, { tools });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 工具缓存失败"));
      }
      setToast("MCP 工具缓存已更新");
      await loadMcpState();
      await loadAgentCapabilities().catch(() => {});
    } catch (error) {
      setMcpError(error instanceof SyntaxError
        ? "工具 JSON 格式不正确"
        : normalizeUserMessage(error.message, "MCP 工具缓存失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleDiscoverMcpTools = async (server) => {
    const serverId = String(server?.serverId || mcpCacheServerId || "").trim();
    if (!serverId) return;
    setMcpError("");
    setMcpActionKey(`discover-${serverId}`);
    try {
      const res = await discoverMcpTools(serverId, { cache: true });
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 工具发现失败"));
      }
      setMcpCacheServerId(serverId);
      const toolCount = Number(res.data?.toolCount || 0);
      setToast(toolCount > 0 ? `MCP 已发现并缓存 ${toolCount} 个工具` : "MCP 未发现可缓存工具");
      await loadMcpState();
      await loadAgentCapabilities().catch(() => {});
    } catch (error) {
      setMcpError(normalizeUserMessage(error.message, "MCP 工具发现失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleCheckMcpHealth = async () => {
    setMcpError("");
    setMcpActionKey("health");
    try {
      const res = await queryMcpHealth();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 健康检查失败"));
      }
      setMcpHealth(res.data || null);
      setToast("MCP 健康状态已更新");
    } catch (error) {
      setMcpError(normalizeUserMessage(error.message, "MCP 健康检查失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleExportMcpState = async () => {
    setMcpError("");
    setMcpActionKey("export");
    try {
      const res = await exportMcpState();
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 配置导出失败"));
      }
      const text = JSON.stringify(res.data || {}, null, 2);
      setMcpExportPayload(text);
      setMcpImportPayload(text);
      setToast("MCP 配置已导出");
    } catch (error) {
      setMcpError(normalizeUserMessage(error.message, "MCP 配置导出失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleImportMcpState = async () => {
    setMcpError("");
    setMcpActionKey("import");
    try {
      const payload = JSON.parse(mcpImportPayload || "{}");
      const res = await importMcpState(payload);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 配置导入失败"));
      }
      setToast(`MCP 配置已导入，服务 ${res.data?.serverCount || 0} 个，工具 ${res.data?.toolCount || 0} 个`);
      await loadMcpState();
      await loadAgentCapabilities().catch(() => {});
    } catch (error) {
      setMcpError(error instanceof SyntaxError
        ? "导入 JSON 格式不正确"
        : normalizeUserMessage(error.message, "MCP 配置导入失败"));
    } finally {
      setMcpActionKey("");
    }
  };

  const handleCallMcpTool = async () => {
    const toolName = String(mcpToolCallName || "").trim();
    if (!toolName) {
      setMcpError("请先选择要测试的 MCP 工具");
      return;
    }
    setMcpError("");
    setMcpActionKey("call-tool");
    try {
      const payload = JSON.parse(mcpToolCallPayload || "{}");
      const res = await callMcpTool(toolName, payload);
      if (!apiSucceeded(res)) {
        throw new Error(normalizeUserMessage(res.info || res.message, "MCP 工具调用失败"));
      }
      setMcpToolCallResult(JSON.stringify(res.data || {}, null, 2));
      setToast("MCP 工具试调用完成");
    } catch (error) {
      setMcpError(error instanceof SyntaxError
        ? "调用参数 JSON 格式不正确"
        : normalizeUserMessage(error.message, "MCP 工具调用失败"));
    } finally {
      setMcpActionKey("");
    }
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
        throw new Error("当前额度包暂无可用拼团活动");
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
        source: "new"
      });
      setRechargeTab("orders");
      setToast("订单已创建，确认支付后继续处理");
      await loadOrders().catch(() => {});
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "购买失败"));
    } finally {
      setBuyingKey("");
    }
  };

  const payExistingOrder = async (order) => {
    if (!order?.orderId) return;
    setPaymentDialog({
      orderId: order.orderId,
      productName: order.productName || order.productId || "额度订单",
      amount: order.payAmount || order.totalAmount,
      marketType: order.marketType,
      quotaAmount: 0,
      source: "existing"
    });
  };

  const confirmPayment = async () => {
    if (!paymentDialog?.orderId) return;
    setBuyingKey(`pay-${paymentDialog.orderId}`);
    setConnectionError("");
    try {
      const payRes = await mockPaySuccess(paymentDialog.orderId);
      if (payRes.code !== "0000") throw new Error(normalizeUserMessage(payRes.info, "模拟支付失败"));
      const groupSettled = payRes.data?.orderStatus === "GROUP_SETTLED" || payRes.data?.orderStatus === "DEAL_DONE";
      const isGroupOrder = Number(paymentDialog.marketType) === 1;
      setPaymentDialog(null);
      setToast(isGroupOrder && !groupSettled ? "支付成功，等待成团" : "支付成功，额度已到账");
      await refreshRecharge();
    } catch (error) {
      setConnectionError(normalizeUserMessage(error.message, "模拟支付失败"));
    } finally {
      setBuyingKey("");
    }
  };

  return (
    <div className="bear-doctor-app" data-theme={theme}>
      <div className="glow-effect glow-effect-1" />
      <div className="glow-effect glow-effect-2" />
      <div className="container">
        <aside className="sidebar">
          <div className="sidebar-header">
            <div className="app-title">
              <img className="logo-icon" src="/bear-doctor-logo.png" alt="熊博士" />
              <span className="title-text">熊博士Agent</span>
            </div>
            <button className="new-chat-btn" onClick={createNewChat}>
              <Plus size={16} />
              <span>新对话</span>
            </button>
          </div>

          <div className="workspace-nav">
            {WORKSPACES.map((workspace) => (
              <button
                type="button"
                key={workspace.id}
                className={`workspace-nav-item ${activeWorkspace === workspace.id ? "active" : ""}`}
                onClick={() => openWorkspace(workspace.id)}
              >
                <span>{workspace.icon}</span>
                <b>{workspace.name}</b>
              </button>
            ))}
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
            <div className="workspace-titlebar">
              <span>{currentWorkspace.icon}</span>
              <strong>{currentWorkspace.name}</strong>
            </div>
            <div className="top-actions">
              <ThemeToggle theme={theme} onToggle={toggleTheme} />
              <button className="account-btn" onClick={() => setModelConfigOpen(true)}>
                <Settings size={15} />
                <span>模型</span>
              </button>
              <button className={`account-btn ${mcpPanelOpen ? "active" : ""}`} onClick={toggleMcpPanel}>
                <Globe2 size={15} />
                <span>MCP</span>
              </button>
              <button className={`account-btn ${agentAdminPanelOpen ? "active" : ""}`} onClick={toggleAgentAdminPanel}>
                <Settings size={15} />
                <span>Agent 配置</span>
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
            {agentAdminPanelOpen && (
              <AgentAdminPanel
                adminForm={adminForm}
                setAdminForm={setAdminForm}
                onSaveAdminAuth={handleSaveAdminAuth}
                onCapabilitiesRefresh={loadAgentCapabilities}
              />
            )}
            {mcpPanelOpen && (
              <McpManagementPanelV2
                adminForm={adminForm}
                setAdminForm={setAdminForm}
                serverForm={mcpServerForm}
                setServerForm={setMcpServerForm}
                servers={mcpServers}
                tools={mcpTools}
                loading={mcpLoading}
                actionKey={mcpActionKey}
                error={mcpError}
                health={mcpHealth}
                exportPayload={mcpExportPayload}
                importPayload={mcpImportPayload}
                setImportPayload={setMcpImportPayload}
                toolCallName={mcpToolCallName}
                setToolCallName={setMcpToolCallName}
                toolCallPayload={mcpToolCallPayload}
                setToolCallPayload={setMcpToolCallPayload}
                toolCallResult={mcpToolCallResult}
                cacheServerId={mcpCacheServerId}
                setCacheServerId={setMcpCacheServerId}
                toolPayload={mcpToolPayload}
                setToolPayload={setMcpToolPayload}
                onSaveAdminAuth={handleSaveMcpAdminAuth}
                onRefresh={loadMcpState}
                onRegister={handleRegisterMcpServer}
                onToggleServer={handleToggleMcpServer}
                onDiscoverTools={handleDiscoverMcpTools}
                onCacheTools={handleCacheMcpTools}
                onCheckHealth={handleCheckMcpHealth}
                onExportState={handleExportMcpState}
                onImportState={handleImportMcpState}
                onCallTool={handleCallMcpTool}
              />
            )}
            {workspaceSupportsHistory(currentWorkspace.id) && (
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
            {currentWorkspace.id === "image" && (
              <ImageWorkspacePanel
                draft={imageWorkspaceDraft}
                onChange={setImageWorkspaceDraft}
                hasReference={Boolean(selectedFile?.fileId && isImageArtifact({ fileName: selectedFile.name, contentType: selectedFile.fileType }))}
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
                onAuditOrder={auditTradeOrder}
              />
            )}
            {(!currentChat || currentChat.messages.length === 0) ? (
              <WorkspaceEmptyState
                workspace={currentWorkspace}
                profile={currentWorkspaceProfile}
                capabilities={agentCapabilities}
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
            <div className="agent-selector">
              {AGENTS.map((agent) => (
                <button
                  key={agent.id}
                  className={`agent-item ${selectedAgent === agent.id ? "active" : ""}`}
                  onClick={() => selectAgent(agent.id)}
                  title={agent.summary}
                >
                  <span className="agent-icon">{agent.icon}</span>
                  <span className="agent-copy">
                    <span className="agent-name">{agent.name}</span>
                    <span className={`agent-mode agent-mode-${agent.executionFamily}`}>{agent.executionMode}</span>
                  </span>
                  {selectedAgent === agent.id && <Check size={12} className="check-icon" />}
                </button>
              ))}
              <button
                type="button"
                className={`web-search-toggle ${webSearchEnabled ? "active" : ""}`}
                aria-pressed={webSearchEnabled}
                onClick={() => setWebSearchEnabled((prev) => !prev)}
                title={webSearchEnabled ? "关闭联网搜索" : "开启联网搜索"}
              >
                <Globe2 size={16} />
                <span>联网搜索</span>
                <span className="toggle-state">{webSearchEnabled ? "开" : "关"}</span>
              </button>
              <label className="output-style-select" title={OUTPUT_STYLE_OPTIONS.find((item) => item.key === outputStyle)?.description || ""}>
                <span>输出</span>
                <select value={outputStyle} onChange={(event) => setOutputStyle(event.target.value)}>
                  {OUTPUT_STYLE_OPTIONS.map((item) => (
                    <option value={item.key} key={item.key}>{item.label}</option>
                  ))}
                </select>
              </label>
            </div>

            {(capabilitySummary.length > 0 || agentCapabilitiesError) && (
              <div className="agent-capability-strip">
                {capabilitySummary.map((item) => (
                  <span key={item.key} className={`agent-capability-pill ${item.active ? "active" : ""}`}>
                    <span>{item.label}</span>
                    <b>{item.value}</b>
                  </span>
                ))}
                {visibleAcademicTools.length > 0 && (
                  <div className="agent-tool-preview">
                    {visibleToolGroups.map((group) => (
                      <span key={`group-${group.key}`} className="tool-group-chip">
                        {group.key} <b>{group.count}</b>
                      </span>
                    ))}
                    {visibleAcademicTools.map((tool) => (
                      <span key={tool.name}>{TOOL_LABELS[tool.name] || tool.name}</span>
                    ))}
                    {Number(agentCapabilities?.academicToolCount || 0) > visibleAcademicTools.length && (
                      <span>+{Number(agentCapabilities.academicToolCount || 0) - visibleAcademicTools.length}</span>
                    )}
                  </div>
                )}
                {visibleToolReadiness.length > 0 && (
                  <div className="agent-tool-readiness">
                    {visibleToolReadiness.map((tool) => {
                      const meta = toolReadinessMeta(tool);
                      return (
                        <span
                          key={tool.name}
                          className={tool.status === "ready" ? "ready" : "missing"}
                          title={[tool.hint || tool.message || "", meta].filter(Boolean).join("\n")}
                        >
                          <b>{TOOL_LABELS[tool.name] || tool.name}</b>
                          <em>{tool.status}</em>
                          {meta && <small>{meta}</small>}
                          {tool.status !== "ready" && tool.hint && <small className="tool-runtime-hint">{tool.hint}</small>}
                        </span>
                      );
                    })}
                  </div>
                )}
                <CapabilityMatrixPanel items={visibleCapabilityMatrix} executionModes={visibleExecutionModes} />
                {agentCapabilitiesError && <span className="agent-capability-error">{agentCapabilitiesError}</span>}
              </div>
            )}

            {selectedFile && (
              <div className="file-preview">
                <div className="file-preview-item">
                  <div className="file-icon-wrapper"><FileText size={22} /></div>
                  <div className="file-info">
                    <div className="file-name">{selectedFile.name}</div>
                    <div className="file-size">{formatFileSize(selectedFile.size)}</div>
                    {isUploading && <div className="upload-parsing"><Loader2 size={14} className="spin" /><span>解析中...</span></div>}
                  </div>
                  <button className="remove-file" onClick={() => setSelectedFile(null)}><Trash2 size={16} /></button>
                </div>
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

            <div className="input-container">
              {canUseFile && !selectedFile && (
                <button className="file-btn" disabled={isUploading} onClick={() => fileInputRef.current?.click()} title="上传文件">
                  <Paperclip size={20} />
                </button>
              )}
              <input ref={fileInputRef} type="file" accept=".md,.txt,.pdf,.docx,.png,.jpg,.jpeg,.webp" onChange={handleFileSelect} hidden />
              {selectedFile && <div className="input-file-icon"><FileText size={18} /></div>}
              <textarea
                value={inputMessage}
                onChange={(event) => setInputMessage(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    sendMessage();
                  }
                }}
                placeholder="输入消息... (Shift+Enter 换行)"
                rows={1}
              />
              <button
                className={`send-btn ${isSending ? "stop" : ""} ${!isSending && (!inputMessage.trim() && !selectedFile) ? "disabled" : ""}`}
                onClick={isSending ? stopMessage : sendMessage}
                disabled={!isSending && (!inputMessage.trim() && !selectedFile)}
              >
                {isSending ? <Square size={18} /> : <Send size={18} />}
              </button>
            </div>
          </div>
        </main>
      </div>

      {connectionError && (
        <div className="connection-error">
          <AlertTriangle size={18} />
          <span>{connectionError}</span>
          <button className="retry-btn" onClick={() => setConnectionError("")}>关闭</button>
        </div>
      )}

      {toast && (
        <div className="toast">
          <Check size={16} />
          <span>{toast}</span>
          <button onClick={() => setToast("")}>×</button>
        </div>
      )}

      {loginOpen && (
        <AuthDialog
          mode={authMode}
          setMode={setAuthMode}
          form={authForm}
          setForm={setAuthForm}
          error={authError}
          onSubmit={handleAuthSubmit}
          onClose={() => setLoginOpen(false)}
        />
      )}

      {rechargeOpen && (
        <RechargeDialog
          quota={quota}
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
          adminForm={adminForm}
          setAdminForm={setAdminForm}
          onBuy={buyPackage}
          onOpenGroupPreview={openGroupPreview}
          onBackToPackages={() => {
            setGroupPreviewPackage(null);
            setGroupMarketConfig(null);
          }}
          onSaveAdminAuth={handleSaveAdminAuth}
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

function compactToolList(items = [], limit = 2) {
  const cleanItems = (items || []).filter(Boolean);
  const visible = cleanItems.slice(0, limit);
  if (visible.length === 0) return "";
  const more = Math.max(0, cleanItems.length - visible.length);
  return `${visible.join("/")}${more ? ` +${more}` : ""}`;
}

function toolReadinessMeta(tool = {}) {
  const inputText = compactToolList(
    tool.requiredArguments?.length ? tool.requiredArguments : tool.inputFields,
    2
  );
  const outputText = compactToolList(tool.outputKinds, 2);
  const workspaceText = compactToolList(tool.workspaces, 2);
  return [
    inputText ? `入参 ${inputText}` : "",
    outputText ? `输出 ${outputText}` : "",
    workspaceText ? `工作区 ${workspaceText}` : ""
  ].filter(Boolean).join(" · ");
}

function CapabilityMatrixPanel({ items = [], executionModes = [] }) {
  if (!items.length && !executionModes.length) return null;
  return (
    <div className="agent-capability-matrix">
      {executionModes.length > 0 && (
        <div className="agent-execution-modes">
          {executionModes.map((mode) => (
            <span key={mode.agentId} title={mode.summary || ""}>
              <b>{mode.name}</b>
              <em>{mode.executionMode || mode.family || "-"}</em>
            </span>
          ))}
        </div>
      )}
      {items.map((item) => (
        <article
          key={item.key || item.label}
          className={`agent-capability-node ${item.status === "ready" ? "ready" : "degraded"}`}
        >
          <div>
            <b>{item.label}</b>
            <em>{item.status === "ready" ? "已就绪" : "降级中"}</em>
          </div>
          {item.summary && <p>{item.summary}</p>}
          {item.evidence?.length > 0 && (
            <div className="agent-capability-evidence">
              {item.evidence.map((evidence) => (
                <span key={evidence}>{evidence}</span>
              ))}
            </div>
          )}
          <small>{item.gaps?.length > 0 ? item.gaps.join("；") : "无缺口"}</small>
        </article>
      ))}
    </div>
  );
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

function ImageWorkspacePanel({ draft, onChange, hasReference }) {
  const update = (field, value) => onChange({ ...draft, [field]: value });
  return (
    <section className="image-workspace-panel">
      <div className="image-workspace-head">
        <div>
          <strong>图像参数</strong>
          <span>{draft.mode === "edit" ? "图生图会使用当前上传的参考图" : "文生图会直接使用输入提示词"}</span>
        </div>
        <span className={hasReference ? "ready" : ""}>{hasReference ? "已有参考图" : "无参考图"}</span>
      </div>
      <div className="image-workspace-grid">
        <label>
          <span>模式</span>
          <select value={draft.mode} onChange={(event) => update("mode", event.target.value)}>
            <option value="generate">文生图</option>
            <option value="edit">图生图</option>
          </select>
        </label>
        <label>
          <span>尺寸</span>
          <select value={draft.size} onChange={(event) => update("size", event.target.value)}>
            <option value="1024x1024">1024x1024</option>
            <option value="1536x1024">1536x1024</option>
            <option value="1024x1536">1024x1536</option>
            <option value="768x768">768x768</option>
          </select>
        </label>
        <label>
          <span>张数</span>
          <input
            type="number"
            min="1"
            max="4"
            value={draft.batchCount}
            onChange={(event) => update("batchCount", event.target.value)}
          />
        </label>
        {draft.mode === "edit" && (
          <label className="image-mask-field">
            <span>蒙版图片地址</span>
            <textarea
              rows="2"
              value={draft.maskImageUrlsText || ""}
              onChange={(event) => update("maskImageUrlsText", event.target.value)}
              placeholder="https://example.com/mask.png"
            />
          </label>
        )}
      </div>
    </section>
  );
}

function DataWorkspacePanel({ draft, onChange, catalog, catalogLoading, catalogError }) {
  const models = Array.isArray(catalog?.models) ? catalog.models : [];
  const update = (field, value) => onChange({ ...draft, [field]: value });
  const clear = () => onChange({
    rowsJson: "",
    columnsText: "",
    modelCodeText: "",
    schemaInfoJson: "",
    businessKnowledge: ""
  });
  const applyCatalog = () => {
    onChange({ ...draft, ...buildWorkspaceDataCatalogDraft(catalog) });
  };
  return (
    <section className="data-workspace-panel">
      <div className="data-workspace-head">
        <div>
          <strong>数据上下文</strong>
          <span>结构化数据会随下一次数据问答一起提交</span>
        </div>
        <div>
          <button type="button" onClick={applyCatalog} disabled={catalogLoading || models.length === 0}>
            {catalogLoading ? <Loader2 size={14} className="spin" /> : <BookOpen size={14} />}
            使用目录
          </button>
          <button type="button" onClick={clear}>清空</button>
        </div>
      </div>
      {catalogError && <div className="data-workspace-catalog-error">{catalogError}</div>}
      {models.length > 0 && (
        <div className="data-workspace-catalog">
          {models.map((model) => (
            <span key={model.modelCode || model.tableName}>
              <b>{model.displayName || model.modelCode}</b>
              {model.modelCode || model.tableName}
            </span>
          ))}
        </div>
      )}
      <div className="data-workspace-grid">
        <label>
          <span>字段列表</span>
          <input
            value={draft.columnsText}
            onChange={(event) => update("columnsText", event.target.value)}
            placeholder="pay_status, count, amount"
          />
        </label>
        <label>
          <span>模型编码</span>
          <input
            value={draft.modelCodeText}
            onChange={(event) => update("modelCodeText", event.target.value)}
            placeholder="trade_order, quota_flow"
          />
        </label>
        <label className="wide">
          <span>业务知识</span>
          <textarea
            value={draft.businessKnowledge}
            onChange={(event) => update("businessKnowledge", event.target.value)}
            placeholder="拼团支付成功后需等待成团再发放额度"
          />
        </label>
        <label className="wide">
          <span>表格行 JSON</span>
          <textarea
            value={draft.rowsJson}
            onChange={(event) => update("rowsJson", event.target.value)}
            placeholder='[{"pay_status":"PAY_SUCCESS","count":12}]'
          />
        </label>
        <label className="wide">
          <span>表结构 JSON</span>
          <textarea
            value={draft.schemaInfoJson}
            onChange={(event) => update("schemaInfoJson", event.target.value)}
            placeholder='[{"table":"trade_order","columns":["pay_status","order_status"]}]'
          />
        </label>
      </div>
    </section>
  );
}

function formatTradeNumber(value) {
  return Number(value || 0).toFixed(2);
}

function tradeOrderAmount(order = {}) {
  return formatTradeNumber(order.payAmount || order.totalAmount || order.amount || order.lockAmount);
}

function TradeWorkspacePanel({ summary, loading, onRefresh, onOpenRecharge, onAuditOrder }) {
  const stats = [
    { label: "可用额度", value: `${formatTradeNumber(summary.quotaBalance)} 点` },
    { label: "已消耗", value: `${formatTradeNumber(summary.usedQuota)} 点` },
    { label: "拼团订单", value: `${summary.groupOrders} 单` },
    { label: "等待成团", value: `${summary.waitingGroupOrders} 单`, danger: summary.waitingGroupOrders > 0 }
  ];

  return (
    <section className="trade-workspace-panel">
      <div className="trade-workspace-head">
        <div>
          <strong>交易闭环看板</strong>
          <span>把额度账户、拼团订单、支付状态和额度流水放在同一个工作区核对</span>
        </div>
        <div>
          <button type="button" onClick={onOpenRecharge}>
            <Wallet size={15} />
            <span>购买额度</span>
          </button>
          <button type="button" onClick={onRefresh} disabled={loading}>
            {loading ? <Loader2 size={15} className="spin" /> : <RotateCcw size={15} />}
            <span>刷新订单</span>
          </button>
        </div>
      </div>
      <div className="trade-stat-grid">
        {stats.map((item) => (
          <div className={`trade-stat-card ${item.danger ? "danger" : ""}`} key={item.label}>
            <span>{item.label}</span>
            <strong>{item.value}</strong>
          </div>
        ))}
      </div>
      <div className="trade-consistency-list">
        {summary.consistencyHints.map((hint) => (
          <span key={hint}>{hint}</span>
        ))}
      </div>
      <div className="trade-workspace-grid">
        <div>
          <div className="trade-workspace-subhead">
            <strong>最近订单</strong>
            <span>{summary.totalOrders} 单</span>
          </div>
          {summary.recentOrders.length === 0 && <p className="trade-workspace-empty">暂无订单</p>}
          {summary.recentOrders.map((order, index) => {
            const status = order.orderStatus || order.status || order.payStatus;
            return (
              <article className="trade-order-card" key={order.orderId || order.outTradeNo || index}>
                <div>
                  <strong>{order.productName || order.goodsName || order.productId || "额度订单"}</strong>
                  <span>{order.orderId || order.outTradeNo || "未生成订单号"}</span>
                </div>
                <em>{Number(order.marketType || 0) === 1 ? "拼团" : "直购"}</em>
                <b>{tradeOrderStatusLabel(status)}</b>
                <span>¥{tradeOrderAmount(order)}</span>
                <button type="button" className="trade-order-audit" onClick={() => onAuditOrder?.(order)}>
                  <ShieldCheck size={14} />
                  <span>审计</span>
                </button>
              </article>
            );
          })}
        </div>
        <div>
          <div className="trade-workspace-subhead">
            <strong>最近额度流水</strong>
            <span>{summary.recentFlows.length} 条</span>
          </div>
          {summary.recentFlows.length === 0 && <p className="trade-workspace-empty">暂无流水</p>}
          {summary.recentFlows.map((flow, index) => (
            <article className="trade-flow-card" key={flow.flowId || flow.bizId || index}>
              <div>
                <strong>{flow.bizType || flow.flowType || "额度流水"}</strong>
                <span>{flow.bizId || flow.remark || flow.createTime || ""}</span>
              </div>
              <b>{formatTradeNumber(flow.quotaAmount)}</b>
            </article>
          ))}
        </div>
      </div>
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
          <span>{loading ? "读取中" : "刷新"}</span>
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
          <span>{actionKey === "web-url" ? "导入中" : "加入网页"}</span>
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
          <span>{actionKey === "upload" ? "上传中" : "上传资料"}</span>
        </button>
        <button type="button" onClick={onRebuild} disabled={!hasAdminAuth || Boolean(actionKey)}>
          <RotateCcw size={15} />
          <span>{actionKey === "rebuild" ? "重建中" : "重建向量"}</span>
        </button>
        <button type="button" onClick={onCompensate} disabled={!hasAdminAuth || Boolean(actionKey)}>
          <AlertTriangle size={15} />
          <span>{actionKey === "compensate" ? "补偿中" : "失败补偿"}</span>
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
            {hasAdminAuth ? "暂无知识文档" : "保存后台权限后读取知识文档"}
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

function WorkspaceEmptyState({ workspace, profile, capabilities, onPrompt, onOpenRecharge }) {
  const prompts = WORKSPACE_PROMPTS[workspace.id] || WORKSPACE_PROMPTS.agent;
  const serviceProfile = profile || workspaceServiceProfile(workspace.id);
  const capabilityStatus = workspaceCapabilityStatus(workspace.id, capabilities);
  const toolReadiness = workspaceToolReadiness(workspace.id, capabilities);
  const isImage = workspace.id === "image";
  const isData = workspace.id === "data";
  const isMrag = workspace.id === "mrag";
  const isTrade = workspace.id === "trade";
  const showWorkspaceRuntime = isImage || isData || isMrag || isTrade;
  const manualSkills = Array.isArray(capabilities?.manualSkills)
    ? capabilities.manualSkills.slice(0, 6)
    : [];
  return (
    <div className={`empty-state workspace-empty workspace-empty-${workspace.id}`}>
      <div className="empty-icon-wrapper">
        <div className="empty-icon">{workspace.icon}</div>
        <div className="icon-glow" />
      </div>
      <h2>{workspace.name}</h2>
      <p>{serviceProfile.summary}</p>
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
            <strong>工具准备度</strong>
            <em>{toolReadiness.statusLabel}</em>
          </div>
          <div className="workspace-readiness-metrics">
            <span><b>可用</b>{toolReadiness.readyTools.length}/{toolReadiness.requiredTools.length}</span>
            <span><b>输出</b>{toolReadiness.outputKinds.map((kind) => OUTPUT_KIND_LABELS[kind] || kind).slice(0, 4).join("、") || "-"}</span>
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
        <div className="workspace-tool-strip">
          {serviceProfile.primaryTools.map((toolName) => (
            <span key={toolName}>{TOOL_LABELS[toolName] || toolName}</span>
          ))}
        </div>
      )}
      {showWorkspaceRuntime && (
        <div className="workspace-output-strip">
          {serviceProfile.outputKinds.map((kind) => (
            <span key={kind}>{OUTPUT_KIND_LABELS[kind] || kind}</span>
          ))}
        </div>
      )}
      {manualSkills.length > 0 && (
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

function SessionMemoryPanel({ memory }) {
  if (!hasSessionMemory(memory)) return null;
  const runs = memory.runs || [];
  const observations = memory.toolObservations || [];
  const artifacts = memory.reusableArtifacts || [];
  const latestArtifact = artifacts[0];
  return (
    <section className="session-memory-panel">
      <div className="session-memory-head">
        <strong>会话记忆</strong>
        <span>{memory.summary || "历史执行结果已进入当前智能体上下文"}</span>
      </div>
      <div className="session-memory-stats">
        <span>运行 <b>{runs.length}</b></span>
        <span>工具观察 <b>{observations.length}</b></span>
        <span>可复用产物 <b>{artifacts.length}</b></span>
      </div>
      {latestArtifact && (
        <div className="session-memory-artifact">
          可复用：{latestArtifact.title || latestArtifact.fileName || latestArtifact.artifactId}
        </div>
      )}
    </section>
  );
}

function formatPanelValue(value) {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

function hostFromUrl(url = "") {
  const value = String(url || "").trim();
  if (!value) return "";
  try {
    return new URL(value.startsWith("http") ? value : `https://${value}`).hostname.replace(/^www\./, "");
  } catch {
    return value.replace(/^https?:\/\//, "").split("/")[0] || value;
  }
}

function numericValue(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value.replace(/,/g, ""));
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function buildDataChartPreview(panel = {}) {
  const rows = Array.isArray(panel.rows) ? panel.rows : [];
  const columns = Array.isArray(panel.columns) ? panel.columns : [];
  if (!rows.length || !columns.length) return null;
  const measure = columns.find((column) => rows.some((row) => numericValue(row?.[column]) !== null));
  if (!measure) return null;
  const dimension = columns.find((column) => column !== measure && rows.some((row) => numericValue(row?.[column]) === null))
    || columns.find((column) => column !== measure)
    || measure;
  const points = rows
    .slice(0, 6)
    .map((row, index) => ({
      label: formatPanelValue(row?.[dimension] ?? `#${index + 1}`),
      value: numericValue(row?.[measure]) ?? 0
    }));
  const maxValue = Math.max(...points.map((item) => Math.abs(item.value)), 0);
  if (!maxValue) return null;
  return { dimension, measure, points, maxValue };
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
            <em>{TOOL_LABELS[panel.toolName] || panel.toolName || panel.kind}</em>
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
                        <span>{file.fileSize ? formatFileSize(file.fileSize) : file.type || file.contentType || "文件"}</span>
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
                        <span>{file.fileSize ? formatFileSize(file.fileSize) : file.type || file.contentType || "文件"}</span>
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
                {["mode", "size", "batchCount", "provider", "usedFallback"].map((key) => (
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
                          <strong>{file.title || file.fileName || "生成图片"}</strong>
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
                        <strong>{file.title || file.fileName || "多模态产物"}</strong>
                        <span>{file.fileSize ? formatFileSize(file.fileSize) : file.type || file.contentType || "文件"}</span>
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
                          <span>{file.fileSize ? formatFileSize(file.fileSize) : file.type || file.contentType || "文件"}</span>
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

function AgentRunDigestPanel({ digest }) {
  if (!digest?.visible) return null;
  return (
    <section className={`agent-run-digest ${digest.status}`}>
      <div className="agent-run-digest-head">
        <span>{digest.statusLabel}</span>
        <strong>执行摘要</strong>
      </div>
      {digest.metrics.length > 0 && (
        <div className="agent-run-digest-metrics">
          {digest.metrics.map((metric) => (
            <span className={metric.tone || "normal"} key={metric.key}>
              <b>{metric.value}</b>
              {metric.label}
            </span>
          ))}
        </div>
      )}
      {digest.highlights.length > 0 && (
        <div className="agent-run-digest-highlights">
          {digest.highlights.map((item, index) => (
            <p key={`${item}-${index}`}>{item}</p>
          ))}
        </div>
      )}
    </section>
  );
}

function MessageItem({ msg, copied, isSending, isLast, onCopy, onToggleTimeline, onToggleReference, onRecommendClick, onDownloadArtifact }) {
  const isUser = msg.role === "user";
  const plannerHistory = !isUser ? buildPlannerHistory(msg.timeline || []) : [];
  const runDigest = !isUser ? buildAgentRunDigest(msg) : null;
  const [previewArtifactKey, setPreviewArtifactKey] = useState("");
  return (
    <div className={`message ${msg.role}`}>
      <div className="message-avatar">{isUser ? "👤" : "🤖"}</div>
      <div className="message-content">
        {isUser ? (
          <>
            <div className="user-message">
              {msg.file && <span className="file-attachment"><Paperclip size={14} />{msg.fileName}</span>}
              <div>{msg.content}</div>
            </div>
            <button className="copy-btn copy-btn-user" onClick={() => onCopy(msg)}>
              {copied ? <Check size={15} /> : <Copy size={15} />}
            </button>
          </>
        ) : (
          <div className="ai-message">
            <AgentRunDigestPanel digest={runDigest} />
            {plannerHistory.length > 0 && (
              <div className="planner-history">
                <div className="planner-history-header">
                  <span>计划历史</span>
                  <b>{plannerHistory.length} 版</b>
                </div>
                <div className="planner-history-list">
                  {plannerHistory.map((version, index) => (
                    <div className={`planner-history-item ${version.latest ? "latest" : ""}`} key={version.id}>
                      <span className={`planner-history-status ${version.status}`}>{version.status}</span>
                      <strong>{index + 1}. {version.title}</strong>
                      <small>
                        第 {version.revision || index + 1} 版
                        {version.changeType === "replan" ? "，重规划" : ""}
                        {"，"}
                        {version.stageCount > 0 ? `${version.stageCount} 阶段，` : ""}
                        {version.stepCount} 步
                        {version.flowUpdates > 0 ? `，${version.flowUpdates} 次推进` : ""}
                      </small>
                      {version.replanReason && <em>原因：{version.replanReason}</em>}
                      {version.summary && <em>{version.summary}</em>}
                    </div>
                  ))}
                </div>
              </div>
            )}
            {msg.timeline?.length > 0 && (
              <div className="timeline-section">
                <button className="timeline-header" onClick={() => onToggleTimeline(msg.id)}>
                  <span className="timeline-icon-wrapper">⚙️</span>
                  <span className="timeline-title">执行过程</span>
                  <span>{msg.showTimeline ? "⌄" : "›"}</span>
                </button>
                {msg.showTimeline && (
                  <div className="timeline-content">
                    {msg.timeline.map((item, index) => {
                      const statusClass = timelineItemStatus(item);
                      const statusLabel = timelineItemStatusLabel(item);
                      return (
                      <div className="timeline-item" key={`${item.type}-${index}`}>
                        <div className={`timeline-dot ${statusClass}`} title={statusLabel} />
                        <div className="timeline-item-body">
                          {item.type === "thinking" && <div className="timeline-thinking">{item.content}</div>}
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
                            </div>
                          )}
                          {item.type === "llm" && (
                            <div className="timeline-llm">
                              <span className="timeline-tool-name">{item.modelName}</span>
                              <span className={`timeline-inline-status ${statusClass}`}>{statusLabel}</span>
                              <small>{item.tokens || 0} tokens · {item.latencyMillis || 0} ms</small>
                            </div>
                          )}
                          {item.type === "error" && <div className="timeline-error"><AlertTriangle size={14} /><span>{item.message}</span></div>}
                        </div>
                      </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            <MarkdownRenderer content={msg.content} />
            {isSending && isLast && (
              <div className="thinking-loading"><span className="dot" /><span className="dot" /><span className="dot" /></div>
            )}

            <ResultPanelList panels={msg.resultPanels || []} onDownloadArtifact={onDownloadArtifact} />

            {msg.reference?.length > 0 && (
              <div className="reference-section">
                <button className="reference-header" onClick={() => onToggleReference(msg.id)}>
                  <span className="reference-icon-wrapper">📚</span>
                  <span className="reference-title">参考来源 ({msg.reference.length})</span>
                  <span>{msg.showReference ? "⌄" : "›"}</span>
                </button>
                {msg.showReference && (
                  <div className="reference-content">
                    {msg.reference.map((ref, index) => (
                      <div className="reference-link" key={`${ref.title}-${index}`}>
                        <div className="ref-icon">↗</div>
                        <div className="ref-info">
                          {safeExternalUrl(ref.url) ? (
                            <a className="ref-title-text" href={safeExternalUrl(ref.url)} target="_blank" rel="noreferrer">
                              {ref.title || ref.url || "参考资料"}
                            </a>
                          ) : (
                            <div className="ref-title-text">{ref.title || "参考资料"}</div>
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

            <button className="copy-btn" onClick={() => onCopy(msg)}>
              {copied ? <Check size={15} /> : <Copy size={15} />}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function AuthDialog({ mode, setMode, form, setForm, error, onSubmit, onClose }) {
  return (
    <div className="modal-overlay">
      <form className="auth-dialog" onSubmit={onSubmit}>
        <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
        <img className="auth-logo" src="/bear-doctor-logo.png" alt="熊博士" />
        <h3>{mode === "login" ? "登录熊博士Agent" : "注册账号"}</h3>
        <div className="auth-switch">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>登录</button>
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>注册</button>
        </div>
        <input name="username" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="账号" autoComplete="username" required />
        <input name="password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} type="password" placeholder="密码" autoComplete={mode === "login" ? "current-password" : "new-password"} required />
        {mode === "register" && (
          <>
            <input name="nickname" value={form.nickname} onChange={(event) => setForm({ ...form, nickname: event.target.value })} placeholder="昵称" autoComplete="nickname" />
            <input name="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="邮箱" autoComplete="email" />
          </>
        )}
        {error && <div className="auth-error">{error}</div>}
        <button className="auth-submit" type="submit">{mode === "login" ? "登录" : "注册并登录"}</button>
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
        <label>
          <span>API 地址</span>
          <input
            value={draft.baseUrl || ""}
            onChange={(event) => update("baseUrl", event.target.value)}
            placeholder="https://dashscope.aliyuncs.com/compatible-mode"
            disabled={!draft.enabled}
          />
        </label>
        <label>
          <span>API 密钥</span>
          <input
            value={draft.apiKey || ""}
            onChange={(event) => update("apiKey", event.target.value)}
            type="password"
            placeholder="sk-..."
            disabled={!draft.enabled}
          />
        </label>
        <label>
          <span>模型名称</span>
          <input
            value={draft.model || ""}
            onChange={(event) => update("model", event.target.value)}
            placeholder="qwen3.6-plus"
            disabled={!draft.enabled}
          />
        </label>
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
  adminForm,
  setAdminForm,
  onBuy,
  onOpenGroupPreview,
  onBackToPackages,
  onSaveAdminAuth,
  onRefresh,
  onPayOrder,
  onClose
}) {
  const [now, setNow] = useState(() => Date.now());
  const formatMoney = (value) => Number(value || 0).toFixed(2);
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
  const teamSize = Number(groupPreviewPackage?.teamSize || teamList[0]?.targetCount || 2);
  const quotaAmount = Number(groupPreviewPackage?.quotaAmount || 0).toFixed(0);
  const isGroupBuying = Boolean(groupPreviewPackage && buyingKey.startsWith(`${groupPreviewPackage.goodsId}-group`));
  const isDirectBuying = Boolean(groupPreviewPackage && buyingKey === `${groupPreviewPackage.goodsId}-direct`);
  const previewProduct = groupPreviewPackage ? {
    ...groupPreviewPackage,
    activityId: groupMarketConfig?.activityId || groupPreviewPackage.activityId,
    originPrice: marketGoods.originalPrice || groupPreviewPackage.originPrice,
    groupPrice: marketGoods.payPrice || groupPreviewPackage.groupPrice
  } : null;

  if (groupPreviewPackage) {
    return (
      <div className="modal-overlay">
        <div className="recharge-dialog group-detail-dialog">
          <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
          <div className="group-detail-topbar">
            <button type="button" className="group-back" onClick={onBackToPackages}>
              <ArrowLeft size={16} />
            </button>
            <strong>额度包详情</strong>
          </div>

          <div className="group-detail-main">
            <div className="group-product-icon">⚙️</div>
            <h3>{groupPreviewPackage.goodsName || `可调用次数 - ${quotaAmount}次`}</h3>
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
                <CreditCard size={16} /> {isDirectBuying ? "处理中" : `直接购买 ¥${formatMoney(previewProduct.originPrice)}`}
              </button>
              <button className="primary" type="button" onClick={() => onBuy(previewProduct, "group")} disabled={Boolean(buyingKey)}>
                <UserPlus size={16} /> {isGroupBuying ? "处理中" : `自己开团 ¥${formatMoney(previewProduct.groupPrice)}`}
              </button>
            </div>
            <div className="group-detail-tip">{teamSize} 人成团，先锁单占位，确认支付后等待成团到账。</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay">
      <div className="recharge-dialog">
        <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
        <div className="recharge-head">
          <div>
            <h3>额度中心</h3>
            <p>购买额度后可用于对话、文件问答、生成 PPT、深度研究和技能调用</p>
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
        <div className="recharge-tabs">
          <button type="button" className={activeTab === "packages" ? "active" : ""} onClick={() => setActiveTab("packages")}>
            <Wallet size={15} /> 额度包
          </button>
          <button type="button" className={activeTab === "orders" ? "active" : ""} onClick={() => setActiveTab("orders")}>
            <CreditCard size={15} /> 订单/拼团
          </button>
        </div>

        {activeTab === "packages" && (
          <>
            <div className="package-grid">
              {packages.map((pkg) => (
                <article className="quota-package" key={pkg.goodsId}>
                  <h4>{pkg.goodsName}</h4>
                  <p>{pkg.specSummary}</p>
                  <div className="pkg-amount">{Number(pkg.quotaAmount || 0).toFixed(0)} 点</div>
                  <div className="pkg-actions">
                    <button onClick={() => onBuy(pkg, "direct")} disabled={Boolean(buyingKey)}>
                      ¥{Number(pkg.originPrice || 0).toFixed(2)}
                    </button>
                    <button className="group" onClick={() => onOpenGroupPreview(pkg)} disabled={Boolean(buyingKey)}>
                      {buyingKey === `${pkg.goodsId}-group` ? "处理中" : `${pkg.teamSize || 2}人团 ¥${Number(pkg.groupPrice || 0).toFixed(2)}`}
                    </button>
                  </div>
                </article>
              ))}
              {packages.length === 0 && <div className="empty-package">后端启动后会显示额度包</div>}
            </div>
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
                    <span>¥{formatMoney(order.payAmount || order.totalAmount)}</span>
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
        <div className="admin-auth-box">
          <div>
            <strong>模拟支付授权</strong>
            <span>本地演示支付回调时使用</span>
          </div>
          <input value={adminForm.username} onChange={(event) => setAdminForm({ ...adminForm, username: event.target.value })} placeholder="运营账号" />
          <input value={adminForm.password} onChange={(event) => setAdminForm({ ...adminForm, password: event.target.value })} type="password" placeholder="运营密码" />
          <button onClick={onSaveAdminAuth}><CreditCard size={15} /> 保存</button>
        </div>
      </div>
    </div>
  );
}

function PaymentConfirmDialog({ payment, buyingKey, onConfirm, onCancel }) {
  const amount = Number(payment?.amount || 0).toFixed(2);
  const isGroupOrder = Number(payment?.marketType) === 1;
  const paying = buyingKey === `pay-${payment?.orderId}`;

  return (
    <div className="modal-overlay payment-overlay">
      <div className="payment-confirm-dialog">
        <button type="button" className="modal-close" onClick={onCancel} disabled={paying}><X size={18} /></button>
        <div className="payment-icon">
          <CreditCard size={24} />
        </div>
        <h3>确认支付</h3>
        <p>{isGroupOrder ? "拼团订单支付成功后，满员成团才会发放额度。" : "直购订单支付成功后，额度会立即到账。"}</p>
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
            <b>¥{amount}</b>
          </div>
        </div>
        <div className="payment-confirm-actions">
          <button type="button" onClick={onCancel} disabled={paying}>取消</button>
          <button type="button" className="primary" onClick={onConfirm} disabled={paying}>
            {paying ? <Loader2 size={16} className="spin" /> : <CreditCard size={16} />}
            {paying ? "支付中" : "确认支付"}
          </button>
        </div>
      </div>
    </div>
  );
}

function formatFileSize(size = 0) {
  if (!size) return "-";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function normalizeRecommendItems(value) {
  let raw = value;
  if (typeof raw === "string") {
    try {
      raw = JSON.parse(raw);
    } catch {
      raw = [raw];
    }
  }
  if (raw && !Array.isArray(raw)) {
    raw = raw.items || raw.questions || raw.recommends || [raw];
  }
  return (Array.isArray(raw) ? raw : [])
    .map((item) => {
      if (typeof item === "string") return item;
      if (!item || typeof item !== "object") return "";
      return item.question || item.content || item.title || item.text || item.name || "";
    })
    .map((item) => String(item || "").trim())
    .filter(Boolean)
    .slice(0, 6);
}

export default App;
