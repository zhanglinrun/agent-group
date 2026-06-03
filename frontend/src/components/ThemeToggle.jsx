import { Moon, Sun } from "lucide-react";

export default function ThemeToggle({ theme, onToggle, className = "" }) {
  const isDark = theme === "dark";
  return (
    <button
      type="button"
      className={`theme-toggle ${className}`.trim()}
      onClick={onToggle}
      title={isDark ? "切换浅色主题" : "切换深色主题"}
      aria-label={isDark ? "切换浅色主题" : "切换深色主题"}
    >
      {isDark ? <Sun size={15} /> : <Moon size={15} />}
      <span>{isDark ? "浅色" : "深色"}</span>
    </button>
  );
}
