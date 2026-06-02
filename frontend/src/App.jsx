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
  Square,
  Trash2,
  UserPlus,
  Wallet,
  X
} from "lucide-react";
import AdminDashboard from "./components/AdminDashboard";
import {
  createDirectOrder,
  getAdminAuth,
  getQuotaSummary,
  getSessionId,
  getUserAuth,
  lockGroupBuyOrder,
  login,
  logout,
  mockPaySuccess,
  queryAcademicTaskStatus,
  queryAcademicSessionDetail,
  queryAcademicSessions,
  queryQuotaPackages,
  queryUserOrderList,
  register,
  requestAcademicResumeStream,
  requestAcademicStream,
  saveAdminAuth,
  stopAcademicStream,
  uploadAcademicFile
} from "./services/api";

const AGENTS = [
  { id: "chat", name: "对话助手", icon: "💬" },
  { id: "file", name: "文件问答", icon: "📁" },
  { id: "ppt", name: "PPT生成", icon: "📊" },
  { id: "deep", name: "深度研究", icon: "🔬" },
  { id: "skills", name: "技能助手", icon: "🛠" }
];

const EMPTY_MESSAGES = [];

function App() {
  const path = window.location.pathname.replace(/\/+$/, "") || "/";

  if (path === "/admin" || path === "/admin.html") return <AdminDashboard />;

  return <BearDoctorAcademicApp />;
}

