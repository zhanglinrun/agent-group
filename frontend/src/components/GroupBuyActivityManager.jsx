import { useEffect, useState } from "react";
import { Activity, RefreshCw, Save, Tag, Trash2, X } from "lucide-react";
import {
  createGroupBuyActivity,
  listGroupBuyActivities,
  listGroupBuyDiscounts,
  normalizeApiMessage,
  queryGroupBuyDiscountOptions,
  queryGroupBuyGoodsOptions,
  removeGroupBuyActivity,
  removeGroupBuyDiscount,
  saveGroupBuyDiscount,
  setGroupBuyDiscountEnabled,
  updateGroupBuyActivity,
  updateGroupBuyActivityEnabled,
  updateGroupBuyActivityStock
} from "../services/api";

const EMPTY_FORM = {
  activityId: "",
  activityName: "",
  goodsId: "",
  groupPrice: "",
  teamSize: 3,
  target: 3,
  discountId: "",
  validTime: 1440,
  takeLimitCount: 1,
  startTime: "",
  endTime: "",
  tagId: "",
  tagScope: "",
  totalStock: 100
};

const EMPTY_DISCOUNT_FORM = {
  discountId: "",
  discountName: "",
  discountDesc: "",
  marketPlan: "ZJ",
  marketExpr: "",
  tagId: ""
};

const DISCOUNT_PLAN_LABELS = {
  ZJ: "直减（减 N 元）",
  MJ: "满减（满 A 减 B）",
  ZK: "折扣（N 折，填 0.8 表示八折）",
  N: "N 元购（固定 N 元）"
};

const DISCOUNT_PLAN_HINTS = {
  ZJ: "直减金额，如 3 表示减 3 元",
  MJ: "满,减，如 30,7 表示满 30 减 7",
  ZK: "折扣率，如 0.8 表示八折",
  N: "固定价格，如 1.99 表示 1.99 元购"
};

