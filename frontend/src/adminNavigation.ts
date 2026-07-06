export type AdminBackendOwner = "agent-group-trade" | "reactor-agent";

export interface AdminMenuItem {
  key: string;
  label: string;
  backendOwner: AdminBackendOwner;
}

export interface AdminMenuGroup {
  name: string;
  label: string;
  items: AdminMenuItem[];
}

export const ADMIN_MENU_GROUPS: AdminMenuGroup[] = [
  {
    name: "groupbuy",
    label: "拼团运营",
    items: [
      { key: "activity", label: "活动管理", backendOwner: "agent-group-trade" },
      { key: "channel", label: "渠道与库存", backendOwner: "agent-group-trade" }
    ]
  },
  {
    name: "agent",
    label: "智能体配置",
    items: [
      { key: "llmConfig", label: "模型配置", backendOwner: "reactor-agent" },
      { key: "skills", label: "技能 Skills", backendOwner: "reactor-agent" },
      { key: "mcp", label: "MCP 服务", backendOwner: "reactor-agent" }
    ]
  },
  {
    name: "trade",
    label: "交易管理",
    items: [
      { key: "order", label: "订单与核查", backendOwner: "agent-group-trade" },
      { key: "refund", label: "售后退款", backendOwner: "agent-group-trade" },
      { key: "tradeOps", label: "交易运维", backendOwner: "agent-group-trade" }
    ]
  },
  {
    name: "ops",
    label: "运维配置",
    items: [
      { key: "rules", label: "运营规则", backendOwner: "agent-group-trade" }
    ]
  }
];

export function adminMenuItem(key: string): AdminMenuItem | null {
  for (const group of ADMIN_MENU_GROUPS) {
    const item = group.items.find((candidate) => candidate.key === key);
    if (item) return item;
  }
  return null;
}

export function adminMenuKeysByOwner(owner: AdminBackendOwner): string[] {
  return ADMIN_MENU_GROUPS.flatMap((group) => group.items)
    .filter((item) => item.backendOwner === owner)
    .map((item) => item.key);
}
