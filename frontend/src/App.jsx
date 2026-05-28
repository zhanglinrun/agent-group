import { useEffect, useRef, useState } from "react";
import ChatBubble from "./components/ChatBubble";
import InputArea from "./components/InputArea";
import Sidebar from "./components/Sidebar";
import AdminDashboard from "./components/AdminDashboard";
import AgentTracePanel from "./components/AgentTracePanel";
import { CallbackTestPage, CartPage, CheckoutPage, LoginPage, OrderListPage, ProductDetailPage, ProductListPage } from "./components/MallPages";
import { createDirectOrder, getSessionId, lockGroupBuyOrder, mockPaySuccess, requestGuideStream, stopGuideStream } from "./services/api";

function App() {
  const path = window.location.pathname.replace(/\/+$/, "") || "/";

  if (path === "/admin" || path === "/admin.html") {
    return <AdminDashboard />;
  }
  if (path === "/login" || path === "/login.html") {
    return <LoginPage />;
  }
  if (path === "/mall" || path === "/products" || path === "/mall.html") {
    return <ProductListPage />;
  }
  if (path.startsWith("/products/")) {
    return <ProductDetailPage />;
  }
  if (path === "/cart" || path === "/cart.html") {
    return <CartPage />;
  }
  if (path === "/order-list" || path === "/orders" || path === "/order-list.html") {
    return <OrderListPage />;
  }
  if (path === "/checkout" || path === "/checkout.html") {
    return <CheckoutPage />;
  }
  if (path === "/callback-test" || path === "/test-callback" || path === "/callback-test.html") {
    return <CallbackTestPage />;
  }

  return <ChatApp />;
}