function toLocalInputValue(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toIsoValue(localValue) {
  if (!localValue) return null;
  const date = new Date(localValue);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

function defaultDiscountName(plan, expr) {
  switch (plan) {
    case "ZJ": return `直减 ${expr} 元`;
    case "MJ": return `满减 ${expr}`;
    case "ZK": return `${expr} 折`;
    case "N": return `${expr} 元购`;
    default: return "自定义折扣";
  }
}

export default function GroupBuyActivityManager() {
  const [activities, setActivities] = useState([]);
  const [goodsOptions, setGoodsOptions] = useState([]);
  const [discountOptions, setDiscountOptions] = useState([]);
  const [discounts, setDiscounts] = useState([]);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  const [discountEditing, setDiscountEditing] = useState(null);
  const [discountForm, setDiscountForm] = useState(EMPTY_DISCOUNT_FORM);
  const [discountSaving, setDiscountSaving] = useState(false);
  const [discountMessage, setDiscountMessage] = useState("");

  // 活动表单内联新建折扣：选 __new__ 时展开类型和金额输入，提交活动前先建折扣再关联。
  const [inlineDiscount, setInlineDiscount] = useState({ plan: "ZJ", expr: "", name: "" });

  const loadOptions = async () => {
    try {
      const [goodsRes, discountRes] = await Promise.all([
        queryGroupBuyGoodsOptions(),
        queryGroupBuyDiscountOptions()
      ]);
      if (goodsRes?.code === "0000") setGoodsOptions(goodsRes.data || []);
      if (discountRes?.code === "0000") setDiscountOptions(discountRes.data || []);
    } catch {
      // 未登录或网络异常时静默，等登录后刷新即可
    }
  };

  const loadActivities = async () => {
    setMessage("");
    try {
      const res = await listGroupBuyActivities();
      if (res?.code === "0000") {
        setActivities(res.data || []);
      } else {
        setMessage(normalizeApiMessage(res) || "加载活动列表失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "加载活动列表失败，请先登录运营端");
    }
  };

  const loadDiscounts = async () => {
    setDiscountMessage("");
    try {
      const res = await listGroupBuyDiscounts();
      if (res?.code === "0000") {
        setDiscounts(res.data || []);
      } else {
        setDiscountMessage(normalizeApiMessage(res) || "加载折扣列表失败");
      }
    } catch (err) {
      setDiscountMessage(normalizeApiMessage(err) || "加载折扣列表失败，请先登录运营端");
    }
  };

  useEffect(() => {
    loadActivities();
    loadOptions();
    loadDiscounts();
  }, []);

  const startCreate = () => {
    setEditing("create");
    setForm({ ...EMPTY_FORM });
  };

  const startEdit = (item) => {
    setEditing(item.activityId);
    setForm({
      activityId: item.activityId,
      activityName: item.activityName || "",
      goodsId: item.goodsId || "",
      groupPrice: item.groupPrice ?? "",
      teamSize: item.teamSize ?? 3,
      target: item.target ?? item.teamSize ?? 3,
      discountId: item.discountId || "",
      validTime: item.validTime ?? 1440,
      takeLimitCount: item.takeLimitCount ?? 1,
      startTime: toLocalInputValue(item.startTime),
      endTime: toLocalInputValue(item.endTime),
      tagId: item.tagId || "",
      tagScope: item.tagScope || "",
      totalStock: item.totalStock ?? 100
    });
  };

  const cancelEdit = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setInlineDiscount({ plan: "ZJ", expr: "", name: "" });
  };

  const updateField = (key, value) => {
    // 团价与折扣二选一：填了团价就清折扣，选了折扣就清团价。
    setForm((prev) => {
      const next = { ...prev, [key]: value };
      if (key === "groupPrice" && value !== "" && value != null) {
        next.discountId = "";
      }
      if (key === "discountId" && value) {
        next.groupPrice = "";
      }
      return next;
    });
    if (key === "discountId") {
      // 切换折扣来源时重置内联折扣输入；选自定义时保留默认 plan。
      setInlineDiscount((prev) => (value === "__new__" ? { ...prev, expr: "", name: "" } : { plan: "ZJ", expr: "", name: "" }));
    }
  };

  const updateInlineDiscount = (key, value) => {
    setInlineDiscount((prev) => ({ ...prev, [key]: value }));
  };

  const submit = async () => {
    setMessage("");
    const isInlineNew = form.discountId === "__new__";
    const hasDiscount = Boolean(form.discountId);
    if (!form.activityName || !form.goodsId || !form.startTime || !form.endTime) {
      setMessage("请补全活动名称、额度包和有效期");
      return;
    }
    if (!hasDiscount && (!form.groupPrice || Number(form.groupPrice) <= 0)) {
      setMessage("请填写团价，或在折扣下拉里选择/新建一个折扣（团价和折扣二选一）");
      return;
    }
    if (isInlineNew && !inlineDiscount.expr) {
      setMessage(`请填写折扣规则：${DISCOUNT_PLAN_HINTS[inlineDiscount.plan] || ""}`);
      return;
    }
    setSaving(true);
    try {
      let discountId = form.discountId || undefined;
      if (isInlineNew) {
        const name = inlineDiscount.name || defaultDiscountName(inlineDiscount.plan, inlineDiscount.expr);
        const discountRes = await saveGroupBuyDiscount({
          discountName: name,
          discountType: 0,
          marketPlan: inlineDiscount.plan,
          marketExpr: inlineDiscount.expr.trim()
        });
        if (discountRes?.code !== "0000" || !discountRes.data?.discountId) {
          setMessage(normalizeApiMessage(discountRes) || "新建折扣失败");
          setSaving(false);
          return;
        }
        discountId = discountRes.data.discountId;
      }
      const payload = {
        activityName: form.activityName,
        goodsId: form.goodsId,
        groupPrice: hasDiscount ? undefined : Number(form.groupPrice),
        teamSize: Number(form.teamSize),
        target: Number(form.target),
        discountId,
        validTime: Number(form.validTime),
        takeLimitCount: Number(form.takeLimitCount),
        startTime: toIsoValue(form.startTime),
        endTime: toIsoValue(form.endTime),
        tagId: form.tagId || undefined,
        tagScope: form.tagScope || undefined
      };
      const res = editing === "create"
        ? await createGroupBuyActivity({ ...payload, totalStock: Number(form.totalStock) })
        : await updateGroupBuyActivity(form.activityId, payload);
      if (res?.code === "0000") {
        if (editing !== "create" && form.totalStock !== "") {
          await updateGroupBuyActivityStock(form.activityId, Number(form.totalStock));
        }
        cancelEdit();
        await Promise.all([loadActivities(), isInlineNew ? Promise.all([loadDiscounts(), loadOptions()]) : Promise.resolve()]);
        setMessage(editing === "create" ? "活动已创建" : "活动已更新");
      } else {
        setMessage(normalizeApiMessage(res) || "保存失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "保存失败，请先登录运营端");
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (item) => {
    setMessage("");
    try {
      const res = await updateGroupBuyActivityEnabled(item.activityId, !item.enabled);
      if (res?.code === "0000") {
        await loadActivities();
      } else {
        setMessage(normalizeApiMessage(res) || "上下架失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "上下架失败，请先登录运营端");
    }
  };

  const remove = async (item) => {
    if (!window.confirm(`确认删除活动「${item.activityName || item.activityId}」？`)) return;
    setMessage("");
    try {
      const res = await removeGroupBuyActivity(item.activityId);
      if (res?.code === "0000") {
        await loadActivities();
        setMessage("活动已删除");
      } else {
        setMessage(normalizeApiMessage(res) || "删除失败");
      }
    } catch (err) {
      setMessage(normalizeApiMessage(err) || "删除失败，请先登录运营端");
    }
  };

  const startDiscountCreate = () => {
    setDiscountEditing("create");
    setDiscountForm({ ...EMPTY_DISCOUNT_FORM });
  };

  const startDiscountEdit = (item) => {
    setDiscountEditing(item.discountId);
    setDiscountForm({
      discountId: item.discountId || "",
      discountName: item.discountName || "",
      discountDesc: item.discountDesc || "",
      marketPlan: item.marketPlan || "ZJ",
      marketExpr: item.marketExpr || "",
      tagId: item.tagId || ""
    });
  };

  const cancelDiscountEdit = () => {
    setDiscountEditing(null);
    setDiscountForm(EMPTY_DISCOUNT_FORM);
  };

  const updateDiscountField = (key, value) => {
    setDiscountForm((prev) => ({ ...prev, [key]: value }));
  };

  const submitDiscount = async () => {
    setDiscountMessage("");
    if (!discountForm.discountName) {
      setDiscountMessage("请填写折扣名称");
      return;
    }
    if (!discountForm.marketExpr) {
      setDiscountMessage(`请填写折扣规则：${DISCOUNT_PLAN_HINTS[discountForm.marketPlan] || ""}`);
      return;
    }
    setDiscountSaving(true);
    try {
      const res = await saveGroupBuyDiscount({
        discountId: discountForm.discountId || undefined,
        discountName: discountForm.discountName,
        discountDesc: discountForm.discountDesc,
        discountType: discountForm.tagId ? 1 : 0,
        marketPlan: discountForm.marketPlan,
        marketExpr: discountForm.marketExpr,
        tagId: discountForm.tagId || undefined
      });
      if (res?.code === "0000") {
        cancelDiscountEdit();
        await Promise.all([loadDiscounts(), loadOptions()]);
        setDiscountMessage("折扣已保存");
      } else {
        setDiscountMessage(normalizeApiMessage(res) || "保存折扣失败");
      }
    } catch (err) {
      setDiscountMessage(normalizeApiMessage(err) || "保存折扣失败，请先登录运营端");
    } finally {
      setDiscountSaving(false);
    }
  };

  const toggleDiscountEnabled = async (item) => {
    setDiscountMessage("");
    try {
      const res = await setGroupBuyDiscountEnabled(item.discountId, !item.enabled);
      if (res?.code === "0000") {
        await Promise.all([loadDiscounts(), loadOptions()]);
      } else {
        setDiscountMessage(normalizeApiMessage(res) || "启停失败");
      }
    } catch (err) {
      setDiscountMessage(normalizeApiMessage(err) || "启停失败，请先登录运营端");
    }
  };

  const removeDiscount = async (item) => {
    if (!window.confirm(`确认删除折扣「${item.discountName || item.discountId}」？`)) return;
    setDiscountMessage("");
    try {
      const res = await removeGroupBuyDiscount(item.discountId);
      if (res?.code === "0000") {
        await Promise.all([loadDiscounts(), loadOptions()]);
        setDiscountMessage("折扣已删除");
      } else {
        setDiscountMessage(normalizeApiMessage(res) || "删除折扣失败");
      }
    } catch (err) {
      setDiscountMessage(normalizeApiMessage(err) || "删除折扣失败，请先登录运营端");
    }
  };

  const hasDiscountChoice = Boolean(form.discountId);

  return (
    <div className="ops-block">
      <div className="admin-title-line ops-title">
        <Activity size={16} />
        <h4>活动配置</h4>
        <button className="admin-btn outline small" onClick={loadActivities} title="刷新"><RefreshCw size={14} /></button>
        <button className="admin-btn primary small" onClick={startCreate}>新建活动</button>
      </div>

      {message && <div className="admin-tip">{message}</div>}

      {editing && (
        <div className="admin-form-panel">
          <div className="admin-title-line">
            <h5>{editing === "create" ? "新建活动" : "编辑活动"}</h5>
            <button className="admin-btn outline small" onClick={cancelEdit}><X size={14} /></button>
          </div>
          <div className="admin-hint">团价和折扣二选一：填了团价就不能选折扣，选了折扣团价会被清空。折扣可以选已有的，也可在下拉里选「新建折扣」当场填金额。</div>
          <div className="admin-form-grid">
            <label className="admin-field">
              <span>活动名称</span>
              <input value={form.activityName} onChange={(e) => updateField("activityName", e.target.value)} placeholder="如：春季拼团" />
            </label>
            <label className="admin-field">
              <span>额度包</span>
              <select value={form.goodsId} onChange={(e) => updateField("goodsId", e.target.value)}>
                <option value="">请选择</option>
                {goodsOptions.map((g) => (
                  <option key={g.goodsId} value={g.goodsId}>{g.goodsName}（￥{g.originalPrice}）</option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>团价{hasDiscountChoice ? "（已选折扣，留空）" : ""}</span>
              <input
                type="number"
                step="0.01"
                value={form.groupPrice}
                disabled={hasDiscountChoice}
                onChange={(e) => updateField("groupPrice", e.target.value)}
                placeholder={hasDiscountChoice ? "选了折扣就不填团价" : "如 36.90"}
              />
            </label>
            <label className="admin-field">
              <span>折扣{form.groupPrice ? "（已填团价，留空）" : ""}</span>
              <select
                value={form.discountId}
                disabled={Boolean(form.groupPrice)}
                onChange={(e) => updateField("discountId", e.target.value)}
              >
                <option value="">无折扣（走团价）</option>
                <option value="__new__">＋ 新建折扣（自选类型和金额）</option>
                {discountOptions.map((d) => (
                  <option key={d.discountId} value={d.discountId}>{d.discountName}（{d.marketPlan}/{d.marketExpr}）</option>
                ))}
              </select>
            </label>
            {form.discountId === "__new__" && (
              <div className="admin-inline-discount">
                <label className="admin-field">
                  <span>折扣类型</span>
                  <select value={inlineDiscount.plan} onChange={(e) => updateInlineDiscount("plan", e.target.value)}>
                    {Object.entries(DISCOUNT_PLAN_LABELS).map(([plan, label]) => (
                      <option key={plan} value={plan}>{label}</option>
                    ))}
                  </select>
                </label>
                <label className="admin-field">
                  <span>规则值</span>
                  <input value={inlineDiscount.expr} onChange={(e) => updateInlineDiscount("expr", e.target.value)} placeholder={DISCOUNT_PLAN_HINTS[inlineDiscount.plan] || ""} />
                </label>
                <label className="admin-field">
                  <span>折扣名称（可选）</span>
                  <input value={inlineDiscount.name} onChange={(e) => updateInlineDiscount("name", e.target.value)} placeholder={defaultDiscountName(inlineDiscount.plan, inlineDiscount.expr)} />
                </label>
              </div>
            )}
            <label className="admin-field">
              <span>成团人数</span>
              <input type="number" min="1" value={form.teamSize} onChange={(e) => updateField("teamSize", e.target.value)} />
            </label>
            <label className="admin-field">
              <span>目标人数</span>
              <input type="number" min="1" value={form.target} onChange={(e) => updateField("target", e.target.value)} />
            </label>
            <label className="admin-field">
              <span>有效时长(分钟)</span>
              <input type="number" min="1" value={form.validTime} onChange={(e) => updateField("validTime", e.target.value)} />
            </label>
            <label className="admin-field">
              <span>限参次数</span>
              <input type="number" min="1" value={form.takeLimitCount} onChange={(e) => updateField("takeLimitCount", e.target.value)} />
            </label>
            <label className="admin-field">
              <span>开始时间</span>
              <input type="datetime-local" value={form.startTime} onChange={(e) => updateField("startTime", e.target.value)} />
            </label>
            <label className="admin-field">
              <span>结束时间</span>
              <input type="datetime-local" value={form.endTime} onChange={(e) => updateField("endTime", e.target.value)} />
            </label>
            <label className="admin-field">
              <span>人群标签</span>
              <input value={form.tagId} onChange={(e) => updateField("tagId", e.target.value)} placeholder="留空表示不限" />
            </label>
            <label className="admin-field">
              <span>标签范围</span>
              <input value={form.tagScope} onChange={(e) => updateField("tagScope", e.target.value)} placeholder="如 WHITELIST" />
            </label>
            <label className="admin-field">
              <span>总库存{editing === "create" ? "" : "（编辑可调整）"}</span>
              <input type="number" min="0" value={form.totalStock} onChange={(e) => updateField("totalStock", e.target.value)} />
            </label>
          </div>
          <div className="admin-form-actions">
            <button className="admin-btn primary" onClick={submit} disabled={saving}>
              <Save size={14} /> {saving ? "保存中…" : "保存"}
            </button>
            <button className="admin-btn outline" onClick={cancelEdit}>取消</button>
          </div>
        </div>
      )}

      <div className="table-wrap compact">
        <table className="admin-table compact">
          <thead>
            <tr><th>活动</th><th>额度包</th><th>团价/折扣</th><th>库存</th><th>有效期</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            {activities.map((item) => (
              <tr key={item.activityId}>
                <td>{item.activityName || item.activityId}</td>
                <td className="mono">{item.goodsId}</td>
                <td>{item.discountId ? <span className="badge badge-blue">折扣 {item.discountId}</span> : `￥${item.groupPrice || 0}`}</td>
                <td className="mono">{item.totalStock ?? "-"}<span className="dim"> (可用 {item.availableStock ?? 0})</span></td>
                <td className="dim small">{item.startTime ? new Date(item.startTime).toLocaleDateString() : "-"} ~ {item.endTime ? new Date(item.endTime).toLocaleDateString() : "-"}</td>
                <td><span className={`badge ${item.enabled ? "badge-green" : "badge-gray"}`}>{item.enabled ? "启用" : "停用"}</span></td>
                <td className="action-cell">
                  <button className="admin-btn outline small" onClick={() => startEdit(item)}>编辑</button>
                  <button className="admin-btn outline small" onClick={() => toggleEnabled(item)}>{item.enabled ? "下架" : "上架"}</button>
                  <button className="admin-btn warning small" onClick={() => remove(item)}><Trash2 size={12} /> 删除</button>
                </td>
              </tr>
            ))}
            {activities.length === 0 && <tr><td colSpan="7" className="empty-cell">暂无活动配置，点击右上角新建</td></tr>}
          </tbody>
        </table>
      </div>

      <div className="admin-title-line ops-title" style={{ marginTop: 24 }}>
        <Tag size={16} />
        <h4>折扣管理</h4>
        <button className="admin-btn outline small" onClick={loadDiscounts} title="刷新"><RefreshCw size={14} /></button>
        <button className="admin-btn primary small" onClick={startDiscountCreate}>新建折扣</button>
      </div>

      {discountMessage && <div className="admin-tip">{discountMessage}</div>}

      {discountEditing && (
        <div className="admin-form-panel">
          <div className="admin-title-line">
            <h5>{discountEditing === "create" ? "新建折扣" : "编辑折扣"}</h5>
            <button className="admin-btn outline small" onClick={cancelDiscountEdit}><X size={14} /></button>
          </div>
          <div className="admin-hint">规则：直减填金额、满减填“满,减”、折扣填折扣率（0.8 表示八折）、N 元购填固定价格。</div>
          <div className="admin-form-grid">
            <label className="admin-field">
              <span>折扣名称</span>
              <input value={discountForm.discountName} onChange={(e) => updateDiscountField("discountName", e.target.value)} placeholder="如：满 30 减 7" />
            </label>
            <label className="admin-field">
              <span>折扣类型</span>
              <select value={discountForm.marketPlan} onChange={(e) => updateDiscountField("marketPlan", e.target.value)}>
                {Object.entries(DISCOUNT_PLAN_LABELS).map(([plan, label]) => (
                  <option key={plan} value={plan}>{label}</option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>规则值</span>
              <input value={discountForm.marketExpr} onChange={(e) => updateDiscountField("marketExpr", e.target.value)} placeholder={DISCOUNT_PLAN_HINTS[discountForm.marketPlan] || ""} />
            </label>
            <label className="admin-field">
              <span>描述</span>
              <input value={discountForm.discountDesc} onChange={(e) => updateDiscountField("discountDesc", e.target.value)} placeholder="可选" />
            </label>
            <label className="admin-field">
              <span>人群标签</span>
              <input value={discountForm.tagId} onChange={(e) => updateDiscountField("tagId", e.target.value)} placeholder="留空表示不限人群" />
            </label>
          </div>
          <div className="admin-form-actions">
            <button className="admin-btn primary" onClick={submitDiscount} disabled={discountSaving}>
              <Save size={14} /> {discountSaving ? "保存中…" : "保存"}
            </button>
            <button className="admin-btn outline" onClick={cancelDiscountEdit}>取消</button>
          </div>
        </div>
      )}

      <div className="table-wrap compact">
        <table className="admin-table compact">
          <thead>
            <tr><th>折扣</th><th>类型</th><th>规则</th><th>人群</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            {discounts.map((item) => (
              <tr key={item.discountId}>
                <td>{item.discountName || item.discountId}<div className="dim small">{item.discountDesc}</div></td>
                <td className="mono">{item.marketPlan}</td>
                <td className="mono">{item.marketExpr}</td>
                <td className="mono">{item.tagId || "不限"}</td>
                <td><span className={`badge ${item.enabled ? "badge-green" : "badge-gray"}`}>{item.enabled ? "启用" : "停用"}</span></td>
                <td className="action-cell">
                  <button className="admin-btn outline small" onClick={() => startDiscountEdit(item)}>编辑</button>
                  <button className="admin-btn outline small" onClick={() => toggleDiscountEnabled(item)}>{item.enabled ? "停用" : "启用"}</button>
                  <button className="admin-btn warning small" onClick={() => removeDiscount(item)}><Trash2 size={12} /> 删除</button>
                </td>
              </tr>
            ))}
            {discounts.length === 0 && <tr><td colSpan="6" className="empty-cell">暂无折扣，点击右上角新建（可选直减/满减/折扣/N 元购）</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  );
}
