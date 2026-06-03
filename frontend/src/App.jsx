import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  BookOpen,
  Check,
  Copy,
  CreditCard,
  Download,
  FileText,
  Loader2,
  LogIn,
  LogOut,
  Paperclip,
  Plus,
  RotateCcw,
  Send,
  Settings,
  Square,
  Trash2,
  UserPlus,
  Wallet,
  X
} from "lucide-react";
import AdminDashboard from "./components/AdminDashboard";
import ThemeToggle from "./components/ThemeToggle";
import {
  createDirectOrder,
  deleteAcademicSession,
  downloadAcademicArtifact,
  getAdminAuth,
  getModelConfig,
  getQuotaSummary,
  getSessionId,
  getUserAuth,
  lockMarketPayOrder,
  login,
  logout,
  modelConfigReady,
  mockPaySuccess,
  normalizeApiMessage,
  queryAcademicTaskStatus,
  queryAcademicSessionDetail,
  queryAcademicSessions,
  queryGroupBuyMarketConfig,
  queryQuotaPackages,
  queryUserOrderList,
  register,
  requestAcademicAttachStream,
  requestAcademicResumeStream,
  requestAcademicStream,
  saveAdminAuth,
  saveModelConfig,
  stopAcademicStream,
  uploadAcademicFile
} from "./services/api";
import { applyTheme, getStoredTheme, nextTheme } from "./theme";

const AGENTS = [
  { id: "chat", name: "对话助手", icon: "💬" },
  { id: "file", name: "文件问答", icon: "📁" },
  { id: "ppt", name: "PPT生成", icon: "📊" },
  { id: "deep", name: "深度研究", icon: "🔬" },
  { id: "skills", name: "技能助手", icon: "🛠" }
];

const EMPTY_MESSAGES = [];

const normalizeUserMessage = normalizeApiMessage;

function createRuntimeId(prefix) {
  return `${prefix}${Date.now()}`;
}

function mergeThinking(timeline = [], content) {
  const last = timeline[timeline.length - 1];
  if (last?.type === "thinking") {
    return [...timeline.slice(0, -1), { ...last, content }];
  }
  return [...timeline, { type: "thinking", content }];
}

function toUiArtifact(data = {}) {
  return {
    id: data.artifactId || `${data.fileName || data.title || "artifact"}_${data.downloadUrl || ""}`,
    title: data.title || data.fileName || "生成文件",
    type: data.artifactType || data.type || "ARTIFACT",
    fileName: data.fileName || data.title || "artifact",
    fileSize: data.fileSize || 0,
    content: data.content || data.fileName || "",
    downloadUrl: data.downloadUrl || ""
  };
}

function App() {
  const path = window.location.pathname.replace(/\/+$/, "") || "/";

  if (path === "/admin" || path === "/admin.html") return <AdminDashboard />;

  return <BearDoctorAcademicApp />;
}

