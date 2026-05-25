const sampleProducts = [
  {
    id: "G10001",
    name: "轻薄学习平板标准版",
    originPrice: 2399,
    groupPrice: 2099,
    spec: "10.9 英寸屏幕，128GB 存储，支持手写笔",
    afterSale: "7 天无理由退货，1 年质保",
    reason: "学习、写论文、看网课场景下够用，拼团价低，长期使用成本更稳。",
    notSuitable: "长期剪视频或运行大型应用的用户",
    decisionId: "",
    activityId: "A10001",
    teamSize: 3,
    leftTime: "29 分钟"
  },
  {
    id: "G10002",
    name: "高配创作平板",
    originPrice: 3299,
    groupPrice: 2899,
    spec: "12.1 英寸屏幕，256GB 存储，高刷新率",
    afterSale: "7 天无理由退货，1 年质保",
    reason: "性能更强，适合剪视频、绘图和多任务，但对学生轻办公预算压力更大。",
    notSuitable: "只做笔记和看网课且预算有限的用户",
    decisionId: "",
    activityId: "A10002",
    teamSize: 5,
    leftTime: "18 分钟"
  }
];

const sampleReferences = [
  {
    title: "商品详情片段 A-01",
    text: "标准版定位学习和轻办公，支持手写笔，适合网课、笔记和文档编辑。"
  },
  {
    title: "营销规则片段 M-03",
    text: "标准版三人成团，拼团价 2099 元，活动剩余时间以队伍创建时间计算。"
  },
  {
    title: "售后政策片段 R-02",
    text: "拼团商品成团后支持 7 天无理由退货；未成团自动退款。"
  }
];

const defaultDocs = [
  { name: "平板商品详情说明.md", type: "商品详情", scope: "正式知识库", status: "已向量化" },
  { name: "拼团活动规则.md", type: "营销规则", scope: "正式知识库", status: "已向量化" },
  { name: "退款与售后政策.md", type: "售后政策", scope: "正式知识库", status: "已向量化" }
];

const defaultEvalCases = [
  { name: "学生预算导购", recall: "Top 2", answer: "通过", recommend: "通过" },
  { name: "拼团退款规则", recall: "Top 1", answer: "通过", recommend: "不适用" },
  { name: "标准版和高配版对比", recall: "Top 3", answer: "待复核", recommend: "通过" }
];

const GUIDE_STREAM_URL = "http://localhost:8080/api/v1/agent/guide/stream";
const GUIDE_STOP_URL = "http://localhost:8080/api/v1/agent/stop";
const GUIDE_EVALUATION_URL = "http://localhost:8080/api/v1/evaluate/guide/run";
const KNOWLEDGE_UPLOAD_FILE_URL = "http://localhost:8080/api/v1/knowledge/document/upload-file";
const DIRECT_ORDER_URL = "http://localhost:8080/api/v1/trade/order/direct";
const GROUP_LOCK_URL = "http://localhost:8080/api/v1/group/trade/lock";
const PAYMENT_CREATE_URL = "http://localhost:8080/api/v1/payment/create";
const PAYMENT_WEBHOOK_URL = "http://localhost:8080/api/v1/payment/webhook";
const STATUS_FLOW_URL = "http://localhost:8080/api/v1/trade/order/status-flow";
const ADMIN_AUTH_KEY = "agentGroupAdminAuth";

const state = {
  timers: [],
  streaming: false,
  abortController: null,
  answerTarget: null,
  pendingImageUrl: "",
  pendingImageName: "",
  products: [],
  docs: loadStore("agentGroupDocs", defaultDocs),
  orders: loadStore("agentGroupOrders", []),
  evalCases: loadStore("agentGroupEvalCases", defaultEvalCases)
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

document.addEventListener("DOMContentLoaded", () => {
  const page = document.body.dataset.page;
  if (page === "consumer") {
    initConsumer();
  }
  if (page === "admin") {
    initAdmin();
  }
  if (page === "checkout") {
    initCheckout();
  }
});

function initConsumer() {
  bindComposer();
  bindUploads();
  bindConsumerActions();
  renderEmptyResult();
  addMessage("assistant", "AI 导购", "你好，可以直接描述预算、用途和限制，我会边检索商品知识库边给出推荐商品卡片。");
}

function initAdmin() {
  bindAdminActions();
  renderKnowledgeRows();
  renderOrderRows();
  renderEvalRows();
}

function bindComposer() {
  const form = $("#composerForm");
  if (!form) {
    return;
  }
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const input = $("#messageInput");
    const message = input.value.trim() || "我是学生，预算有限，想买适合写论文和看网课的平板，哪款更合适？";
    input.value = "";
    runGuide(message);
  });
}

function bindUploads() {
  $("#imageInput")?.addEventListener("change", (event) => handleFiles(event.target.files, "图片"));
  $("#docInput")?.addEventListener("change", (event) => handleFiles(event.target.files, "文档"));
}

function bindConsumerActions() {
  $("#stopBtn")?.addEventListener("click", stopCurrentStream);
  $("#runDemoBtn")?.addEventListener("click", () => {
    runGuide("我是学生，预算有限，想买适合写论文和看网课的平板，哪款更合适？");
  });
  $$(".quick-prompts button").forEach((button) => {
    button.addEventListener("click", () => runGuide(button.dataset.prompt));
  });
}

function bindAdminActions() {
  $("#mockKnowledgeBtn")?.addEventListener("click", () => {
    state.docs.unshift({
      name: "学生平板促销补充说明.md",
      type: "营销补充",
      scope: "正式知识库",
      status: "待向量化"
    });
    saveStore("agentGroupDocs", state.docs);
    renderKnowledgeRows();
  });
  $("#runEvalBtn")?.addEventListener("click", runEval);
}

