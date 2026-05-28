import { ChevronRight } from 'lucide-react';

const FALLBACK_IMAGE_URL = "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=760&q=82";

export default function ProductCard({ product, onDirectBuy, onGroupBuy }) {
  if (!product) return null;

  const originPrice = Number(product.originPrice) || 0;
  const groupPrice = Number(product.groupPrice) || 0;
  const productImageUrl = product.imageUrl && !product.imageUrl.includes("example.com/")
    ? product.imageUrl
    : FALLBACK_IMAGE_URL;

  const fakeAvatars = [
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix&backgroundColor=f0f0f0",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka&backgroundColor=e0e0e0"
  ];

  return (
    <article className="product-card">
      <div className="pdd-card-header">
        <span className="pdd-card-header-icon">百亿补贴</span>
        <span>正品发票 · 假一赔十 <ChevronRight size={12} style={{display:'inline', verticalAlign:'middle'}}/></span>
      </div>

      <div className="product-image-wrap">
        <img
          src={productImageUrl}
          alt={product.name}
          className="product-image"
        />
        <div className="stock-progress">
          {product.stockProgress || "宸叉姠85%"}
          <div className="stock-bar">
            <div className="stock-bar-fill"></div>
          </div>
        </div>
      </div>

      <div className="product-info">
        <h3 className="product-title">{product.name}</h3>

        <div className="pdd-tag-row">
          <span className="pdd-tag green">退货包运费</span>
          <span className="pdd-tag">极速退款</span>
          <span className="pdd-tag">七天无理由</span>
        </div>

        <div className="product-price-row">
          <span className="price-symbol">￥</span>
          <span className="price-group">{groupPrice}</span>
          <span className="price-origin">￥{originPrice}</span>
          <span style={{fontSize: '0.75rem', color: '#999', marginLeft: 'auto'}}>已拼 {product.soldCount || product.teamSize * 153} 件</span>
        </div>

        <div className="product-actions">
          <button className="btn-direct" onClick={() => onDirectBuy(product)}>
            <span className="btn-title">￥{originPrice}</span>
            <span className="btn-sub">单独购买</span>
          </button>
          <button className="btn-group" onClick={() => onGroupBuy(product)}>
            <span className="btn-title">
              <div className="avatars-inline">
                {fakeAvatars.map((src, i) => <img key={i} src={src} alt="user" />)}
              </div>
              ￥{groupPrice}
            </span>
            <span className="btn-sub">{product.teamSize}人团 · 发起拼单</span>
          </button>
        </div>
      </div>
    </article>
  );
}
