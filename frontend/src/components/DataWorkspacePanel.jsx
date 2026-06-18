import { BookOpen, Loader2 } from "lucide-react";

import { buildWorkspaceDataCatalogDraft } from "../workspaceServices";
import { WorkspacePanelHeader } from "./WorkspacePanelHeader";

export function DataWorkspacePanel({ draft, onChange, catalog, catalogLoading, catalogError }) {
  const models = Array.isArray(catalog?.models) ? catalog.models : [];
  const update = (field, value) => onChange({ ...draft, [field]: value });
  const clear = () => onChange({
    rowsJson: "",
    columnsText: "",
    modelCodeText: "",
    schemaInfoJson: "",
    businessKnowledge: ""
  });
  const applyCatalog = () => {
    onChange({ ...draft, ...buildWorkspaceDataCatalogDraft(catalog) });
  };
  return (
    <section className="data-workspace-panel">
      <WorkspacePanelHeader
        className="data-workspace-head"
        title="数据上下文"
        subtitle="结构化数据会随下一次数据问答一起提交"
        trailing={(
        <div>
          <button type="button" onClick={applyCatalog} disabled={catalogLoading || models.length === 0}>
            {catalogLoading ? <Loader2 size={14} className="spin" /> : <BookOpen size={14} />}
            使用目录
          </button>
          <button type="button" onClick={clear}>清空</button>
        </div>
        )}
      />
      {catalogError && <div className="data-workspace-catalog-error">{catalogError}</div>}
      {models.length > 0 && (
        <div className="data-workspace-catalog">
          {models.map((model) => (
            <span key={model.modelCode || model.tableName}>
              <b>{model.displayName || model.modelCode}</b>
              {model.modelCode || model.tableName}
            </span>
          ))}
        </div>
      )}
      <div className="data-workspace-grid">
        <label>
          <span>字段列表</span>
          <input
            value={draft.columnsText}
            onChange={(event) => update("columnsText", event.target.value)}
            placeholder="pay_status, count, amount"
          />
        </label>
        <label>
          <span>模型编码</span>
          <input
            value={draft.modelCodeText}
            onChange={(event) => update("modelCodeText", event.target.value)}
            placeholder="trade_order, quota_flow"
          />
        </label>
        <label className="wide">
          <span>业务知识</span>
          <textarea
            value={draft.businessKnowledge}
            onChange={(event) => update("businessKnowledge", event.target.value)}
            placeholder="补充口径、枚举和业务规则"
          />
        </label>
        <label className="wide">
          <span>表格行 JSON</span>
          <textarea
            value={draft.rowsJson}
            onChange={(event) => update("rowsJson", event.target.value)}
            placeholder='[{"pay_status":"PAY_SUCCESS","count":12}]'
          />
        </label>
        <label className="wide">
          <span>表结构 JSON</span>
          <textarea
            value={draft.schemaInfoJson}
            onChange={(event) => update("schemaInfoJson", event.target.value)}
            placeholder='[{"table":"trade_order","columns":["pay_status","order_status"]}]'
          />
        </label>
      </div>
    </section>
  );
}
