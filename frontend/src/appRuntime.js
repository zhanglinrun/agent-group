import { normalizeFileUrlForBrowser } from "./fileUrl";
import { mergeArtifacts, mergeResultPanels, toUiArtifact } from "./taskArtifacts";

export function apiSucceeded(res) {
  return res?.code === "0000" || res?.code === 200 || res?.code === "200";
}

export function isOperatorAuthText(value = "") {
  const text = String(value || "");
  return text.includes("\u8fd0\u8425\u8d26\u53f7") || text.includes("\u8fd0\u8425\u8d26\u53f7\u8bbf\u95ee\u8be5\u63a5\u53e3");
}

export function createRuntimeId(prefix) {
  return `${prefix}${Date.now()}`;
}

export function attachReplayTimeline(messages = [], timeline = [], artifacts = [], resultPanels = []) {
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

export function hasAssistantPayload(message = {}) {
  return Boolean(
    String(message.content || "").trim()
      || (message.timeline || []).length
      || (message.reference || []).length
      || (message.artifacts || []).length
      || (message.resultPanels || []).length
      || (message.recommend || []).length
  );
}

export function latestAssistantWithPayload(messages = []) {
  return [...messages].reverse().find((message) => message.role === "assistant" && hasAssistantPayload(message)) || null;
}

export function hasSessionMemory(memory = {}) {
  return Boolean(
    (memory.runs || []).length
      || (memory.toolObservations || []).length
      || (memory.reusableArtifacts || []).length
  );
}

export function toUiReference(data = {}) {
  return {
    title: data.title || data.fileId || "参考资料",
    url: data.url || data.link || "",
    text: data.content || data.snippet || data.summary || data.text || ""
  };
}

export function safeExternalUrl(url = "") {
  const value = String(url || "").trim();
  if (!value) return "";
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : "";
  } catch {
    return "";
  }
}

export function safeResourceUrl(url = "") {
  const value = normalizeFileUrlForBrowser(url);
  if (!value) return "";
  if (value.startsWith("/") && !value.startsWith("//")) return value;
  return safeExternalUrl(value);
}

export function paymentReturnUrl(orderId = "") {
  if (typeof window === "undefined") return "";
  const url = new URL(window.location.origin);
  url.searchParams.set("paymentReturn", "1");
  if (orderId) url.searchParams.set("orderId", orderId);
  return url.toString();
}

export function submitPaymentForm(payHtml = "", targetWindow = null) {
  const html = String(payHtml || "").trim();
  if (!html) return false;
  const page = `<!doctype html><html><head><meta charset="UTF-8"><title>支付宝支付</title></head><body>${html}<script>window.opener=null;var form=document.forms[0];if(form){form.submit();}</script></body></html>`;
  if (targetWindow && !targetWindow.closed) {
    targetWindow.document.open();
    targetWindow.document.write(page);
    targetWindow.document.close();
    return true;
  }
  const container = document.createElement("div");
  container.hidden = true;
  container.innerHTML = html;
  const form = container.querySelector("form");
  if (!form) return false;
  form.target = "_blank";
  document.body.appendChild(container);
  form.submit();
  window.setTimeout(() => container.remove(), 1000);
  return true;
}

export function isPaymentFormHtml(value = "") {
  return /<form[\s>]/i.test(String(value || ""));
}

export function preferredFrontendPayChannel(explicitChannel = "") {
  const configured = String(explicitChannel || import.meta.env?.VITE_PAYMENT_CHANNEL || "").trim();
  if (configured) return configured.toUpperCase();
  const host = typeof window !== "undefined" ? window.location?.hostname : "";
  return host === "localhost" || host === "127.0.0.1" || host === "::1" ? "MOCK_PAY" : "ALIPAY";
}

export function isMockPayment(payment = {}) {
  const channel = String(payment.payChannel || "").toUpperCase();
  const payUrl = String(payment.payUrl || "");
  return channel === "MOCK_PAY" || payUrl.startsWith("mock://");
}

export function openGatewayPayment(payment = {}, targetWindow = null) {
  const payFormHtml = payment.payFormHtml || (payment.paymentType === "PAGE_FORM" ? payment.payUrl : "");
  if (payFormHtml && submitPaymentForm(payFormHtml, targetWindow)) {
    return true;
  }
  const payUrl = safeExternalUrl(payment.payUrl || "");
  if (!payUrl) return false;
  if (targetWindow && !targetWindow.closed) {
    targetWindow.opener = null;
    targetWindow.location.href = payUrl;
  } else {
    window.open(payUrl, "_blank", "noopener,noreferrer");
  }
  return true;
}

export function isImageArtifact(artifact = {}) {
  const type = String(artifact.contentType || artifact.type || "").toLowerCase();
  const name = String(artifact.fileName || artifact.title || artifact.previewUrl || artifact.downloadUrl || "").toLowerCase();
  return type.startsWith("image/") || /\.(png|jpe?g|webp|gif|svg)(\?.*)?$/.test(name);
}

export function isImageUpload(file = {}) {
  return isImageArtifact({
    contentType: file.type || file.contentType || file.fileType || "",
    fileName: file.name || file.fileName || ""
  });
}

export function createLocalPreviewUrl(file) {
  if (!isImageUpload(file) || typeof URL === "undefined" || typeof URL.createObjectURL !== "function") {
    return "";
  }
  return URL.createObjectURL(file);
}

export function revokeLocalPreviewUrl(url = "") {
  if (String(url || "").startsWith("blob:") && typeof URL !== "undefined" && typeof URL.revokeObjectURL === "function") {
    URL.revokeObjectURL(url);
  }
}

export function workspaceImageArtifacts(data = {}) {
  return [
    ...(Array.isArray(data.fileRefs) ? data.fileRefs : []),
    ...(Array.isArray(data.artifactRefs) ? data.artifactRefs : [])
  ].map(toUiArtifact).filter((artifact) => artifact.fileName || artifact.downloadUrl || artifact.previewUrl);
}

export function workspaceImageToolResultEvent(data = {}, fallbackInvocationId = "") {
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

export function workspaceDataToolResultEvent(result = {}) {
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

export function workspaceMragToolResultEvent(result = {}) {
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
