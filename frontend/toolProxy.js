function trimTrailingSlash(value) {
  return String(value || "").trim().replace(/\/+$/, "");
}

function buildDefaultToolTarget() {
  return "http://127.0.0.1:1801";
}

function parseToolBaseUrl(rawBaseUrl) {
  const normalized = trimTrailingSlash(rawBaseUrl);
  if (!normalized) {
    return {
      target: buildDefaultToolTarget(),
      basePath: ""
    };
  }

  try {
    const parsed = new URL(normalized);
    return {
      target: `${parsed.protocol}//${parsed.host}`,
      basePath: parsed.pathname === "/" ? "" : trimTrailingSlash(parsed.pathname)
    };
  } catch {
    return {
      target: buildDefaultToolTarget(),
      basePath: ""
    };
  }
}

export function createToolProxyConfig(rawBaseUrl) {
  const { target, basePath } = parseToolBaseUrl(rawBaseUrl);

  return {
    target,
    changeOrigin: true,
    rewrite: (path) => {
      const normalizedPath = path.startsWith("/tool")
        ? path.slice("/tool".length) || "/"
        : path;
      return `${basePath}${normalizedPath}`;
    }
  };
}