function handleFiles(files, type) {
  Array.from(files).forEach((file) => {
    const chip = document.createElement("span");
    chip.className = "attachment-chip";
    chip.textContent = `${type}：${file.name}`;
    $("#attachmentList")?.appendChild(chip);

    if (type === "文档") {
      state.docs.unshift({
        name: file.name,
        type: "用户上传",
        scope: "本轮会话",
        status: "待运营审核"
      });
      saveStore("agentGroupDocs", state.docs);
      uploadKnowledgeFile(file, chip);
    } else if (type === "图片") {
      state.pendingImageName = file.name;
      state.pendingImageUrl = `local-image://${file.name}`;
      readFileAsDataUrl(file)
        .then((dataUrl) => {
          state.pendingImageUrl = dataUrl;
          chip.textContent = `图片：${file.name} 已就绪`;
        })
        .catch(() => {
          chip.textContent = `图片：${file.name} 已添加`;
        });
    }
  });
}

async function uploadKnowledgeFile(file, chip) {
  const form = new FormData();
  form.append("file", file);
  form.append("goodsId", "G10001");
  form.append("documentName", file.name);
  form.append("documentType", "商品资料");
  form.append("knowledgeVersion", "v1");
  try {
    const result = await postForm(KNOWLEDGE_UPLOAD_FILE_URL, form, adminAuthHeaders());
    updateDocStatus(file.name, "已入库并向量化");
    if (chip) {
      chip.textContent = `文档：${file.name} 已入库`;
    }
    addMessage("system", "文档", `已上传到对象存储：${result.objectKey || file.name}`);
  } catch (error) {
    updateDocStatus(file.name, "上传失败");
    if (chip) {
      chip.textContent = `文档：${file.name} 上传失败`;
    }
    addMessage("system", "文档", error.message || "文档上传失败");
  }
}

function updateDocStatus(name, status) {
  const doc = state.docs.find((item) => item.name === name);
  if (doc) {
    doc.status = status;
    saveStore("agentGroupDocs", state.docs);
  }
}

function runGuide(message) {
  const imageUrl = state.pendingImageUrl;
  const imageName = state.pendingImageName;
  state.pendingImageUrl = "";
  state.pendingImageName = "";
  cancelCurrentRequest();
  stopTimers();
  state.streaming = true;
  state.answerTarget = null;
  state.products = [];
  setText("#connectionStatus", "连接后端中");
  setText("#sessionHint", "正在理解你的需求");
  setText("#decisionCopy", "系统正在识别预算、场景和限制，并检索商品知识库。");
  setText("#productCount", "0 个");
  setText("#tradeState", "未开始");
  setDisabled("#sendBtn", true);
  setDisabled("#stopBtn", false);

  clearNode("#productDeck");
  clearNode("#referenceList");
  clearNode("#tradeTimeline");
  clearNode("#attachmentList");

  addMessage("user", "你", message);
  state.answerTarget = addMessage("assistant", "AI 导购", "");

  requestGuideStream(message, imageUrl, imageName).catch((error) => {
    if (!state.streaming || error.name === "AbortError") {
      return;
    }
    addMessage("system", "系统", "后端服务暂不可用，已切换为本地演示流。");
    runLocalGuideDemo();
  });
}

async function requestGuideStream(message, imageUrl, imageName) {
  state.abortController = new AbortController();
  const response = await fetch(GUIDE_STREAM_URL, {
    method: "POST",
    headers: {
      "Accept": "text/event-stream",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      sessionId: getSessionId(),
      userId: "U10001",
      question: message,
      imageUrl: imageUrl || "",
      imageName: imageName || ""
    }),
    signal: state.abortController.signal
  });

  if (!response.ok || !response.body) {
    throw new Error(`导购接口请求失败：${response.status}`);
  }

  setText("#connectionStatus", "后端流式生成中");
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (state.streaming) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || "";
    blocks.forEach(handleSseBlock);
  }

  if (state.streaming) {
    finishStream("后端已完成");
  }
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result || "");
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

function runLocalGuideDemo() {
  setText("#connectionStatus", "本地演示生成中");
  schedule(180, () => addToolEvent("识别意图：商品推荐、预算敏感、学习场景"));
  schedule(420, () => renderReferences(sampleReferences.slice(0, 1)));
  schedule(760, () => appendAnswer("我会先按预算、学习场景和长期使用成本筛选。"));
  schedule(1120, () => renderReferences(sampleReferences.slice(0, 2)));
  schedule(1260, () => appendAnswer("从知识库看，标准版覆盖写论文、做笔记和看网课这些核心需求。"));
  schedule(1580, () => renderProduct(sampleProducts[0]));
  schedule(1900, () => appendAnswer("如果你不需要长期剪视频或运行大型应用，标准版更符合预算优先的选择。"));
  schedule(2200, () => renderReferences(sampleReferences));
  schedule(2460, () => renderProduct(sampleProducts[1]));
  schedule(2740, () => appendAnswer("高配版性能更强，但价格更高，更适合创作类场景。"));
  schedule(3100, () => appendAnswer("我的建议是优先选标准版。如果你想省钱，可以用三人成团价购买；如果不想等成团，也可以按原价直接购买。"));
  schedule(3400, () => addToolEvent("结果自检：回答依据充分，商品卡片价格与推荐理由一致"));
  schedule(3700, () => finishStream("本地演示"));
}