function nowLabel() {
  return new Date().toLocaleTimeString("zh-CN", { hour12: false, hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function describeTrace(event) {
  const data = event.data || {};
  switch (event.event) {
    case "tool_plan":
      return {
        stage: "计划",
        text: `${data.intent ? `意图：${data.intent}；` : ""}准备调用：${data.tools?.map(tool => tool.name).join("、") || "无工具"}`
      };
    case "retrieval_progress":
      return {
        stage: "检索",
        text: data.message || data.stage || "检索进度更新"
      };
    case "tool_call":
      return {
        stage: "工具",
        text: `${data.toolName || "工具"}：${data.message || data.status || "执行完成"}`
      };
    case "reference_delta":
      return {
        stage: "引用",
        text: `${data.documentType || "知识片段"}：${data.content || ""}`.slice(0, 180)
      };
    case "self_check":
      return {
        stage: "自检",
        text: data.message || data.summary || "已完成回答自检"
      };
    case "usage_metric":
      return {
        stage: "指标",
        text: `耗时 ${data.totalLatencyMillis ?? data.elapsedMillis ?? data.costMillis ?? "-"} ms`
      };
    case "error":
      return {
        stage: "异常",
        text: data.message || data.info || "导购流返回异常"
      };
    default:
      return null;
  }
}

function ChatApp() {
  const initialSessionId = getSessionId();
  const [sessions, setSessions] = useState([
    { id: initialSessionId, title: "新导购会话" }
  ]);
  const [currentSessionId, setCurrentSessionId] = useState(initialSessionId);
  const [messages, setMessages] = useState([
    {
      id: "welcome",
      role: "assistant",
      text: "你好！我是你的 AI 导购助手。支持上传图片或直接描述你的需求，我会为你检索并推荐商品。",
      products: [],
      references: []
    }
  ]);
  const [traces, setTraces] = useState([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const chatAreaRef = useRef(null);
  const streamControllerRef = useRef(null);

  const scrollToBottom = () => {
    if (chatAreaRef.current) {
      chatAreaRef.current.scrollTop = chatAreaRef.current.scrollHeight;
    }
  };

  const appendTrace = (stage, text) => {
    if (!stage || !text) return;
    setTraces(prev => [...prev.slice(-80), { id: `${Date.now()}-${prev.length}`, stage, text, time: nowLabel() }]);
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleNewSession = () => {
    const id = `S${Date.now()}`;
    localStorage.setItem("agentGroupSessionId", id);
    setSessions(prev => [{ id, title: "新导购会话" }, ...prev]);
    setCurrentSessionId(id);
    setTraces([]);
    setMessages([
      {
        id: "welcome",
        role: "assistant",
        text: "你好！开启了新的导购会话，请问有什么可以帮您的？",
        products: [],
        references: []
      }
    ]);
  };

  const handleSelectSession = (id) => {
    localStorage.setItem("agentGroupSessionId", id);
    setCurrentSessionId(id);
  };

  const handleSend = (text, imageUrl, imageName) => {
    if (isStreaming) return;
    if (messages.length === 1) {
      setSessions(prev => prev.map(session => (
        session.id === currentSessionId
          ? { ...session, title: text ? text.slice(0, 15) : "图片检索" }
          : session
      )));
    }

    const userMsgContent = imageUrl ? `[上传了图片 ${imageName}]\n${text}` : text;
    const userMsg = { id: Date.now().toString(), role: "user", text: userMsgContent, products: [] };
    const aiMsgId = (Date.now() + 1).toString();
    const initialAiMsg = { id: aiMsgId, role: "assistant", text: "", products: [], references: [], isTyping: true };

    setMessages(prev => [...prev, userMsg, initialAiMsg]);
    setTraces([]);
    setIsStreaming(true);
    appendTrace("会话", imageUrl ? "已接收图文导购请求" : "已接收导购请求");

    streamControllerRef.current = requestGuideStream(
      text,
      imageUrl,
      imageName,
      (event) => {
        const trace = describeTrace(event);
        if (trace) {
          appendTrace(trace.stage, trace.text);
        }

        setMessages(prev => prev.map(msg => {
          if (msg.id !== aiMsgId) return msg;
          const updated = { ...msg, isTyping: true };

          if (event.event === "answer_delta") {
            updated.text += event.data?.content || "";
          } else if (event.event === "product_card") {
            const productData = event.data || {};
            const product = {
              id: productData.goodsId || `G${Date.now()}`,
              name: productData.goodsName || "推荐商品",
              originPrice: productData.originPrice,
              groupPrice: productData.groupPrice,
              teamSize: productData.teamSize || 2,
              decisionId: productData.decisionId,
              activityId: productData.activityId,
              imageUrl: productData.imageUrl,
              stockProgress: productData.progressText || "已抢85%",
              soldCount: productData.soldCount || (productData.teamSize || 2) * 153
            };
            updated.products = [...(updated.products || []), product];
          } else if (event.event === "reference_delta") {
            const ref = {
              title: event.data?.documentType || "相关知识",
              text: event.data?.content || ""
            };
            updated.references = [...(updated.references || []), ref];
          } else if (event.event === "order_delta") {
            updated.text += `\n[订单状态] ${event.data?.message || event.data?.displayStatus || event.data?.status || "已更新"}\n`;
          } else if (event.event === "error") {
            updated.text += `\n[异常] ${event.data?.message || event.data?.info || "导购处理失败"}\n`;
          }
          return updated;
        }));
      },
      () => {
        streamControllerRef.current = null;
        setMessages(prev => prev.map(msg => msg.id === aiMsgId ? { ...msg, isTyping: false } : msg));
        setIsStreaming(false);
      },
      (error) => {
        streamControllerRef.current = null;
        appendTrace("异常", error.message || "导购流请求失败");
        setMessages(prev => prev.map(msg => (
          msg.id === aiMsgId
            ? { ...msg, isTyping: false, text: `${msg.text}\n[异常] ${error.message || "服务暂不可用，请稍后重试"}` }
            : msg
        )));
        setIsStreaming(false);
      },
      currentSessionId
    );
  };

  const handleStop = async () => {
    streamControllerRef.current?.abort();
    streamControllerRef.current = null;
    await stopGuideStream(currentSessionId);
    setIsStreaming(false);
    appendTrace("会话", "已停止本次流式生成");
    setMessages(prev => {
      const last = prev[prev.length - 1];
      if (last.role === "assistant" && last.isTyping) {
        return prev.map(msg => msg.id === last.id ? { ...msg, isTyping: false, text: `${msg.text}\n\n_[已停止生成]_` } : msg);
      }
      return prev;
    });
  };

  const handleDirectBuy = async (product) => {
    try {
      if (!product.decisionId) {
        alert("缺少决策凭证，请重新提问");
        return;
      }
      const res = await createDirectOrder(product);
      if (res.code === "0000") {
        const orderId = res.data.orderId;
        alert(`直接购买下单成功！订单号：${orderId}\n即将模拟支付...`);
        const payRes = await mockPaySuccess(orderId);
        if (payRes.code === "0000") {
          alert(`支付成功！当前状态：${payRes.data.status}`);
        } else {
          alert(`模拟支付失败：${payRes.info}`);
        }
      } else {
        alert(`直接购买下单失败：${res.info}`);
      }
    } catch (error) {
      alert(`调用直接购买失败：${error.message || "请求失败"}`);
    }
  };

  const handleGroupBuy = async (product) => {
    try {
      if (!product.decisionId) {
        alert("缺少决策凭证，请重新提问");
        return;
      }
      const res = await lockGroupBuyOrder(product);
      if (res.code === "0000") {
        const orderId = res.data.orderId;
        alert(`发起拼单成功！拼团订单号：${orderId}\n即将模拟支付...`);
        const payRes = await mockPaySuccess(orderId);
        if (payRes.code === "0000") {
          alert(`拼团支付成功！当前状态：${payRes.data.status}`);
        } else {
          alert(`模拟支付失败：${payRes.info}`);
        }
      } else {
        alert(`发起拼单失败：${res.info}`);
      }
    } catch (error) {
      alert(`调用拼团购买失败：${error.message || "请求失败"}`);
    }
  };

  return (
    <div className="app-container">
      <Sidebar
        sessions={sessions}
        currentSessionId={currentSessionId}
        onNewSession={handleNewSession}
        onSelectSession={handleSelectSession}
      />

      <main className="main-chat">
        <header className="chat-header">
          <span>智能导购</span>
          <span className={isStreaming ? "streaming-status active" : "streaming-status"}>
            {isStreaming ? "正在生成响应..." : ""}
          </span>
        </header>

        <div className="chat-area" ref={chatAreaRef}>
          {messages.map(msg => (
            <ChatBubble
              key={msg.id}
              message={msg}
              onDirectBuy={handleDirectBuy}
              onGroupBuy={handleGroupBuy}
            />
          ))}
        </div>

        <InputArea
          onSend={handleSend}
          onStop={handleStop}
          isStreaming={isStreaming}
        />
      </main>

      <AgentTracePanel traces={traces} />
    </div>
  );
}

export default App;
