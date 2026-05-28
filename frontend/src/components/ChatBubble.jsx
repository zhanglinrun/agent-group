import { useState } from 'react';
import ProductCard from './ProductCard';
import { Sparkles, Compass, ChevronDown, ChevronRight, FileText } from 'lucide-react';

export default function ChatBubble({ message, onDirectBuy, onGroupBuy }) {
  const isUser = message.role === 'user';
  const [showCapsuleDetail, setShowCapsuleDetail] = useState(false);

  return (
    <div className={`message-row ${isUser ? 'user' : 'ai'}`}>
      {!isUser && (
        <div className="avatar ai-avatar">
          <Sparkles size={16} />
        </div>
      )}

      <div className="bubble">
        {/* Doubao style thinking / references capsules */}
        {!isUser && message.references && message.references.length > 0 && (
          <div style={{marginBottom: '12px'}}>
            <div className="capsule" onClick={() => setShowCapsuleDetail(!showCapsuleDetail)}>
              <Compass size={14} className="capsule-icon" />
              <span>检索了 {message.references.length} 个知识片段</span>
              {showCapsuleDetail ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </div>

            {showCapsuleDetail && (
              <div style={{marginTop: '8px'}}>
                {message.references.map((ref, idx) => (
                  <div key={idx} className="capsule-content">
                    <div style={{display: 'flex', alignItems: 'center', gap: '4px', fontWeight: 600, marginBottom: '4px', color: '#333'}}>
                      <FileText size={12} /> {ref.title}
                    </div>
                    <div>{ref.text}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {message.text && (
          <div style={{ whiteSpace: 'pre-wrap' }}>{message.text}</div>
        )}

        {message.products && message.products.map(product => (
          <ProductCard
            key={product.id}
            product={product}
            onDirectBuy={onDirectBuy}
            onGroupBuy={onGroupBuy}
          />
        ))}

      </div>
    </div>
  );
}
