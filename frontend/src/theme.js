const THEME_KEY = "agentGroupTheme";
const THEMES = new Set(["dark", "light"]);

export function getStoredTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY);
    return THEMES.has(saved) ? saved : "light";
  } catch {
    return "light";
  }
}

export function applyTheme(theme) {
  const normalized = THEMES.has(theme) ? theme : "dark";
  document.documentElement.dataset.theme = normalized;
  document.body.dataset.theme = normalized;
  try {
    localStorage.setItem(THEME_KEY, normalized);
  } catch {
    // 忽略本地存储不可用的情况，页面仍按当前主题显示。
  }
  return normalized;
}

export function nextTheme(theme) {
  return theme === "dark" ? "light" : "dark";
}