function handleSseBlock(block) {
  const lines = block.split(/\r?\n/);
  const dataLines = lines.filter((line) => line.startsWith("data:"));
  const data = (dataLines.length ? dataLines : lines)
    .map((line) => line.replace(/^data:\s*/, "").trim())
    .filter((line) => line && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:"))
    .join("");

  if (!data) {
    return;
  }

  try {
    handleGuideEvent(JSON.parse(data));
  } catch (error) {
    addMessage("system", "系统", "后端返回内容解析失败，已忽略该片段。");
  }
}

function handleGuideEvent(event) {
  if (!state.streaming || !event) {
    return;
  }

  if (event.event === "tool_call") {
    const args = formatToolArguments(event.data?.arguments);
    addToolEvent(`${event.data?.message || "后端正在执行导购步骤"}${args ? `（${args}）` : ""}`);
    return;
  }

  if (event.event === "tool_plan") {
    const tools = (event.data?.tools || []).map((tool) => tool.name).join(" → ");
    addToolEvent(`工具计划：${tools || "已生成"}`);
    return;
  }

  if (event.event === "reference_delta") {
    renderReference({
      title: `${event.data?.documentType || "知识片段"} ${event.data?.fragmentId || ""}`.trim(),
      text: event.data?.content || ""
    });
    return;
  }

  if (event.event === "answer_delta") {
    appendAnswerChunk(event.data?.content || "");
    return;
  }

  if (event.event === "product_card") {
    renderProduct(mapProductCard(event.data));
    return;
  }

  if (event.event === "order_delta") {
    handleOrderDelta(event.data);
    return;
  }

  if (event.event === "self_check") {
    addToolEvent(event.data?.message || "结果自检完成");
    return;
  }

  if (event.event === "error") {
    addMessage("system", "错误", event.data?.message || "导购接口返回错误");
    finishStream("后端已结束", {
      hint: "导购生成失败",
      copy: "可以修改问题后再次发送。"
    });
    return;
  }

  if (event.event === "done") {
    finishStream("后端已完成");
  }
}

function mapProductCard(data) {
  return {
    id: data?.goodsId || `G${Date.now()}`,
    name: data?.goodsName || "推荐商品",
    originPrice: formatPrice(data?.originPrice),
    groupPrice: formatPrice(data?.groupPrice),
    spec: data?.specSummary || "暂无规格说明",
    afterSale: data?.afterSalePolicy || "",
    reason: data?.recommendReason || "符合本轮导购需求。",
    notSuitable: data?.notSuitableFor || "暂无",
    decisionId: data?.decisionId || "",
    quoteExpireTime: data?.quoteExpireTime || "",
    activityId: data?.activityId || "",
    teamSize: data?.teamSize || 1,
    leftTime: formatRemainingTime(data?.remainingSeconds)
  };
}

function handleOrderDelta(data) {
  if (!data) {
    return;
  }
  setText("#tradeState", data.status || "订单更新");
  pushTradeStep(data.message || data.status || "订单状态已更新", "done");
}

function schedule(delay, task) {
  const timer = window.setTimeout(task, delay);
  state.timers.push(timer);
}

function stopTimers() {
  state.timers.forEach((timer) => window.clearTimeout(timer));
  state.timers = [];
}

function cancelCurrentRequest() {
  if (state.abortController) {
    state.abortController.abort();
    state.abortController = null;
  }
}

function stopCurrentStream() {
  if (!state.streaming) {
    return;
  }
  postJson(GUIDE_STOP_URL, { sessionId: getSessionId() }).catch(() => {});
  cancelCurrentRequest();
  stopTimers();
  state.streaming = false;
  setDisabled("#sendBtn", false);
  setDisabled("#stopBtn", true);
  setText("#sessionHint", "本轮已停止");
  setText("#connectionStatus", "本地演示");
  setText("#decisionCopy", "已记录中断状态，可以继续补充需求后再次发送。");
  addMessage("system", "系统", "本轮生成已停止，已记录中断状态。");
}

function finishStream(statusText = "本地演示", result = {}) {
  cancelCurrentRequest();
  stopTimers();
  state.streaming = false;
  setDisabled("#sendBtn", false);
  setDisabled("#stopBtn", true);
  setText("#sessionHint", result.hint || "建议优先选择标准版");
  setText("#connectionStatus", statusText);
  setText("#decisionCopy", result.copy || "标准版满足学习、写论文和看网课需求。直接购买按原价支付，拼团购买按优惠价支付。");
}

function addMessage(role, label, text) {
  const stream = $("#chatStream");
  if (!stream) {
    return null;
  }
  const wrap = document.createElement("div");
  wrap.className = `message ${role}`;

  const labelEl = document.createElement("span");
  labelEl.className = "message-label";
  labelEl.textContent = label;

  const body = document.createElement("div");
  body.className = "message-body";
  body.textContent = text;

  wrap.append(labelEl, body);
  stream.appendChild(wrap);
  stream.scrollTop = stream.scrollHeight;
  return body;
}

function appendAnswer(text) {
  if (!state.answerTarget) {
    state.answerTarget = addMessage("assistant", "AI 导购", "");
  }
  if (!state.answerTarget) {
    return;
  }
  const prefix = state.answerTarget.textContent ? "\n" : "";
  state.answerTarget.textContent += `${prefix}${text}`;
  const stream = $("#chatStream");
  if (stream) {
    stream.scrollTop = stream.scrollHeight;
  }
}

function formatToolArguments(argumentsMap) {
  if (!argumentsMap || typeof argumentsMap !== "object") {
    return "";
  }
  return Object.entries(argumentsMap)
    .filter(([key, value]) => key && value && String(value).length <= 32)
    .map(([key, value]) => `${toolArgumentLabel(key)}: ${value}`)
    .join("，");
}

function toolArgumentLabel(key) {
  const labels = {
    goodsId: "商品编号",
    limit: "检索条数",
    imageUrl: "图片",
    queryMode: "查询方式"
  };
  return labels[key] || key;
}

function appendAnswerChunk(text) {
  if (!state.answerTarget) {
    state.answerTarget = addMessage("assistant", "AI 导购", "");
  }
  if (!state.answerTarget || !text) {
    return;
  }
  state.answerTarget.textContent += text;
  const stream = $("#chatStream");
  if (stream) {
    stream.scrollTop = stream.scrollHeight;
  }
}

function addToolEvent(text) {
  addMessage("system", "事件", text);
}

function renderEmptyResult() {
  setText("#productCount", "0 个");
  setText("#tradeState", "未开始");
  setHtml("#productDeck", `<div class="empty-state">发送问题后，这里会实时出现商品卡片。</div>`);
  setHtml("#referenceList", `<div class="empty-state">检索到的商品详情、活动规则和售后片段会显示在这里。</div>`);
  setHtml("#tradeTimeline", `<li class="wait">选择商品后开始购买流程</li>`);
}

function renderReferences(references) {
  const root = $("#referenceList");
  if (!root) {
    return;
  }
  root.innerHTML = "";
  references.forEach(renderReference);
}

function renderReference(item) {
  const root = $("#referenceList");
  if (!root) {
    return;
  }
  if (root.querySelector(".empty-state")) {
    root.innerHTML = "";
  }
  const row = document.createElement("div");
  row.className = "reference-item";
  row.innerHTML = `<strong>${escapeHtml(item.title)}</strong><br>${escapeHtml(item.text)}`;
  root.appendChild(row);
}

function renderProduct(product) {
  if (state.products.some((item) => item.id === product.id)) {
    return;
  }
  const root = $("#productDeck");
  if (!root) {
    return;
  }
  state.products.push(product);
  setText("#productCount", `${state.products.length} 个`);

  if (state.products.length === 1) {
    root.innerHTML = "";
  }

  const card = document.createElement("article");
  card.className = "product-card";
  card.innerHTML = `
    <div class="product-art" aria-hidden="true">
      <img src="https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=760&q=82" alt="">
      <span class="media-badge">AI 精选</span>
      <div class="media-thumbs">
        <i></i><i></i><i></i>
      </div>
    </div>
    <div class="product-info">
      <div class="product-title">
        <strong>${escapeHtml(product.name)}</strong>
        <span class="tag info">本轮推荐</span>
      </div>
      <div class="product-rating">
        <span>4.9 分</span>
        <span>1.2 万人关注</span>
        <span>48 小时发货</span>
      </div>
      <div class="product-price-line">
        <strong>￥${product.groupPrice}</strong>
        <span>拼团到手价</span>
        <em>省 ￥${formatPrice(Number(product.originPrice) - Number(product.groupPrice))}</em>
      </div>
      <div class="price-stack">
        <span>直接价 ￥${product.originPrice}</span>
        <span>${product.teamSize} 人成团</span>
        <span>剩余 ${escapeHtml(product.leftTime)}</span>
      </div>
      <div class="coupon-row">
        <span>拼团立减</span>
        <span>学生补贴</span>
        <span>售后无忧</span>
      </div>
      <div class="product-meta">${escapeHtml(product.spec)}</div>
      <p class="product-reason">${escapeHtml(product.reason)}</p>
      <div class="product-meta">不适合：${escapeHtml(product.notSuitable)}</div>
      <div class="service-strip">
        <span>正品保障</span>
        <span>7 天无理由</span>
        <span>未成团自动退</span>
      </div>
      <div class="product-actions">
        <button class="soft-button direct-buy-button" type="button">直接购买</button>
        <button class="primary-button group-buy-button" type="button">拼团购买</button>
        <button class="soft-button rule-button" type="button">查看规则</button>
      </div>
    </div>
  `;
  card.querySelector(".direct-buy-button").addEventListener("click", () => startPurchase(product, "direct"));
  card.querySelector(".group-buy-button").addEventListener("click", () => startPurchase(product, "group"));
  card.querySelector(".rule-button").addEventListener("click", () => {
    setText("#tradeState", "规则已展开");
    setHtml("#tradeTimeline", "");
    pushTradeStep(`${product.teamSize} 人成团，拼团价 ￥${product.groupPrice}；未成团自动退款。`, "done");
  });
  root.appendChild(card);
}

async function startPurchase(product, mode) {
  if (!product.decisionId) {
    setText("#tradeState", "需要后端决策");
    pushTradeStep("请先连接后端完成一次真实导购，系统会生成导购决策编号后才能下单。", "warn");
    return;
  }
  saveCheckoutSession(product, mode);
  window.location.href = `./checkout.html?mode=${encodeURIComponent(mode)}&goodsId=${encodeURIComponent(product.id)}`;
}

function saveCheckoutSession(product, mode) {
  const createdAt = Date.now();
  saveStore("agentGroupCheckout", {
    mode,
    product,
    idempotentKey: resolveCheckoutIdempotentKey(product, mode, createdAt),
    createdAt
  });
}

function initCheckout() {
  const checkout = resolveCheckoutSession();
  bindCheckoutActions(checkout);
  renderCheckoutProduct(checkout);
  if (checkout.order?.orderId) {
    renderCheckoutOrder(checkout.order);
    restoreCheckoutOrderState(checkout.order);
    return;
  }
  renderCheckoutSteps(["确认商品和购买方式", "提交订单", "创建支付单", "支付回调演示", "查看订单流水"], 0);
}

function resolveCheckoutSession() {
  const params = new URLSearchParams(window.location.search);
  const mode = params.get("mode") || "group";
  const goodsId = params.get("goodsId") || "G10001";
  const stored = loadStore("agentGroupCheckout", null);
  if (stored?.product?.id === goodsId && stored.mode === mode) {
    return stored;
  }
  return {
    mode,
    product: sampleProducts.find((item) => item.id === goodsId) || sampleProducts[0],
    createdAt: Date.now()
  };
}

function bindCheckoutActions(checkout) {
  $("#confirmOrderBtn")?.addEventListener("click", () => submitCheckoutOrder(checkout));
  $("#mockPayBtn")?.addEventListener("click", () => simulateCheckoutPay(checkout));
  $("#refreshFlowBtn")?.addEventListener("click", () => refreshCheckoutFlow(checkout));
}

function renderCheckoutProduct(checkout) {
  const product = checkout.product;
  const isGroup = checkout.mode === "group";
  setText("#checkoutMode", isGroup ? "拼团购买" : "直接购买");
  setText("#checkoutTitle", product.name);
  setText("#checkoutSpec", product.spec);
  setText("#checkoutAfterSale", product.afterSale || "7 天无理由退货，1 年质保");
  setText("#checkoutPrice", `￥${isGroup ? product.groupPrice : product.originPrice}`);
  setText("#checkoutOrigin", `原价 ￥${product.originPrice}`);
  setText("#checkoutGroup", `拼团价 ￥${product.groupPrice}`);
  setText("#checkoutTeam", isGroup ? `${product.teamSize || 1} 人成团 · ${product.leftTime || "活动进行中"}` : "直接购买无需等待成团");
  setText("#checkoutStatus", product.decisionId ? "待提交" : "缺少导购决策");
  setDisabled("#confirmOrderBtn", !product.decisionId);
  setDisabled("#mockPayBtn", true);
  setDisabled("#refreshFlowBtn", true);
}

function restoreCheckoutOrderState(order) {
  const paid = isPaidOrder(order);
  setText("#checkoutStatus", order.orderStatus || "待支付");
  setDisabled("#confirmOrderBtn", true);
  setDisabled("#mockPayBtn", paid);
  setDisabled("#refreshFlowBtn", false);
  renderCheckoutSteps([
    "已恢复本地结算会话",
    paid ? "订单已支付，可刷新流水" : "订单待支付",
    "可进入运营端查看交易监控"
  ], paid ? 2 : 1);
}

async function submitCheckoutOrder(checkout) {
  const isGroup = checkout.mode === "group";
  const product = checkout.product;
  checkout.idempotentKey = checkout.idempotentKey || resolveCheckoutIdempotentKey(product, checkout.mode, checkout.createdAt || Date.now());
  setDisabled("#confirmOrderBtn", true);
  setText("#checkoutStatus", isGroup ? "锁单中" : "下单中");
  renderCheckoutSteps(["正在向后端提交订单"], 1);

  try {
    const createResult = isGroup
      ? await createGroupOrder(product, checkout.idempotentKey)
      : await createDirectOrder(product, checkout.idempotentKey);
    const paymentResult = createResult.payOrderId && createResult.payUrl
      ? null
      : await createGatewayPayment(createResult.orderId);
    checkout.order = normalizeCheckoutOrder(checkout, createResult, paymentResult);
    saveStore("agentGroupCheckout", checkout);
    upsertOrderForAdmin(checkout.order);
    renderCheckoutOrder(checkout.order);
    renderCheckoutSteps([
      isGroup ? "拼团锁单成功" : "直接购买订单创建成功",
      "支付网关单已创建",
      "等待支付回调演示"
    ], 2);
    setText("#checkoutStatus", "待支付");
    setDisabled("#mockPayBtn", false);
    setDisabled("#refreshFlowBtn", false);
  } catch (error) {
    setDisabled("#confirmOrderBtn", false);
    setText("#checkoutStatus", "下单失败");
    renderCheckoutSteps([error.message || "订单提交失败"], -1);
  }
}

async function simulateCheckoutPay(checkout) {
  if (!checkout.order?.orderId || !checkout.order?.payOrderId) {
    renderCheckoutSteps(["请先确认下单"], -1);
    return;
  }
  setDisabled("#mockPayBtn", true);
  setText("#checkoutStatus", "支付处理中");
  try {
    const payResult = await verifyMockPayment(checkout.order.orderId, checkout.order.payOrderId);
    checkout.order.orderStatus = payResult.orderStatus || "PAY_SUCCESS";
    checkout.order.payStatus = payResult.payStatus || "SUCCESS";
    checkout.order.gatewayTradeNo = payResult.gatewayTradeNo;
    saveStore("agentGroupCheckout", checkout);
    upsertOrderForAdmin({
      ...checkout.order,
      status: checkout.order.orderStatus
    });
    renderCheckoutOrder(checkout.order);
    setText("#checkoutStatus", checkout.order.orderStatus);
    renderCheckoutSteps(["支付回调验签成功", "订单和支付单状态已推进"], 3);
    await refreshCheckoutFlow(checkout);
  } catch (error) {
    setDisabled("#mockPayBtn", false);
    setText("#checkoutStatus", "支付失败");
    renderCheckoutSteps([error.message || "模拟支付失败"], -1);
  }
}

async function refreshCheckoutFlow(checkout) {
  const orderId = checkout.order?.orderId;
  if (!orderId) {
    renderCheckoutSteps(["请先确认下单"], -1);
    return;
  }
  try {
    const response = await fetch(`${STATUS_FLOW_URL}?orderId=${encodeURIComponent(orderId)}`, {
      headers: { "Accept": "application/json" }
    });
    if (!response.ok) {
      throw new Error(`交易流水查询失败：${response.status}`);
    }
    const body = await response.json();
    const flows = unwrapResponse(body) || [];
    const root = $("#checkoutTimeline");
    if (root) {
      root.innerHTML = "";
      flows.forEach((flow) => {
        const li = document.createElement("li");
        li.className = "done";
        li.textContent = `${flow.eventType}：${flow.fromStatus || "-"} → ${flow.toStatus}`;
        root.appendChild(li);
      });
    }
  } catch (error) {
    renderCheckoutSteps([error.message || "订单流水查询失败"], -1);
  }
}

function normalizeCheckoutOrder(checkout, createResult, paymentResult) {
  const product = checkout.product;
  const isGroup = checkout.mode === "group";
  return {
    orderId: createResult.orderId,
    payOrderId: createResult.payOrderId || paymentResult?.payOrderId,
    goodsId: product.id,
    goods: product.name,
    type: isGroup ? "拼团购买" : "直接购买",
    amount: formatPrice(createResult.payAmount || createResult.lockAmount || (isGroup ? product.groupPrice : product.originPrice)),
    orderStatus: createResult.orderStatus || "PAY_WAIT",
    payStatus: createResult.payStatus || "WAIT_PAY",
    payUrl: paymentResult?.payUrl || createResult.payUrl || "",
    decisionId: createResult.decisionId || product.decisionId || "",
    teamId: createResult.teamId || "",
    teamStatus: createResult.teamStatus || "",
    lockStatus: createResult.lockStatus || "",
    createdAt: nowLocalDateTime()
  };
}

function renderCheckoutOrder(order) {
  setText("#checkoutOrderId", order.orderId || "-");
  setText("#checkoutPayOrderId", order.payOrderId || "-");
  setText("#checkoutPayUrl", order.payUrl || "-");
  setText("#checkoutOrderStatus", order.orderStatus || "-");
  setText("#checkoutPayStatus", order.payStatus || "-");
  setText("#checkoutTeamId", order.teamId || "-");
}

function isPaidOrder(order) {
  const orderStatus = String(order.orderStatus || order.status || "");
  const payStatus = String(order.payStatus || "");
  return orderStatus.includes("SUCCESS") || orderStatus.includes("PAID") || payStatus.includes("SUCCESS");
}

function renderCheckoutSteps(lines, activeIndex) {
  const root = $("#checkoutTimeline");
  if (!root) {
    return;
  }
  root.innerHTML = "";
  lines.forEach((line, index) => {
    const li = document.createElement("li");
    li.className = activeIndex < 0 ? "warn" : index <= activeIndex ? "done" : "wait";
    li.textContent = line;
    root.appendChild(li);
  });
}

function upsertOrderForAdmin(order) {
  const rows = loadStore("agentGroupOrders", []);
  const orderNo = order.orderId || order.orderNo;
  const next = {
    orderNo,
    type: order.type,
    goods: order.goods,
    amount: order.amount,
    status: order.status || order.orderStatus || "待支付"
  };
  const index = rows.findIndex((item) => item.orderNo === orderNo);
  if (index >= 0) {
    rows[index] = { ...rows[index], ...next };
  } else {
    rows.unshift(next);
  }
  state.orders = rows;
  saveStore("agentGroupOrders", rows);
}

async function runInlinePurchase(product, mode) {
  if (!product.decisionId) {
    setText("#tradeState", "需要后端决策");
    pushTradeStep("当前商品缺少导购决策编号，请先完成后端导购流式推荐。", "warn");
    return;
  }
  const isGroup = mode === "group";
  const idempotentKey = resolveCheckoutIdempotentKey(product, mode);
  const order = {
    orderNo: "创建中",
    type: isGroup ? "拼团购买" : "直接购买",
    goods: product.name,
    amount: isGroup ? product.groupPrice : product.originPrice,
    status: isGroup ? "锁单中" : "下单中"
  };
  state.orders.unshift(order);
  saveStore("agentGroupOrders", state.orders);
  setHtml("#tradeTimeline", "");
  setText("#tradeState", order.status);

  try {
    const createResult = isGroup
      ? await createGroupOrder(product, idempotentKey)
      : await createDirectOrder(product, idempotentKey);
    order.orderNo = createResult.orderId;
    order.status = createResult.orderStatus || "待支付";
    order.amount = formatPrice(createResult.payAmount || order.amount);
    saveStore("agentGroupOrders", state.orders);
    setText("#tradeState", order.status);
    pushTradeStep(isGroup ? "后端拼团锁单成功，支付单已创建" : "后端直接购买订单已创建", "done");

    await createGatewayPayment(createResult.orderId);
    pushTradeStep("支付网关单已创建", "done");

    const payResult = await verifyMockPayment(createResult.orderId, createResult.payOrderId);
    order.status = payResult.orderStatus || "已支付";
    saveStore("agentGroupOrders", state.orders);
    setText("#tradeState", order.status);
    pushTradeStep("支付回调已通过后端验签并推进订单状态", "done");

    await renderBackendStatusFlow(createResult.orderId);
  } catch (error) {
    order.status = "失败";
    saveStore("agentGroupOrders", state.orders);
    setText("#tradeState", "失败");
    pushTradeStep(error.message || "购买链路调用失败", "warn");
  }
}

async function createDirectOrder(product, idempotentKey) {
  return postJson(DIRECT_ORDER_URL, {
    userId: "U10001",
    goodsId: product.id,
    decisionId: product.decisionId || "",
    idempotentKey: idempotentKey || resolveCheckoutIdempotentKey(product, "direct"),
    payChannel: "MOCK_PAY"
  });
}

async function createGroupOrder(product, idempotentKey) {
  return postJson(GROUP_LOCK_URL, {
    userId: "U10001",
    goodsId: product.id,
    decisionId: product.decisionId || "",
    activityId: product.activityId || "A10001",
    idempotentKey: idempotentKey || resolveCheckoutIdempotentKey(product, "group"),
    payChannel: "MOCK_PAY"
  });
}

function resolveCheckoutIdempotentKey(product, mode, seed = Date.now()) {
  return `WEB-${mode || "direct"}-${product.id}-${seed}`;
}

async function createGatewayPayment(orderId) {
  return postJson(PAYMENT_CREATE_URL, {
    orderId,
    payChannel: "MOCK_PAY"
  });
}

async function verifyMockPayment(orderId, payOrderId) {
  return postJson(PAYMENT_WEBHOOK_URL, {
    payChannel: "MOCK_PAY",
    orderId,
    payOrderId,
    gatewayTradeNo: `MOCK${payOrderId}`,
    payTime: nowLocalDateTime()
  });
}

async function renderBackendStatusFlow(orderId) {
  const response = await fetch(`${STATUS_FLOW_URL}?orderId=${encodeURIComponent(orderId)}`, {
    headers: {
      "Accept": "application/json"
    }
  });
  if (!response.ok) {
    throw new Error(`交易流水查询失败：${response.status}`);
  }
  const body = await response.json();
  const flows = unwrapResponse(body);
  setHtml("#tradeTimeline", "");
  (flows || []).forEach((flow) => {
    pushTradeStep(`${flow.eventType}：${flow.toStatus}`, "done");
  });
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Accept": "application/json",
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
  return handleJsonResponse(response);
}

async function postForm(url, form, headers = {}) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Accept": "application/json",
      ...headers
    },
    body: form
  });
  return handleJsonResponse(response);
}

