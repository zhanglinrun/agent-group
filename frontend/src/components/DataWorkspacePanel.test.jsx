import { describe, expect, it } from "vitest";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { DataWorkspacePanel } from "./DataWorkspacePanel";

describe("DataWorkspacePanel", () => {
  it("renders catalog models, errors, and draft values", () => {
    const html = renderToStaticMarkup(createElement(DataWorkspacePanel, {
      draft: {
        rowsJson: '[{"pay_status":"PAY_SUCCESS"}]',
        columnsText: "pay_status, amount",
        modelCodeText: "trade_order",
        schemaInfoJson: '[{"table":"trade_order"}]',
        businessKnowledge: "拼团支付成功不等于额度到账"
      },
      onChange: () => {},
      catalog: {
        models: [
          { modelCode: "trade_order", displayName: "交易订单", tableName: "trade_order" }
        ]
      },
      catalogLoading: false,
      catalogError: "目录读取失败"
    }));

    expect(html).toContain("数据上下文");
    expect(html).toContain("目录读取失败");
    expect(html).toContain("交易订单");
    expect(html).toContain("trade_order");
    expect(html).toContain("pay_status, amount");
    expect(html).toContain("拼团支付成功不等于额度到账");
  });
});
