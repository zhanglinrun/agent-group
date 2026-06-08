import { timelineItemStatus } from "./agentTimeline";

export function assistantMessageCanRetry(message: any = {}): boolean {
  if (message.role !== "assistant") return false;
  const content = String(message.content || "");
  if (/(请求出错|处理失败|生成失败|运行失败|服务暂不可用|header parser received no bytes)/i.test(content)) {
    return true;
  }
  return (message.timeline || []).some((item: any) => {
    const status = timelineItemStatus(item);
    return item?.type === "error" || status === "error" || status === "blocked";
  });
}

export function retryFilesFromUserMessage(message: any = {}): any[] {
  return (message.files || [])
    .filter((file: any) => file?.fileId)
    .map((file: any) => ({
      ...file,
      clientId: file.clientId || file.fileId,
      fileType: file.fileType || file.contentType || "",
      contentType: file.contentType || file.fileType || "",
      status: "parsed"
    }));
}
