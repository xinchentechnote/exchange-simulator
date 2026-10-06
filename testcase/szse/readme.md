# SZSE gt-auto 端到端用例说明

## 文件

- `gw-auto-szse.toml`：模拟器连接配置（binary-szse，localhost:9011）
- `szse_test_case.csv`：用例编排（logon / 下单成交 / 撤单成功 / 撤单拒绝）
- `szse_<msgType>.csv`：各消息类型的字段模板，列名须与 fin-proto-go 的 json tag 一致
  （1=Logon、100101=NewOrder、200102=ExecutionConfirm、200115=ExecutionReport、190007=OrderCancelRequest、290008=CancelReject）

## 数据约定

- 账户取自 `data/szse/account_data.csv`（20001-20010），证券取自 `data/szse/symbol_data.csv`（如 000001）
- `ClearingFirm` 在 NewOrder 中为 **2 字符定长字段**（协议定义），超长会被截断
- `TransactTime` 格式 `yyyymmddHHMMSS`；回报中的 TradeDate 等字段由服务端按此推导
- 成交回报 `OrdStatus` 按 OrdStatus 字典取值：部分成交=1、全部成交=2（本用例场景为全部成交=2）

## gt-auto 版本要求（≥ v0.2.1）

`ExecutionReport(200115)` 的 Receive 步骤做全字段校验（`verify_required=Y`），依赖 gt-auto
v0.2.1 的修复：SZSE codec 构造期望消息时按 ApplID 预填 `ExecutionReport.ApplExtend`
（与解码端行为一致；v0.2.0 仅覆盖 NewOrder/ExecutionConfirm，导致 nil≠空结构误报）。

安装修复版：

```shell
go install github.com/xinchentechnote/gt-auto/cmd/gt-auto@v0.2.1
# 或从源码：cd ~/workspace/gt-auto && go install ./cmd/gt-auto
```

若使用 v0.2.0，需将本目录 `szse_test_case.csv` 中 200115 的 Receive 步骤改为 `verify_required=N`。
