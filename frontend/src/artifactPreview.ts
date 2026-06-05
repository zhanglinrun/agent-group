import { normalizeFileUrlForBrowser } from "./fileUrl";

export type ArtifactPreviewKind = "image" | "html" | "text" | "file" | "none";

export type ArtifactPreviewModel = {
  kind: ArtifactPreviewKind;
  canPreview: boolean;
  title: string;
  fileName: string;
  type: string;
  url: string;
  downloadUrl: string;
  inlineText: string;
};

type UnknownMap = Record<string, unknown>;

const IMAGE_EXTENSIONS = new Set(["png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "avif"]);
const HTML_EXTENSIONS = new Set(["html", "htm"]);
const TEXT_EXTENSIONS = new Set(["txt", "md", "markdown", "json", "csv", "log", "xml", "yml", "yaml"]);

function text(value: unknown): string {
  return String(value ?? "").trim();
}

function safeResourceUrl(value: unknown): string {
  const normalized = normalizeFileUrlForBrowser(text(value));
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("/") && !normalized.startsWith("//")) {
    return normalized;
  }
  try {
    const parsed = new URL(normalized);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : "";
  } catch {
    return "";
  }
}

function fileNameFromUrl(url: string): string {
  if (!url) {
    return "";
  }
  try {
    const parsed = new URL(url, "https://workspace.local");
    return decodeURIComponent(parsed.pathname.split("/").filter(Boolean).pop() || "");
  } catch {
    return url.split(/[/?#]/)[0] || "";
  }
}

function extensionOf(fileName: string): string {
  const match = text(fileName).toLowerCase().match(/\.([a-z0-9+_-]+)(?:[?#].*)?$/);
  return match?.[1] || "";
}

function artifactKind(artifact: UnknownMap, fileName: string): ArtifactPreviewKind {
  const contentType = text(artifact.contentType || artifact.mimeType).toLowerCase();
  const type = text(artifact.type || artifact.artifactType).toLowerCase();
  const ext = extensionOf(fileName);
  if (contentType.startsWith("image/") || IMAGE_EXTENSIONS.has(ext)) {
    return "image";
  }
  if (contentType.includes("html") || type.includes("html") || HTML_EXTENSIONS.has(ext)) {
    return "html";
  }
  if (contentType.startsWith("text/") || contentType.includes("json") || TEXT_EXTENSIONS.has(ext)) {
    return "text";
  }
  return "file";
}

export function buildArtifactPreviewModel(value: unknown): ArtifactPreviewModel {
  const artifact = value && typeof value === "object" && !Array.isArray(value)
    ? value as UnknownMap
    : {};
  const previewUrl = safeResourceUrl(artifact.previewUrl);
  const downloadUrl = safeResourceUrl(artifact.downloadUrl || artifact.ossUrl || artifact.url);
  const url = previewUrl || downloadUrl;
  const fileName = text(artifact.fileName || artifact.filename || artifact.name || artifact.title)
    || fileNameFromUrl(url)
    || "artifact";
  const kind = artifactKind(artifact, fileName);
  const inlineText = text(artifact.content);
  const resolvedKind = url || inlineText ? kind : "none";
  return {
    kind: resolvedKind,
    canPreview: resolvedKind === "image" || resolvedKind === "html" || resolvedKind === "text" || Boolean(inlineText),
    title: text(artifact.title) || fileName,
    fileName,
    type: text(artifact.type || artifact.artifactType || artifact.contentType || kind),
    url,
    downloadUrl,
    inlineText
  };
}
