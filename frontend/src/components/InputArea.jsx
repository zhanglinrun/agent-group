import { useState, useRef } from 'react';
import { Send, Square, Paperclip, X } from 'lucide-react';

export default function InputArea({ onSend, onStop, isStreaming }) {
  const [text, setText] = useState('');
  const [image, setImage] = useState(null); // { url, name }
  const fileInputRef = useRef(null);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file && file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onload = (e) => {
        setImage({ url: e.target.result, name: file.name });
      };
      reader.readAsDataURL(file);
    }
    e.target.value = null; // reset
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if ((text.trim() || image) && !isStreaming) {
      onSend(text.trim(), image?.url, image?.name);
      setText('');
      setImage(null);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  return (
    <div className="input-area-wrapper">
      <div className="input-container" style={{flexDirection: 'column', alignItems: 'stretch'}}>
        {image && (
          <div className="image-preview-wrap">
            <img src={image.url} alt="preview" />
            <button type="button" className="image-remove" onClick={() => setImage(null)} aria-label="移除图片"><X size={12}/></button>
          </div>
        )}

        <div style={{display: 'flex', alignItems: 'flex-end', gap: '8px', width: '100%'}}>
          <button
            type="button"
            className="action-btn"
            onClick={() => fileInputRef.current?.click()}
            title="上传图片"
          >
            <Paperclip size={18} />
          </button>
          <input
            type="file"
            ref={fileInputRef}
            accept="image/*"
            style={{display: 'none'}}
            onChange={handleFileChange}
          />

          <textarea
            id="guide-input"
            name="guide-input"
            className="chat-input"
            placeholder="输入你的问题..."
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
          />

          {isStreaming ? (
            <button type="button" className="send-button" onClick={onStop}>
              <Square size={14} fill="currentColor" />
            </button>
          ) : (
            <button type="submit" className="send-button" onClick={handleSubmit} disabled={!text.trim() && !image}>
              <Send size={16} style={{ marginLeft: '2px' }} />
            </button>
          )}
        </div>
      </div>
      <div style={{ textAlign: 'center', marginTop: '12px', fontSize: '0.75rem', color: '#b3b3b3' }}>
        AI 生成的内容可能不准确，请注意甄别。
      </div>
    </div>
  );
}
