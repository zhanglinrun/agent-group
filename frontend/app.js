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

const state = {
  timers: [],
  streaming: false,
  answerTarget: null,
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
    }
  });
}

function runGuide(message) {
  stopTimers();
  state.streaming = true;
  state.answerTarget = null;
  state.products = [];
  setText("#connectionStatus", "流式生成中");
  setText("#sessionHint", "正在理解你的需求");
  setText("#decisionCopy", "系统正在识别预算、场景和限制，并检索商品知识库。");
  setText("#productCount", "0 个");
  setText("#tradeState", "未开始");
  setDisabled("#sendBtn", true);
  setDisabled("#stopBtn", false);

  clearNode("#productDeck");
  clearNode("#referenceList");
  clearNode("#tradeTimeline");

  addMessage("user", "你", message);
  state.answerTarget = addMessage("assistant", "AI 导购", "");

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
  schedule(3700, finishStream);
}

function schedule(delay, task) {
  const timer = window.setTimeout(task, delay);
  state.timers.push(timer);
}

function stopTimers() {
  state.timers.forEach((timer) => window.clearTimeout(timer));
  state.timers = [];
}

function stopCurrentStream() {
  if (!state.streaming) {
    return;
  }
  stopTimers();
  state.streaming = false;
  setDisabled("#sendBtn", false);
  setDisabled("#stopBtn", true);
  setText("#sessionHint", "本轮已停止");
  setText("#connectionStatus", "本地演示");
  setText("#decisionCopy", "已记录中断状态，可以继续补充需求后再次发送。");
  addMessage("system", "系统", "本轮生成已停止，已记录中断状态。");
}

function finishStream() {
  stopTimers();
  state.streaming = false;
  setDisabled("#sendBtn", false);
  setDisabled("#stopBtn", true);
  setText("#sessionHint", "建议优先选择标准版");
  setText("#connectionStatus", "本地演示");
  setText("#decisionCopy", "标准版满足学习、写论文和看网课需求。直接购买按原价支付，拼团购买按优惠价支付。");
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
  references.forEach((item) => {
    const row = document.createElement("div");
    row.className = "reference-item";
    row.innerHTML = `<strong>${escapeHtml(item.title)}</strong><br>${escapeHtml(item.text)}`;
    root.appendChild(row);
  });
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
    <div class="product-art" aria-hidden="true"><div class="tablet-shape"></div></div>
    <div class="product-info">
      <div class="product-title">
        <strong>${escapeHtml(product.name)}</strong>
        <span class="price">拼团 ￥${product.groupPrice}</span>
      </div>
      <div class="price-stack">
        <span>直接价 ￥${product.originPrice}</span>
        <span>拼团价 ￥${product.groupPrice}</span>
      </div>
      <div class="product-meta">${escapeHtml(product.spec)}</div>
      <p class="product-reason">${escapeHtml(product.reason)}</p>
      <div class="product-meta">不适合：${escapeHtml(product.notSuitable)}</div>
      <div class="product-meta">${product.teamSize} 人成团 · 剩余 ${escapeHtml(product.leftTime)}</div>
      <div class="product-actions">
        <button class="soft-button direct-buy-button" type="button">直接购买</button>
        <button class="primary-button group-buy-button" type="button">拼团购买</button>
        <button class="soft-button" type="button">查看规则</button>
      </div>
    </div>
  `;
  card.querySelector(".direct-buy-button").addEventListener("click", () => startPurchase(product, "direct"));
  card.querySelector(".group-buy-button").addEventListener("click", () => startPurchase(product, "group"));
  root.appendChild(card);
}

function startPurchase(product, mode) {
  const isGroup = mode === "group";
  const orderNo = `O${Date.now()}`;
  const order = {
    orderNo,
    type: isGroup ? "拼团购买" : "直接购买",
    goods: product.name,
    amount: isGroup ? product.groupPrice : product.originPrice,
    status: isGroup ? "锁单中" : "待支付"
  };
  state.orders.unshift(order);
  saveStore("agentGroupOrders", state.orders);
  setHtml("#tradeTimeline", "");
  setText("#tradeState", order.status);

  if (!isGroup) {
    pushTradeStep("创建直接购买订单，按原价支付", "done");
    setTimeout(() => {
      order.status = "已支付";
      saveStore("agentGroupOrders", state.orders);
      setText("#tradeState", "已支付");
      pushTradeStep("模拟支付回调验签通过", "done");
    }, 600);
    setTimeout(() => {
      order.status = "已完成";
      saveStore("agentGroupOrders", state.orders);
      setText("#tradeState", "已完成");
      pushTradeStep("直接购买订单完成，不进入拼团结算", "done");
    }, 1200);
    return;
  }

  pushTradeStep("开始拼团锁单", "done");

  setTimeout(() => {
    order.status = "待支付";
    saveStore("agentGroupOrders", state.orders);
    setText("#tradeState", "待支付");
    pushTradeStep("锁单成功，支付单已创建", "done");
  }, 500);

  setTimeout(() => {
    order.status = "已支付";
    saveStore("agentGroupOrders", state.orders);
    setText("#tradeState", "已支付");
    pushTradeStep("模拟支付回调验签通过", "done");
  }, 1100);

  setTimeout(() => {
    order.status = "已成团";
    saveStore("agentGroupOrders", state.orders);
    setText("#tradeState", "已成团");
    pushTradeStep("拼团结算完成，订单进入已成团", "done");
  }, 1700);
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
    root.appendChild(renderDataItem(order.orderNo, [
      `类型：${order.type || "拼团购买"}`,
      `商品：${order.goods}`,
      `支付金额：￥${order.amount}`,
      `状态：${order.status}`
    ], order.status === "已成团" || order.status === "已完成" ? "info" : "warn"));
  });
}

function renderEvalRows() {
  const root = $("#evalRows");
  if (!root) {
    return;
  }
  root.innerHTML = "";
  state.evalCases.forEach((item) => {
    root.appendChild(renderDataItem(item.name, [
      `检索命中：${item.recall}`,
      `回答准确：${item.answer}`,
      `推荐结果：${item.recommend}`
    ], item.answer === "待复核" ? "warn" : "info"));
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

function runEval() {
  setText("#metricRecall", "86%");
  setText("#metricAccuracy", "82%");
  setText("#metricRecommend", "84%");
  setText("#metricContext", "88%");
  state.evalCases = [
    { name: "学生预算导购", recall: "Top 1", answer: "通过", recommend: "通过" },
    { name: "拼团退款规则", recall: "Top 1", answer: "通过", recommend: "不适用" },
    { name: "标准版和高配版对比", recall: "Top 2", answer: "通过", recommend: "通过" },
    { name: "多轮追问预算限制", recall: "Top 3", answer: "通过", recommend: "通过" }
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

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
