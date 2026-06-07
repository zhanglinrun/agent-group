import { describe, expect, it } from "vitest";

import {
  summarizeTradeWorkspace,
  tradeSettlementHint,
  tradeOrderStatusLabel
} from "./tradeWorkspace";

describe("trade workspace summary", () => {
  it("marks paid group orders as waiting for settlement before quota grant", () => {
    const summary = summarizeTradeWorkspace({
      quota: {
        quotaBalance: 120,
        usedQuota: 30,
        frozenQuota: 5
      },
      flows: [{ bizType: "AGENT_CONSUME", quotaAmount: -2 }],
      orders: [
        {
          orderId: "O1001",
          marketType: 1,
          teamId: "T1001",
          orderStatus: "PAY_SUCCESS"
        },
        {
          orderId: "O1002",
          marketType: 1,
          teamId: "T1002",
          orderStatus: "GROUP_SETTLED"
        },
        {
          orderId: "O1003",
          marketType: 0,
          orderStatus: "DEAL_DONE"
        }
      ]
    });

    expect(summary.quotaBalance).toBe(120);
    expect(summary.groupOrders).toBe(2);
    expect(summary.directOrders).toBe(1);
    expect(summary.waitingGroupOrders).toBe(1);
    expect(summary.settledGroupOrders).toBe(1);
    expect(summary.consistencyHints[0]).toContain("等待成团");
  });

  it("formats common trade statuses", () => {
    expect(tradeOrderStatusLabel("PAY_WAIT")).toBe("待支付");
    expect(tradeOrderStatusLabel("PAY_SUCCESS")).toBe("已支付");
    expect(tradeOrderStatusLabel("GROUP_SETTLED")).toBe("已成团");
    expect(tradeOrderStatusLabel("WAIT_REFUND")).toBe("待退款");
    expect(tradeOrderStatusLabel("REFUNDED")).toBe("已退款");
    expect(tradeOrderStatusLabel("missing")).toBe("MISSING");
  });

  it("keeps quota grant rules explicit for trade settlement states", () => {
    expect(tradeSettlementHint({
      orderId: "O1001",
      marketType: 1,
      orderStatus: "PAY_SUCCESS"
    })).toMatchObject({
      key: "waiting-group",
      label: "等待成团",
      quotaGrantAllowed: false
    });

    expect(tradeSettlementHint({
      orderId: "O1002",
      marketType: 1,
      orderStatus: "GROUP_SETTLED"
    })).toMatchObject({
      key: "group-settled",
      label: "核对到账",
      quotaGrantAllowed: true
    });

    expect(tradeSettlementHint({
      orderId: "O1003",
      marketType: 0,
      orderStatus: "PAY_SUCCESS"
    })).toMatchObject({
      key: "direct-paid",
      label: "可到账",
      quotaGrantAllowed: true
    });

    expect(tradeSettlementHint({
      orderId: "O1004",
      marketType: 1,
      orderStatus: "REFUND_SUCCESS"
    })).toMatchObject({
      key: "refund-check",
      label: "核对退款",
      quotaGrantAllowed: false
    });

    expect(tradeSettlementHint({
      orderId: "O1005",
      marketType: 0,
      orderStatus: "WAIT_REFUND"
    })).toMatchObject({
      key: "refund-check",
      label: "核对退款",
      quotaGrantAllowed: false
    });
  });

});
