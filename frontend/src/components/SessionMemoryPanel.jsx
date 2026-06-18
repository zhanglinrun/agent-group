import { hasSessionMemory } from "../appRuntime";
import { WorkspacePanelHeader } from "./WorkspacePanelHeader";

export function SessionMemoryPanel({ memory }) {
  if (!hasSessionMemory(memory)) return null;
  const runs = memory.runs || [];
  const observations = memory.toolObservations || [];
  const artifacts = memory.reusableArtifacts || [];
  const latestArtifact = artifacts[0];
  return (
    <section className="session-memory-panel">
      <WorkspacePanelHeader
        className="session-memory-head"
        title="会话记忆"
        subtitle={memory.summary || "会话中的关键上下文会显示在这里"}
      />
      <div className="session-memory-stats">
        <span>运行 <b>{runs.length}</b></span>
        <span>工具观察 <b>{observations.length}</b></span>
        <span>可复用产物 <b>{artifacts.length}</b></span>
      </div>
      {latestArtifact && (
        <div className="session-memory-artifact">
          可复用：{latestArtifact.title || latestArtifact.fileName || latestArtifact.artifactId}
        </div>
      )}
    </section>
  );
}