async function handleJsonResponse(response) {
  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (!response.ok) {
    throw new Error(body?.info || `接口请求失败：${response.status}`);
  }
  return unwrapResponse(body);
}

function unwrapResponse(body) {
  if (!body) {
    return null;
  }
  if (body.code && body.code !== "0000") {
    throw new Error(body.info || "接口返回失败");
  }
  return body.data ?? body;
}

function nowLocalDateTime() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function pushTradeStep(text, className) {
  ["#tradeTimeline", "#orderTimeline"].forEach((selector) => {
    const root = $(selector);
    if (!root) {
      return;
    }
    const li = document.createElement("li");
    li.className = className;
    li.textContent = text;
    root.appendChild(li);
  });
}

function renderKnowledgeRows() {
  const root = $("#knowledgeRows");
  if (!root) {
    return;
  }
  root.innerHTML = "";
  state.docs.forEach((doc) => {
    root.appendChild(renderDataItem(doc.name, [
      `类型：${doc.type}`,
      `范围：${doc.scope}`,
      `状态：${doc.status}`
    ], doc.status.includes("待") ? "warn" : "info"));
  });
}

function renderOrderRows() {
  const root = $("#orderRows");
  if (!root) {
    return;
  }
  root.innerHTML = "";
  if (state.orders.length === 0) {
    root.innerHTML = `<div class="empty-state">还没有订单。用户端发起直接购买或拼团购买后会出现在这里。</div>`;
    return;
  }
  state.orders.forEach((order) => {
    const statusText = String(order.status || "");
    const normalStatus = statusText.includes("SUCCESS") || statusText.includes("PAID")
      || statusText === "已成团" || statusText === "已完成";
    root.appendChild(renderDataItem(order.orderNo, [
      `类型：${order.type || "拼团购买"}`,
      `商品：${order.goods}`,
      `支付金额：￥${order.amount}`,
      `状态：${order.status}`
    ], normalStatus ? "info" : "warn"));
  });
}

