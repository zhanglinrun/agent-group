import { useEffect, useRef } from "react";
import { Activity, BookOpen, CheckCircle, Search, Wrench } from "lucide-react";

export default function AgentTracePanel({ traces }) {
  const contentRef = useRef(null);

  useEffect(() => {
    if (contentRef.current) {
      contentRef.current.scrollTop = contentRef.current.scrollHeight;
    }
  }, [traces]);

  const getIcon = (stage) => {
    switch (stage) {
      case "工具": return <Wrench size={14} />;
      case "检索": return <Search size={14} />;
      case "引用": return <BookOpen size={14} />;
      case "自检": return <CheckCircle size={14} />;
      default: return <Activity size={14} />;
    }
  };

  return (
    <aside className="trace-panel">
      <div className="trace-header">
        <Activity size={16} color="var(--ai-icon-bg)" />
        <span>思考与执行链路</span>
      </div>

      <div className="trace-content" ref={contentRef}>
        {traces.length === 0 ? (
          <div className="trace-empty">暂无执行记录</div>
        ) : (
          traces.map((trace) => (
            <div key={trace.id} className={`trace-card ${trace.stage === "引用" ? "reference" : "active"}`}>
              <div className={trace.stage === "引用" ? "trace-card-head reference" : "trace-card-head"}>
                {getIcon(trace.stage)}
                <span className="trace-title">{trace.stage}</span>
                <span className="trace-time">{trace.time}</span>
              </div>
              <div className="trace-text">{trace.text}</div>
            </div>
          ))
        )}
      </div>
    </aside>
  );
}
