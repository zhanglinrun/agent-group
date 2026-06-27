import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { TradeWorkspacePanel } from "./TradeWorkspacePanel";

describe("TradeWorkspacePanel", () => {
  it("renders quota, group order hints, and recent quota flows", () => {
    const html = renderToStaticMarkup(createElement(TradeWorkspacePanel, {
      summary: {
        quotaBalance: 12.5,
        usedQuota: 3,
        groupOrders: 1,
        waitingGroupOrders: 1,
        totalOrders: 1,
        consistencyHints: ["存在支付成功但等待成团的拼团单，额度必须等成团后发放。"],
        recentOrders: [
          {
            orderId: "T202606180001",
            productName: "基础 Agent 额度包",
            orderStatus: "PAY_SUCCESS",
            marketType: 1,
            payAmount: 19.9
          }
        ],
        recentFlows: [
          {
            flowId: "F1",
            bizType: "CONSUME",
            bizId: "TASK-1",
            quotaAmount: -5.5
          }
        ]
      },
      loading: false,
      onRefresh: () => {},
      onOpenRecharge: () => {},
      onOpenOrderRecords: () => {}
    }));

    expect(html).toContain("交易闭环看板");
    expect(html).toContain("12.50 点");
    expect(html).toContain("待成团");
    expect(html).toContain("1 单");
    expect(html).toContain("等待成团");
    expect(html).toContain("拼团支付成功只表示名额已支付，暂不能发放额度。");
    expect(html).toContain("￥19.90");
    expect(html).toContain("-5.50");
  });
});
