import { useEffect, useState } from "react";
import { Settings, X } from "lucide-react";
import { getModelConfig } from "../../services/api";

export default function ModelConfigDialog({ config, onSave, onClose }) {
  const [draft, setDraft] = useState(() => getModelConfig());

  useEffect(() => {
    setDraft({ ...getModelConfig(), ...config });
  }, [config]);

  const update = (field, value) => {
    setDraft((prev) => ({ ...prev, [field]: value }));
  };

  const updateTextBaseUrl = (value) => {
    setDraft((prev) => ({ ...prev, baseUrl: value, textBaseUrl: value }));
  };

  const updateTextApiKey = (value) => {
    setDraft((prev) => ({ ...prev, apiKey: value, textApiKey: value }));
  };

  const updateTextModel = (value) => {
    setDraft((prev) => ({ ...prev, model: value, textModel: value }));
  };

  const submit = (event) => {
    event.preventDefault();
    onSave(draft);
  };

  return (
    <div className="modal-overlay model-config-overlay">
      <form className="model-config-dialog" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={onClose}><X size={18} /></button>
        <div className="model-config-head">
          <Settings size={20} />
          <h3>模型配置</h3>
        </div>
        <label className="model-config-toggle">
          <input
            type="checkbox"
            checked={Boolean(draft.enabled)}
            onChange={(event) => update("enabled", event.target.checked)}
          />
          <span>使用自定义模型</span>
        </label>
        <section className="model-config-section">
          <strong>文本模型</strong>
          <label>
            <span>API 地址</span>
            <input
              value={draft.textBaseUrl || draft.baseUrl || ""}
              onChange={(event) => updateTextBaseUrl(event.target.value)}
              placeholder="https://dashscope.aliyuncs.com/compatible-mode"
              disabled={!draft.enabled}
            />
          </label>
          <label>
            <span>API 密钥</span>
            <input
              value={draft.textApiKey || draft.apiKey || ""}
              onChange={(event) => updateTextApiKey(event.target.value)}
              type="password"
              placeholder={draft.textKeyMasked || draft.keyMasked ? "留空则继续使用已保存密钥" : "sk-..."}
              disabled={!draft.enabled}
            />
            {(draft.textKeyMasked || draft.keyMasked) && (
              <em className="model-config-key-mask">已保存：{draft.textKeyMasked || draft.keyMasked}</em>
            )}
          </label>
          <label>
            <span>默认文本模型</span>
            <input
              value={draft.textModel || draft.model || ""}
              onChange={(event) => updateTextModel(event.target.value)}
              placeholder="qwen3.7-plus"
              disabled={!draft.enabled}
            />
          </label>
        </section>
        <section className="model-config-section">
          <strong>图像模型</strong>
          <label>
            <span>API 地址</span>
            <input
              value={draft.imageBaseUrl || ""}
              onChange={(event) => update("imageBaseUrl", event.target.value)}
              placeholder="https://api.openai.com"
              disabled={!draft.enabled}
            />
          </label>
          <label>
            <span>API 密钥</span>
            <input
              value={draft.imageApiKey || ""}
              onChange={(event) => update("imageApiKey", event.target.value)}
              type="password"
              placeholder={draft.imageKeyMasked ? "留空则继续使用已保存密钥" : "sk-..."}
              disabled={!draft.enabled}
            />
            {draft.imageKeyMasked && <em className="model-config-key-mask">已保存：{draft.imageKeyMasked}</em>}
          </label>
          <label>
            <span>默认图像模型</span>
            <input
              value={draft.imageModel || ""}
              onChange={(event) => update("imageModel", event.target.value)}
              placeholder="gpt-image-2"
              disabled={!draft.enabled}
            />
          </label>
        </section>
        <div className="model-config-actions">
          <button type="button" onClick={onClose}>取消</button>
          <button type="submit" className="primary">保存</button>
        </div>
      </form>
    </div>
  );
}