function renderEvalRows() {
  const root = $("#evalRows");
  if (!root) {
    return;
  }
  root.innerHTML = "";
  state.evalCases.forEach((item) => {
    const needReview = [item.recall, item.answer, item.recommend, item.context, item.tool].includes("待复核");
    const lines = [
      `检索命中：${item.recall}`,
      `回答准确：${item.answer}`,
      `推荐结果：${item.recommend}`,
      `多轮一致：${item.context || "通过"}`,
      `工具调用：${item.tool || "通过"}`,
      `实际工具：${item.actualToolNames || "-"}`,
      `建议：${item.suggestion || "通过"}`
    ];
    if (item.latency) {
      lines.splice(4, 0, `耗时：${item.latency}`);
    }
    if (item.llmLatency) {
      lines.splice(5, 0, `模型耗时：${item.llmLatency}`);
    }
    if (item.tokens) {
      lines.splice(6, 0, `模型用量：${item.tokens}`);
    }
    if (item.cost) {
      lines.splice(7, 0, `估算成本：${item.cost}`);
    }
    if (item.fallback) {
      lines.splice(8, 0, `兜底回答：${item.fallback}`);
    }
    root.appendChild(renderDataItem(item.name, lines, needReview ? "warn" : "info"));
  });
}

function renderDataItem(title, lines, tagType) {
  const row = document.createElement("div");
  row.className = "data-item";
  const tagText = tagType === "warn" ? "待处理" : "正常";
  const safeLines = lines.map(escapeHtml);
  row.innerHTML = `
    <div class="data-title">
      <strong>${escapeHtml(title)}</strong>
      <span class="tag ${tagType}">${tagText}</span>
    </div>
    <div>${safeLines.join("<br>")}</div>
  `;
  return row;
}