function BearDoctorAcademicApp() {
  const [auth, setAuth] = useState(() => getUserAuth());
  const [loginOpen, setLoginOpen] = useState(() => !getUserAuth()?.token);
  const [rechargeOpen, setRechargeOpen] = useState(false);
  const [groupPreviewPackage, setGroupPreviewPackage] = useState(null);
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
  const [isSending, setIsSending] = useState(false);
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
  const streamControllerRef = useRef(null);

  const currentChat = useMemo(() => chatList.find((item) => item.id === currentChatId), [chatList, currentChatId]);
  const backendText = auth?.token ? `已登录：${auth.nickname || auth.username || auth.userId}` : "未登录";
  const currentTaskStatus = taskStatusByChat[currentChatId] || {};
  const canResumeCurrentChat = Boolean((currentTaskStatus.stopped || currentChat?.stopped) && !isSending);
  const canUseFile = selectedAgent === "file" || selectedAgent === "skills";

  const ensureChat = useCallback((sessionId = currentChatId) => {
    setChatList((prev) => {
      if (prev.some((item) => item.id === sessionId)) return prev;
      return [{ id: sessionId, title: "新对话", messages: EMPTY_MESSAGES, isNew: true }, ...prev];
    });
  }, [currentChatId]);

  const updateCurrentChat = useCallback((updater) => {
    setChatList((prev) => prev.map((chat) => {
      if (chat.id !== currentChatId) return chat;
      return typeof updater === "function" ? updater(chat) : { ...chat, ...updater };
    }));
  }, [currentChatId]);

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
      const current = prev.find((item) => item.id === currentChatId) || {
        id: currentChatId,
        title: "新对话",
        messages: EMPTY_MESSAGES,
        isNew: true
      };
      const remote = (res.data || []).map((item) => ({
        id: item.sessionId,
        title: item.title || "学术会话",
        lastMessage: item.lastMessage || "",
        messages: item.sessionId === current.id ? current.messages : EMPTY_MESSAGES,
        isNew: false
      }));
      const merged = [current, ...remote].filter((item, index, list) => list.findIndex((other) => other.id === item.id) === index);
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

  const loadTaskStatus = useCallback(async (sessionId = currentChatId) => {
    if (!getUserAuth()?.token || !sessionId) return;
    const res = await queryAcademicTaskStatus(sessionId);
    if (res.code === "0000") {
      setTaskStatusByChat((prev) => ({ ...prev, [sessionId]: res.data || {} }));
    }
  }, [currentChatId]);

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
    loadQuota().catch((error) => setConnectionError(error.message || "额度读取失败"));
    loadSessions().catch(() => {});
    loadOrders().catch(() => {});
  }, [auth, loadQuota, loadSessions]);

  useEffect(() => {
    if (!auth?.token) return;
    loadTaskStatus(currentChatId).catch(() => {});
  }, [auth, currentChatId, loadTaskStatus]);

  useEffect(() => {
    if (!messagesContainer.current) return;
    messagesContainer.current.scrollTop = messagesContainer.current.scrollHeight;
  }, [chatList, currentChatId, isSending]);

  const createNewChat = () => {
    const id = `AS${Date.now()}`;
    localStorage.setItem("agentGroupSessionId", id);
    setCurrentChatId(id);
    setSelectedFile(null);
    setChatList((prev) => [{ id, title: "新对话", messages: EMPTY_MESSAGES, isNew: true }, ...prev]);
  };

  const deleteChat = (chatId) => {
    setChatList((prev) => prev.filter((item) => item.id !== chatId));
    if (currentChatId === chatId) {
      const next = chatList.find((item) => item.id !== chatId);
      if (next) {
        setCurrentChatId(next.id);
      } else {
        createNewChat();
      }
    }
  };

  const selectChat = async (chatId) => {
    setCurrentChatId(chatId);
    if (!auth?.token) return;
    const chat = chatList.find((item) => item.id === chatId);
    if (!chat || chat.isNew || chat.messages.length > 0) return;
    try {
      const res = await queryAcademicSessionDetail(chatId);
      if (res.code !== "0000") return;
      const messages = (res.data?.messages || []).map((item, index) => ({
        id: `${item.role || "MSG"}_${index}_${item.createTime || Date.now()}`,
        role: item.role === "USER" ? "user" : "assistant",
        content: item.content || "",
        timeline: [],
        reference: [],
        recommend: [],
        artifacts: [],
        showTimeline: false,
        showReference: false
      }));
      setChatList((prev) => prev.map((item) => item.id === chatId ? { ...item, messages } : item));
    } catch (error) {
      setConnectionError(error.message || "会话详情读取失败");
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
    setRechargeOpen(true);
    refreshRecharge().catch(() => {});
  };

  const openGroupPreview = (pkg) => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    setConnectionError("");
    setGroupPreviewPackage(pkg);
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
        setAuthError(res.info || "登录失败");
      }
    } catch (error) {
      setAuthError(error.message || "登录失败");
    }
  };

  const handleLogout = async () => {
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
        setConnectionError(res.info || "文件上传失败");
        setSelectedFile(null);
      }
    } catch (error) {
      setConnectionError(error.message || "文件上传失败");
      setSelectedFile(null);
    } finally {
      setIsUploading(false);
    }
  };

  const sendMessage = () => {
    const text = inputMessage.trim();
    if (isSending || isUploading || (!text && !selectedFile)) return;
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }

    const userMsg = {
      id: `U${Date.now()}`,
      role: "user",
      content: text || "请分析这个文件",
      file: Boolean(selectedFile),
      fileName: selectedFile?.name || ""
    };
    const assistantId = `A${Date.now()}`;
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

    updateCurrentChat((chat) => ({
      ...chat,
      title: chat.isNew && text ? `${text.slice(0, 20)}${text.length > 20 ? "..." : ""}` : chat.title,
      isNew: false,
      stopped: false,
      messages: [...chat.messages, userMsg, assistantMsg]
    }));
    setTaskStatusByChat((prev) => ({ ...prev, [currentChatId]: { ...(prev[currentChatId] || {}), stopped: false, running: true } }));
    setInputMessage("");
    setConnectionError("");
    setIsSending(true);

    streamControllerRef.current = requestAcademicStream(
      {
        sessionId: currentChatId,
        question: text || "请分析这个文件",
        taskType: selectedAgent,
        fileId: selectedFile?.fileId || ""
      },
      (event) => processStreamEvent(assistantId, event),
      () => {
        setIsSending(false);
        streamControllerRef.current = null;
        closeAssistantTimeline(assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        loadTaskStatus(currentChatId).catch(() => {});
      },
      (error) => {
        setIsSending(false);
        streamControllerRef.current = null;
        appendAssistantText(assistantId, `\n\n请求出错：${error.message || "服务暂不可用"}`);
        loadTaskStatus(currentChatId).catch(() => {});
      }
    );
  };

  const processStreamEvent = (messageId, event) => {
    const data = event.data || {};
    if (event.event === "answer_delta") {
      appendAssistantText(messageId, data.content || "");
      return;
    }
    if (event.event === "task_status") {
      updateAssistant(messageId, (message) => ({
        ...message,
        timeline: mergeThinking(message.timeline, data.message || data.stage || "正在处理")
      }));
      return;
    }
    if (event.event === "reference_delta") {
      updateAssistant(messageId, (message) => ({
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
      updateAssistant(messageId, (message) => ({
        ...message,
        artifacts: [...(message.artifacts || []), {
          title: data.title || "可编辑产物",
          type: data.artifactType || "ARTIFACT",
          content: data.content || "",
          downloadUrl: data.downloadUrl || data.content || ""
        }]
      }));
      return;
    }
    if (event.event === "recommend_delta") {
      const items = normalizeRecommendItems(data.items ?? data.content ?? data);
      if (!items.length) return;
      updateAssistant(messageId, (message) => ({
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
      updateAssistant(messageId, (message) => ({
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
      updateAssistant(messageId, (message) => ({
        ...message,
        timeline: [...(message.timeline || []), { type: "error", message: data.message || "处理失败" }]
      }));
      appendAssistantText(messageId, `\n\n${data.message || "处理失败"}`);
    }
  };

  const updateAssistant = (messageId, updater) => {
    updateCurrentChat((chat) => ({
      ...chat,
      messages: chat.messages.map((message) => message.id === messageId ? updater(message) : message)
    }));
  };

  const appendAssistantText = (messageId, text) => {
    updateAssistant(messageId, (message) => ({ ...message, content: `${message.content}${text}` }));
  };

  const closeAssistantTimeline = (messageId) => {
    updateAssistant(messageId, (message) => {
      const hasError = (message.timeline || []).some((item) => item.type === "error");
      return { ...message, showTimeline: hasError };
    });
  };

  const mergeThinking = (timeline = [], content) => {
    const next = [...timeline];
    const last = next[next.length - 1];
    if (last?.type === "thinking") {
      last.content = content;
      return next;
    }
    return [...next, { type: "thinking", content }];
  };

  const stopMessage = async () => {
    streamControllerRef.current?.abort();
    streamControllerRef.current = null;
    await stopAcademicStream(currentChatId);
    updateCurrentChat((chat) => ({ ...chat, stopped: true }));
    setTaskStatusByChat((prev) => ({
      ...prev,
      [currentChatId]: { ...(prev[currentChatId] || {}), running: false, stopped: true, resumable: true }
    }));
    setIsSending(false);
  };

  const resumeMessage = () => {
    if (isSending || !auth?.token) return;
    const assistantId = `A${Date.now()}`;
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
    updateCurrentChat((chat) => ({
      ...chat,
      stopped: false,
      messages: [...chat.messages, assistantMsg]
    }));
    setTaskStatusByChat((prev) => ({ ...prev, [currentChatId]: { ...(prev[currentChatId] || {}), stopped: false, running: true } }));
    setConnectionError("");
    setIsSending(true);
    streamControllerRef.current = requestAcademicResumeStream(
      currentChatId,
      (event) => processStreamEvent(assistantId, event),
      () => {
        setIsSending(false);
        streamControllerRef.current = null;
        closeAssistantTimeline(assistantId);
        loadQuota().catch(() => {});
        loadSessions().catch(() => {});
        loadTaskStatus(currentChatId).catch(() => {});
      },
      (error) => {
        setIsSending(false);
        streamControllerRef.current = null;
        appendAssistantText(assistantId, `\n\n继续生成失败：${error.message || "服务暂不可用"}`);
        loadTaskStatus(currentChatId).catch(() => {});
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

  const handleSaveAdminAuth = () => {
    saveAdminAuth(adminForm.username, adminForm.password);
    setToast("模拟支付授权已保存");
  };

  const buyPackage = async (pkg, buyType, options = {}) => {
    if (!auth?.token) {
      setLoginOpen(true);
      return;
    }
    const key = `${pkg.goodsId}-${buyType}`;
    setBuyingKey(key);
    setConnectionError("");
    try {
      const product = { ...pkg, teamId: options.teamId || "" };
      if (buyType === "group" && !product.activityId) {
        throw new Error("当前额度包暂无可用拼团活动");
      }
      const userId = auth.userId || quota?.userId;
      const orderRes = buyType === "group" ? await lockGroupBuyOrder(product, userId) : await createDirectOrder(product, userId);
      if (orderRes.code !== "0000") throw new Error(orderRes.info || "订单创建失败");
      try {
        const payRes = await mockPaySuccess(orderRes.data.orderId);
        if (payRes.code === "0000") {
          const orderStatus = payRes.data?.orderStatus;
          const groupSettled = orderStatus === "GROUP_SETTLED" || orderStatus === "DEAL_DONE";
          if (buyType === "group" && !groupSettled) {
            setToast("拼团订单已支付，请在订单列表查看成团进度");
          } else {
            setToast(`${product.quotaAmount || 0} 点额度已到账`);
          }
          await refreshRecharge();
        } else {
          throw new Error(payRes.info || "模拟支付失败");
        }
      } catch {
        setToast(`订单已创建：${orderRes.data.orderId}，模拟支付需要运营授权`);
        await loadOrders().catch(() => {});
      }
    } catch (error) {
      setConnectionError(error.message || "购买失败");
    } finally {
      setBuyingKey("");
    }
  };

  const payExistingOrder = async (order) => {
    if (!order?.orderId) return;
    setBuyingKey(`order-${order.orderId}`);
    setConnectionError("");
    try {
      const payRes = await mockPaySuccess(order.orderId);
      if (payRes.code !== "0000") throw new Error(payRes.info || "模拟支付失败");
      const groupSettled = payRes.data?.orderStatus === "GROUP_SETTLED" || payRes.data?.orderStatus === "DEAL_DONE";
      setToast(order.marketType === 1 && !groupSettled ? "订单已支付，等待成团" : "订单已支付，额度已更新");
      await refreshRecharge();
    } catch (error) {
      setConnectionError(error.message || "模拟支付失败");
    } finally {
      setBuyingKey("");
    }
  };

  return (
    <div className="bear-doctor-app">
      <div className="glow-effect glow-effect-1" />
      <div className="glow-effect glow-effect-2" />
      <div className="container">
        <aside className="sidebar">
          <div className="sidebar-header">
            <div className="app-title">
              <span className="logo-icon">🌱</span>
              <span className="title-text">熊博士 Agent</span>
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
                <h2>你好，我是熊博士 Agent</h2>
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
          groupPreviewPackage={groupPreviewPackage}
          adminForm={adminForm}
          setAdminForm={setAdminForm}
          onBuy={buyPackage}
          onOpenGroupPreview={openGroupPreview}
          onBackToPackages={() => setGroupPreviewPackage(null)}
          onSaveAdminAuth={handleSaveAdminAuth}
          onRefresh={refreshRecharge}
          onPayOrder={payExistingOrder}
          onClose={() => {
            setGroupPreviewPackage(null);
            setRechargeOpen(false);
          }}
        />
      )}
    </div>
  );
}

function MessageItem({ msg, copied, isSending, isLast, onCopy, onToggleTimeline, onToggleReference, onRecommendClick }) {
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
                      <a className="artifact-download" href={artifact.downloadUrl} target="_blank" rel="noreferrer" download>
                        <Download size={15} />
                        <span>下载 PPTX</span>
                      </a>
                    )}
                    <pre>{artifact.content}</pre>
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
        <div className="auth-logo">🌱</div>
        <h3>{mode === "login" ? "登录熊博士 Agent" : "注册账号"}</h3>
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

function RechargeDialog({
  quota,
  flows,
  orders,
  ordersLoading,
  packages,
  buyingKey,
  groupPreviewPackage,
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
  const teamId = groupPreviewPackage?.teamId || groupPreviewPackage?.activityId || `TEAM${String(groupPreviewPackage?.goodsId || Date.now()).replace(/\D/g, "").slice(-8) || "00000001"}`;
  const teamSize = Number(groupPreviewPackage?.teamSize || 2);
  const quotaAmount = Number(groupPreviewPackage?.quotaAmount || 0).toFixed(0);
  const isGroupBuying = Boolean(groupPreviewPackage && buyingKey === `${groupPreviewPackage.goodsId}-group`);
  const isDirectBuying = Boolean(groupPreviewPackage && buyingKey === `${groupPreviewPackage.goodsId}-direct`);

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
              <h4>和他们一起拼团</h4>
              <div className="group-team-card">
                <div>
                  <b>团队标识: {teamId}</b>
                  <span>还差1人成团</span>
                </div>
                <span>剩余时间: 23:56:11</span>
                <button
                  type="button"
                  onClick={() => onBuy(groupPreviewPackage, "group", { teamId })}
                  disabled={Boolean(buyingKey)}
                >
                  <UserPlus size={15} /> {isGroupBuying ? "处理中" : "加入拼团"}
                </button>
              </div>
            </div>

            <div className="group-detail-actions">
              <button type="button" onClick={() => onBuy(groupPreviewPackage, "direct")} disabled={Boolean(buyingKey)}>
                <CreditCard size={16} /> {isDirectBuying ? "处理中" : `直接购买 ¥${formatMoney(groupPreviewPackage.originPrice)}`}
              </button>
              <button className="primary" type="button" onClick={() => onBuy(groupPreviewPackage, "group")} disabled={Boolean(buyingKey)}>
                <UserPlus size={16} /> {isGroupBuying ? "处理中" : `自己开团 ¥${formatMoney(groupPreviewPackage.groupPrice)}`}
              </button>
            </div>
            <div className="group-detail-tip">{teamSize} 人成团，支付后可在订单列表查看状态。</div>
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
        <details className="order-details" open>
          <summary>我的订单</summary>
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
                  <em>{statusLabel(order.status)}</em>
                  <span>{order.orderTime ? String(order.orderTime).replace("T", " ") : ""}</span>
                </div>
                {canPay && (
                  <button type="button" onClick={() => onPayOrder(order)} disabled={Boolean(buyingKey)}>
                    {buyingKey === `order-${order.orderId}` ? "处理中" : "支付"}
                  </button>
                )}
              </div>
            );
          })}
        </details>
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
