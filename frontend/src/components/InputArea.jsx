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
            <button type="button" className="image-remove" onClick={() => setImage(null)} aria-label="绉婚櫎鍥剧墖"><X size={12}/></button>
          </div>
        )}

        <div style={{display: 'flex', alignItems: 'flex-end', gap: '8px', width: '100%'}}>
          <button
            type="button"
            className="action-btn"
            onClick={() => fileInputRef.current?.click()}
            title="涓婁紶鍥剧墖"
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
            placeholder="杈撳叆鎮ㄧ殑闂..."
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
        AI 鐢熸垚鐨勫唴瀹瑰彲鑳藉瓨鍦ㄤ笉鍑嗙‘锛岃娉ㄦ剰鐢勫埆銆?      </div>
    </div>
  );
}