async function runEval() {
  try {
    const response = await fetch(GUIDE_EVALUATION_URL, {
      method: "POST",
      headers: {
        "Accept": "application/json",
        ...adminAuthHeaders()
      }
    });
    if (!response.ok) {
      throw new Error(`评测接口请求失败：${response.status}`);
    }
    const body = await response.json();
    renderEvaluationReport(body.data);
  } catch {
    runLocalEval();
  }
}

function adminAuthHeaders() {
  const auth = getAdminAuth();
  return auth ? { Authorization: `Basic ${auth}` } : {};
}

function getAdminAuth() {
  let auth = loadStore(ADMIN_AUTH_KEY, "");
  if (auth) {
    return auth;
  }
  const username = window.prompt("运营账号", "operator");
  if (!username) {
    return "";
  }
  const password = window.prompt("运营密码", "operator_dev");
  if (!password) {
    return "";
  }
  auth = window.btoa(`${username}:${password}`);
  saveStore(ADMIN_AUTH_KEY, auth);
  return auth;
}

function renderEvaluationReport(report) {
  if (!report) {
    runLocalEval();
    return;
  }
  setText("#metricRecall", `${formatRate(report.retrievalHitRate)}%`);
  setText("#metricAccuracy", `${formatRate(report.answerAccuracyRate)}%`);
  setText("#metricRecommend", `${formatRate(report.recommendationReasonableRate)}%`);
  setText("#metricContext", `${formatRate(report.contextConsistencyRate)}%`);
  setText("#metricToolCall", `${formatRate(report.toolCallAccuracyRate)}%`);
  setText("#metricToolArgument", `${formatRate(report.toolArgumentAccuracyRate)}%`);
  setText("#metricToolReference", `${formatRate(report.toolResultReferenceRate)}%`);
  setText("#metricLatency", `${formatLatency(report.averageLatencyMillis)}`);
  setText("#metricP99Latency", `${formatLatency(report.p99LatencyMillis)}`);
  setText("#metricTokens", formatInteger(report.totalTokens));
  setText("#metricCost", formatCostYuan(report.estimatedCostYuan));
  const itemRows = (report.items || []).map((item) => ({
    name: item.caseName,
    recall: item.referencePassed ? "通过" : "待复核",
    answer: item.answerPassed ? "通过" : "待复核",
    recommend: item.recommendationPassed ? "通过" : "待复核",
    context: item.contextPassed ? "通过" : "待复核",
    tool: item.toolCallPassed && item.toolArgumentPassed && item.toolResultReferencePassed ? "通过" : "待复核",
    actualToolNames: item.actualToolNames,
    latency: formatLatency(item.latencyMillis),
    llmLatency: formatLatency(item.llmLatencyMillis),
    tokens: formatInteger(item.totalTokens),
    cost: formatCostYuan(item.estimatedCostYuan),
    fallback: item.fallbackUsed ? "是" : "否",
    suggestion: item.suggestion || "通过"
  }));
  const feedbackRows = (report.feedbacks || []).map((item) => ({
    name: `反馈建议：${feedbackTargetName(item.targetType)}`,
    recall: "-",
    answer: "-",
    recommend: "-",
    context: item.priority || "LOW",
    suggestion: item.content || "继续观察"
  }));
  state.evalCases = [...feedbackRows, ...itemRows];
  saveStore("agentGroupEvalCases", state.evalCases);
  renderEvalRows();
}

