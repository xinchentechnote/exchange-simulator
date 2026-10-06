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

## 已知校验限制（gt-auto v0.2.0）

`ExecutionReport(200115)` 的 Receive 步骤 `verify_required=N`：gt-auto v0.2.0 的
`BinarySzseMessageCodec.JSONToStruct` 仅对 NewOrder/ExecutionConfirm 预填 ApplExtend，
而 Go 解码 ExecutionReport 时必然按 ApplId 生成非空 ApplExtend，导致 nil≠空结构误报。
修复 gt-auto（在 JSONToStruct 的 switch 中补 ExecutionReport/CancelReject 分支）后，
可将上述步骤改回 `Y` 以恢复全字段校验。200102 确认与 290008 撤单拒绝仍为全字段校验（Y）。