function BearDoctorAcademicApp() {
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
  const [selectedAgent, setSelectedAgent] = useState("chat");
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
  const messagesContainer = useRef(null);
  const fileInputRef = useRef(null);
  const streamControllersRef = useRef({});

  const currentChat = useMemo(() => chatList.find((item) => item.id === currentChatId), [chatList, currentChatId]);
  const backendText = auth?.token ? `已登录：${auth.nickname || auth.username || auth.userId}` : "未登录";
  const currentTaskStatus = taskStatusByChat[currentChatId] || {};
  const isSending = Boolean(runningChatIds[currentChatId]);
  const canResumeCurrentChat = Boolean((currentTaskStatus.stopped || currentChat?.stopped) && !isSending);
  const canUseFile = selectedAgent === "file" || selectedAgent === "skills";

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

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

  const toUiMessages = useCallback((items = []) => items.map((item, index) => ({
    id: `${item.role || "MSG"}_${index}_${item.createTime || "local"}`,
    role: item.role === "USER" ? "user" : "assistant",
    content: item.content || "",
    timeline: [],
    reference: [],
    recommend: [],
    artifacts: (item.artifacts || []).map(toUiArtifact),
    showTimeline: false,
    showReference: false
  })), []);

  const refreshSessionDetail = useCallback(async (sessionId, keepMessageId = "") => {
    if (!getUserAuth()?.token || !sessionId) return;
    const res = await queryAcademicSessionDetail(sessionId);
    if (res.code !== "0000") return;
    const remoteMessages = toUiMessages(res.data?.messages || []);
    setChatList((prev) => prev.map((chat) => {
      if (chat.id !== sessionId) return chat;
      const runningMessage = keepMessageId
        ? chat.messages.find((message) => message.id === keepMessageId)
        : null;
      const shouldKeepRunning = runningMessage && runningMessage.content;
      return {
        ...chat,
        isNew: false,
        messages: shouldKeepRunning ? [...remoteMessages, runningMessage] : remoteMessages
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
      const hasError = (message.timeline || []).some((item) => item.type === "error");
      return { ...message, showTimeline: hasError };
    });
  }, [updateAssistantInChat]);

  const processStreamEvent = useCallback((chatId, messageId, event) => {
    const data = event.data || {};
    if (event.event === "answer_delta") {
      appendAssistantTextInChat(chatId, messageId, data.content || "");
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
        reference: [...(message.reference || []), {
          title: data.title || data.fileId || "参考资料",
          text: data.content || ""
        }],
        showReference: true
      }));
      return;
    }
    if (event.event === "artifact_delta") {
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        artifacts: [...(message.artifacts || []), toUiArtifact(data)]
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
    if (event.event === "quota_delta") {
      setQuota(data);
      return;
    }
    if (event.event === "usage_metric") {
      updateAssistantInChat(chatId, messageId, (message) => ({
        ...message,
        timeline: [...(message.timeline || []), {
          type: "tool",
          toolName: `额度消耗 ${data.consumedQuota ?? "-"}，剩余额度 ${data.remainingQuota ?? "-"}`,
          status: "completed"
        }]
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

  const refreshRecharge = useCallback(async () => {
    await Promise.all([
      loadQuota().catch(() => {}),
      loadOrders().catch(() => {})
    ]);
  }, [loadQuota, loadOrders]);

  useEffect(() => {
    ensureChat(currentChatId);
  }, [currentChatId, ensureChat]);

  useEffect(() => {
    loadPackages().catch((error) => console.warn("额度包读取失败", error));
  }, [loadPackages]);

  useEffect(() => {
    if (!auth?.token) return;
    loadQuota().catch((error) => setConnectionError(normalizeUserMessage(error.message, "额度读取失败")));
    loadSessions().catch(() => {});
    loadOrders().catch(() => {});
  }, [auth, loadOrders, loadQuota, loadSessions]);

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
    if (agentId !== "file" && agentId !== "skills") {
      setSelectedFile(null);
    }
  };

  const quickPrompt = (prompt) => {
    setInputMessage(prompt);
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
    const taskType = selectedAgent;
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

    const userMsg = {
      id: createRuntimeId("U"),
      role: "user",
      content: text || "请分析这个文件",
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

    streamControllersRef.current[sessionId] = requestAcademicStream(
      {
        sessionId,
        question: text || "请分析这个文件",
        taskType,
        fileId: file?.fileId || "",
        modelConfig
      },
      (event) => processStreamEvent(sessionId, assistantId, event),
      () => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        closeAssistantTimelineInChat(sessionId, assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        loadTaskStatus(sessionId).catch(() => {});
      },
      (error) => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        appendAssistantTextInChat(sessionId, assistantId, `\n\n请求出错：${normalizeUserMessage(error.message, "服务暂不可用")}`);
        loadTaskStatus(sessionId).catch(() => {});
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
      (event) => processStreamEvent(sessionId, assistantId, event),
      () => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        closeAssistantTimelineInChat(sessionId, assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        loadTaskStatus(sessionId).catch(() => {});
      },
      (error) => {
        delete streamControllersRef.current[sessionId];
        setChatRunning(sessionId, false);
        appendAssistantTextInChat(sessionId, assistantId, `\n\n继续生成失败：${normalizeUserMessage(error.message, "服务暂不可用")}`);
        loadTaskStatus(sessionId).catch(() => {});
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
            {(!currentChat || currentChat.messages.length === 0) ? (
              <div className="empty-state">
                <div className="empty-icon-wrapper">
                  <div className="empty-icon">🤖</div>
                  <div className="icon-glow" />
                </div>
                <h2>你好，我是熊博士Agent</h2>
                <p>可以帮你问答、读文件、做 PPT、深度研究和调用技能</p>
                <div className="quick-actions">
                  <div className="quick-action" onClick={() => quickPrompt("帮我阅读这篇论文，并输出精读笔记")}>
                    <BookOpen size={18} />
                    <span>论文精读</span>
                  </div>
                  <div className="quick-action" onClick={() => quickPrompt("帮我生成一份组会汇报 PPT 大纲")}>
                    <span>📊</span>
                    <span>PPT 大纲</span>
                  </div>
                  <div className="quick-action" onClick={() => quickPrompt("帮我调研大模型智能体应用的最新进展")}>
                    <span>🔬</span>
                    <span>深度研究</span>
                  </div>
                </div>
              </div>
            ) : (
              currentChat.messages.map((msg) => (
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
              ))
            )}
          </div>

          <div className="input-area">
            <div className="agent-selector">
              {AGENTS.map((agent) => (
                <button key={agent.id} className={`agent-item ${selectedAgent === agent.id ? "active" : ""}`} onClick={() => selectAgent(agent.id)}>
                  <span className="agent-icon">{agent.icon}</span>
                  <span className="agent-name">{agent.name}</span>
                  {selectedAgent === agent.id && <Check size={12} className="check-icon" />}
                </button>
              ))}
            </div>

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
              <input ref={fileInputRef} type="file" onChange={handleFileSelect} hidden />
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

function MessageItem({ msg, copied, isSending, isLast, onCopy, onToggleTimeline, onToggleReference, onRecommendClick, onDownloadArtifact }) {
  const isUser = msg.role === "user";
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
            {msg.timeline?.length > 0 && (
              <div className="timeline-section">
                <button className="timeline-header" onClick={() => onToggleTimeline(msg.id)}>
                  <span className="timeline-icon-wrapper">🧠</span>
                  <span className="timeline-title">思考过程</span>
                  <span>{msg.showTimeline ? "⌄" : "›"}</span>
                </button>
                {msg.showTimeline && (
                  <div className="timeline-content">
                    {msg.timeline.map((item, index) => (
                      <div className="timeline-item" key={`${item.type}-${index}`}>
                        <div className={`timeline-dot ${item.status || item.type}`} />
                        <div className="timeline-item-body">
                          {item.type === "thinking" && <div className="timeline-thinking">{item.content}</div>}
                          {item.type === "tool" && <div className="timeline-tool"><span>🔧</span><span className="timeline-tool-name">{item.toolName}</span></div>}
                          {item.type === "error" && <div className="timeline-error"><AlertTriangle size={14} /><span>{item.message}</span></div>}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="text-content markdown-body">{msg.content}</div>
            {isSending && isLast && (
              <div className="thinking-loading"><span className="dot" /><span className="dot" /><span className="dot" /></div>
            )}

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
                          <div className="ref-title-text">{ref.title || "参考资料"}</div>
                          <div className="ref-url-text">{ref.text}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {msg.artifacts?.length > 0 && (
              <div className="artifact-section">
                {msg.artifacts.map((artifact, index) => (
                  <div className="artifact-card" key={`${artifact.title}-${index}`}>
                    <div className="artifact-title">{artifact.title}<span>{artifact.type}</span></div>
                    {artifact.downloadUrl && (
                      <button type="button" className="artifact-download" onClick={() => onDownloadArtifact(artifact)}>
                        <Download size={15} />
                        <span>下载 {artifact.type || "文件"}</span>
                      </button>
                    )}
                    <pre>{artifact.fileName || artifact.content}</pre>
                    {artifact.fileSize ? <small>{formatFileSize(artifact.fileSize)}</small> : null}
                  </div>
                ))}
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