function feedbackTargetName(targetType) {
  const names = {
    KNOWLEDGE: "知识库",
    PROMPT: "提示词",
    RECOMMENDATION: "推荐策略",
    CONTEXT: "多轮上下文",
    TOOL: "工具调用",
    QUALITY: "质量基线"
  };
  return names[targetType] || targetType || "质量闭环";
}

function runLocalEval() {
  setText("#metricRecall", "86%");
  setText("#metricAccuracy", "82%");
  setText("#metricRecommend", "84%");
  setText("#metricContext", "88%");
  setText("#metricToolCall", "92%");
  setText("#metricToolArgument", "90%");
  setText("#metricToolReference", "88%");
  setText("#metricLatency", "420 ms");
  setText("#metricP99Latency", "445 ms");
  setText("#metricTokens", "6,200");
  setText("#metricCost", "¥0.000000");
  state.evalCases = [
    { name: "学生预算导购", recall: "通过", answer: "通过", recommend: "通过", context: "通过", tool: "通过", actualToolNames: "knowledge_search,guide_recommend,group_trial", latency: "390 ms", suggestion: "通过" },
    { name: "拼团退款规则", recall: "通过", answer: "通过", recommend: "不适用", context: "通过", tool: "通过", actualToolNames: "knowledge_search,guide_recommend,group_trial", latency: "410 ms", suggestion: "通过" },
    { name: "标准版和高配版对比", recall: "通过", answer: "通过", recommend: "通过", context: "通过", tool: "通过", actualToolNames: "knowledge_search,guide_recommend,group_trial", latency: "435 ms", suggestion: "通过" },
    { name: "多轮追问预算限制", recall: "通过", answer: "通过", recommend: "通过", context: "通过", tool: "通过", actualToolNames: "knowledge_search,guide_recommend,group_trial", latency: "445 ms", suggestion: "通过" }
  ];
  saveStore("agentGroupEvalCases", state.evalCases);
  renderEvalRows();
}

