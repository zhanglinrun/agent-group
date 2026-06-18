import { WorkspacePanelHeader } from "./WorkspacePanelHeader";

const IMAGE_QUALITY_OPTIONS = [
  { value: "auto", label: "自动" },
  { value: "high", label: "高" },
  { value: "medium", label: "中" },
  { value: "low", label: "低" }
];

const IMAGE_RATIO_PRESETS = [
  { id: "1:1", label: "1:1", aspectRatio: "1:1", size: "1024x1024", shape: "square" },
  { id: "3:2", label: "3:2", aspectRatio: "3:2", size: "1536x1024", shape: "landscape" },
  { id: "2:3", label: "2:3", aspectRatio: "2:3", size: "1024x1536", shape: "portrait" },
  { id: "4:3", label: "4:3", aspectRatio: "4:3", size: "1600x1200", shape: "landscape" },
  { id: "3:4", label: "3:4", aspectRatio: "3:4", size: "1200x1600", shape: "portrait" },
  { id: "16:9", label: "16:9", aspectRatio: "16:9", size: "1920x1080", shape: "landscape" },
  { id: "9:16", label: "9:16", aspectRatio: "9:16", size: "1080x1920", shape: "portrait" },
  { id: "1:1-2k", label: "1:1 2K", aspectRatio: "1:1", size: "2048x2048", shape: "square" },
  { id: "16:9-2k", label: "16:9 2K", aspectRatio: "16:9", size: "2560x1440", shape: "landscape" },
  { id: "9:16-2k", label: "9:16 2K", aspectRatio: "9:16", size: "1440x2560", shape: "portrait" },
  { id: "16:9-4k", label: "16:9 4K", aspectRatio: "16:9", size: "3840x2160", shape: "landscape" },
  { id: "9:16-4k", label: "9:16 4K", aspectRatio: "9:16", size: "2160x3840", shape: "portrait" },
  { id: "auto", label: "自动", aspectRatio: "auto", size: "auto", shape: "auto" }
];

const IMAGE_BATCH_OPTIONS = Array.from({ length: 10 }, (_, index) => index + 1);

export function ImageWorkspacePanel({ draft, onChange, hasReference, compact = false }) {
  const update = (field, value) => onChange({ ...draft, [field]: value });
  const selectedPreset = IMAGE_RATIO_PRESETS.find((preset) => (
    draft.ratioPreset === preset.id
    || (preset.aspectRatio === draft.aspectRatio && preset.size === draft.size)
  )) || IMAGE_RATIO_PRESETS.find((preset) => preset.id === "16:9-4k");
  const updatePreset = (preset) => onChange({
    ...draft,
    ratioPreset: preset.id,
    aspectRatio: preset.aspectRatio,
    size: preset.size
  });
  return (
    <section className={`image-workspace-panel ${compact ? "composer-image-settings" : ""}`}>
      <WorkspacePanelHeader
        className="image-workspace-head"
        title="图像参数"
        subtitle="模型、质量、比例和张数"
        trailing={<span className={hasReference ? "ready" : ""}>{hasReference ? "已有参考图" : "无参考图"}</span>}
      />
      <div className="image-workspace-grid">
        <label className="image-model-field">
          <span>模型</span>
          <input
            list="image-model-options"
            value={draft.model || "gpt-image-2"}
            onChange={(event) => update("model", event.target.value)}
            placeholder="gpt-image-2"
          />
          <datalist id="image-model-options">
            <option value="gpt-image-2" />
          </datalist>
        </label>
        <div className="image-option-group image-quality-field">
          <span>质量</span>
          <div className="image-segmented-options">
            {IMAGE_QUALITY_OPTIONS.map((option) => (
              <button
                type="button"
                key={option.value}
                className={(draft.quality || "auto") === option.value ? "active" : ""}
                onClick={() => update("quality", option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
        <div className="image-option-group image-ratio-field">
          <div className="image-option-title">
            <span>比例与尺寸</span>
            <em>{selectedPreset?.size === "auto" ? "自动" : selectedPreset?.size}</em>
          </div>
          <div className="image-ratio-options">
            {IMAGE_RATIO_PRESETS.map((preset) => (
              <button
                type="button"
                key={preset.id}
                className={selectedPreset?.id === preset.id ? "active" : ""}
                onClick={() => updatePreset(preset)}
              >
                <i className={`ratio-icon ${preset.shape}`} />
                <span>{preset.label}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="image-option-group image-batch-field">
          <span>生成张数</span>
          <div className="image-batch-options">
            {IMAGE_BATCH_OPTIONS.map((count) => (
              <button
                type="button"
                key={count}
                className={Number(draft.batchCount || 1) === count ? "active" : ""}
                onClick={() => update("batchCount", count)}
              >
                {count} 张
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
