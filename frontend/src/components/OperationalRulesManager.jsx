import { useCallback, useEffect, useMemo, useState } from "react";
import { RefreshCw, Save, Settings2 } from "lucide-react";
import { normalizeApiMessage, queryOperationalRules, updateOperationalRule } from "../services/api";

export default function OperationalRulesManager({ authVersion = 0 }) {
  const [rules, setRules] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [loading, setLoading] = useState(false);
  const [savingKey, setSavingKey] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadRules = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await queryOperationalRules();
      if (res?.code === "0000") {
        const items = res.data || [];
        setRules(items);
        const nextDrafts = {};
        items.forEach((rule) => {
          nextDrafts[rule.ruleKey] = rule.ruleValue ?? "";
        });
        setDrafts(nextDrafts);
      } else {
        setError(normalizeApiMessage(res, "加载运营规则失败"));
      }
    } catch (err) {
      setError(err?.message || "加载运营规则失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRules();
  }, [authVersion, loadRules]);

  const groupedRules = useMemo(() => {
    const groups = {};
    rules.forEach((rule) => {
      const group = rule.ruleGroup || "其他规则";
      (groups[group] = groups[group] || []).push(rule);
    });
    return groups;
  }, [rules]);

  const handleSave = async (ruleKey) => {
    if (!ruleKey || savingKey) return;
    setSavingKey(ruleKey);
    setMessage("");
    setError("");
    try {
      const res = await updateOperationalRule(ruleKey, drafts[ruleKey] ?? "");
      if (res?.code === "0000") {
        setMessage(`规则 ${ruleKey} 已更新`);
        await loadRules();
      } else {
        setError(normalizeApiMessage(res, "更新规则失败"));
      }
    } catch (err) {
      setError(err?.message || "更新规则失败");
    } finally {
      setSavingKey("");
    }
  };

  return (
    <div className="operational-rules-manager">
      <div className="admin-card">
        <div className="admin-card-header">
          <div className="admin-title-line"><Settings2 size={18} /><h3>运营规则</h3></div>
          <button className="admin-btn outline small" type="button" onClick={loadRules} disabled={loading}>
            <RefreshCw size={14} className={loading ? "spin" : ""} />
            <span>刷新</span>
          </button>
        </div>
        <div className="admin-card-body">
          <p className="admin-desc">动态配置降级开关、通知地址、Agent 执行开关等运行参数，修改后立即生效。</p>
          {Object.entries(groupedRules).map(([group, items]) => (
            <section className="ops-block" key={group}>
              <div className="admin-title-line ops-title"><h4>{group}</h4></div>
              <div className="table-wrap compact">
                <table className="admin-table compact">
                  <thead>
                    <tr><th>规则键</th><th>当前值</th><th>说明</th><th>更新时间</th><th>操作</th></tr>
                  </thead>
                  <tbody>
                    {items.map((rule) => (
                      <tr key={rule.ruleKey}>
                        <td className="mono">{rule.ruleKey}</td>
                        <td>
                          <input
                            className="ops-rule-input"
                            value={drafts[rule.ruleKey] ?? ""}
                            onChange={(event) => setDrafts((prev) => ({ ...prev, [rule.ruleKey]: event.target.value }))}
                          />
                        </td>
                        <td>{rule.description || "-"}</td>
                        <td>{rule.updateTime ? String(rule.updateTime).replace("T", " ") : "-"}</td>
                        <td>
                          <button
                            className="admin-btn outline small"
                            type="button"
                            disabled={savingKey === rule.ruleKey}
                            onClick={() => handleSave(rule.ruleKey)}
                          >
                            <Save size={14} />
                            <span>{savingKey === rule.ruleKey ? "保存中…" : "保存"}</span>
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
          {rules.length === 0 && (
            <p className="admin-desc">{loading ? "正在加载规则…" : "暂无运营规则"}</p>
          )}
        </div>
      </div>
      {message && <div className="admin-tip">{message}</div>}
      {error && <div className="admin-error"><span>{error}</span></div>}
    </div>
  );
}