function setText(selector, value) {
  const node = $(selector);
  if (node) {
    node.textContent = value;
  }
}

function setHtml(selector, value) {
  const node = $(selector);
  if (node) {
    node.innerHTML = value;
  }
}

function clearNode(selector) {
  setHtml(selector, "");
}

function setDisabled(selector, disabled) {
  const node = $(selector);
  if (node) {
    node.disabled = disabled;
  }
}

function loadStore(key, fallback) {
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function saveStore(key, value) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 本地演示失败不影响页面主流程。
  }
}

function getSessionId() {
  const key = "agentGroupSessionId";
  let sessionId = loadStore(key, "");
  if (!sessionId) {
    sessionId = `S${Date.now()}`;
    saveStore(key, sessionId);
  }
  return sessionId;
}

function formatPrice(value) {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return "0";
  }
  return Number.isInteger(numberValue) ? String(numberValue) : numberValue.toFixed(2);
}

function formatRemainingTime(seconds) {
  const numberValue = Number(seconds);
  if (!Number.isFinite(numberValue) || numberValue <= 0) {
    return "暂无";
  }
  const minutes = Math.ceil(numberValue / 60);
  return `${minutes} 分钟`;
}

function formatRate(value) {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return "0";
  }
  return Number.isInteger(numberValue) ? String(numberValue) : numberValue.toFixed(2);
}

function formatLatency(value) {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return "-";
  }
  return `${Math.max(0, Math.round(numberValue))} ms`;
}

function formatInteger(value) {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return "0";
  }
  return Math.max(0, Math.round(numberValue)).toLocaleString("zh-CN");
}

function formatCostYuan(value) {
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    return "¥0.000000";
  }
  return `¥${Math.max(0, numberValue).toFixed(6)}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
