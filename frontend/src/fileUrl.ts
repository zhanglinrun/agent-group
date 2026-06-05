function trimTrailingSlash(value: string): string {
  return String(value || "").trim().replace(/\/+$/, "");
}

function currentOrigin(): string {
  if (typeof window === "undefined") {
    return "";
  }
  return `${window.location.protocol}//${window.location.host}`;
}

function currentToolBaseUrl(): string {
  const origin = currentOrigin();
  return origin ? `${origin}/tool` : "";
}

function shouldRewriteToToolProxy(url: URL): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const isLoopback = url.hostname === "127.0.0.1" || url.hostname === "localhost";
  if (url.port === "1601" || (isLoopback && !url.port)) {
    return true;
  }

  return url.host === window.location.host && url.pathname.startsWith("/tool/");
}

export function normalizeToolBaseUrlForBrowser(rawUrl?: string | null): string {
  const normalized = trimTrailingSlash(rawUrl || "");
  const toolBaseUrl = currentToolBaseUrl();
  const origin = currentOrigin();
  if (!normalized) {
    return toolBaseUrl;
  }

  try {
    const parsed = new URL(normalized, origin || "https://workspace.local");
    if (!shouldRewriteToToolProxy(parsed) || !toolBaseUrl) {
      return parsed.toString().replace(/\/$/, "");
    }

    const proxyUrl = new URL(toolBaseUrl);
    proxyUrl.pathname = parsed.pathname.startsWith("/tool")
      ? parsed.pathname
      : `/tool${parsed.pathname}`;
    proxyUrl.search = parsed.search;
    proxyUrl.hash = parsed.hash;
    return proxyUrl.toString().replace(/\/$/, "");
  } catch {
    if (normalized.startsWith("/tool/") && origin) {
      return `${origin}${normalized}`;
    }
    return normalized;
  }
}

export function normalizeFileUrlForBrowser(rawUrl?: string | null): string {
  const normalized = String(rawUrl || "").trim();
  if (!normalized) {
    return "";
  }

  try {
    const parsed = new URL(normalized);
    if (!shouldRewriteToToolProxy(parsed)) {
      return parsed.toString();
    }

    const toolBaseUrl = currentToolBaseUrl();
    if (!toolBaseUrl) {
      return parsed.toString();
    }

    const proxyUrl = new URL(toolBaseUrl);
    proxyUrl.pathname = parsed.pathname.startsWith("/tool/")
      ? parsed.pathname
      : `/tool${parsed.pathname}`;
    proxyUrl.search = parsed.search;
    proxyUrl.hash = parsed.hash;
    return proxyUrl.toString();
  } catch {
    if (normalized.startsWith("/tool/")) {
      const origin = currentOrigin();
      return origin ? `${origin}${normalized}` : normalized;
    }
    return normalized;
  }
}
