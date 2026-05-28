import { Plus, MessageSquare } from 'lucide-react';

export default function Sidebar({
  sessions,
  currentSessionId,
  onNewSession,
  onSelectSession
}) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        AI 拼团导购助手
      </div>

      <button className="new-chat-btn" onClick={onNewSession} style={{marginTop: '16px'}}>
        <Plus size={16} />
        开启新对话
      </button>

      <div className="history-list">
        <div style={{fontSize: '0.8rem', color: '#999', margin: '8px 12px'}}>今天</div>
        {sessions.map(session => (
          <div
            key={session.id}
            className={`history-item ${session.id === currentSessionId ? 'active' : ''}`}
            onClick={() => onSelectSession(session.id)}
          >
            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
              <MessageSquare size={14} color="#666" />
              <span style={{overflow: 'hidden', textOverflow: 'ellipsis'}}>{session.title}</span>
            </div>
          </div>
        ))}
      </div>

      <div className="sidebar-footer">
        <a href="/admin">进入管理员后台</a>
      </div>
    </aside>
  );
}
