# 待办清单（Backlog）

> 基于 2026-09-19 对 main 分支（`5bf34a1`）的全量代码 review 整理，并于同日完成第一轮修复。
> 优先级定义：**P0** = 阻断性缺陷/风险；**P1** = 功能缺口或重要缺陷；**P2** = 重构、工程化与卫生问题；**P3** = 长期方向。
> 状态标记：✅ 已修复（含回归测试）；🔧 部分修复；⬜ 待办。
> 设计文档见 [DESIGN.md](./DESIGN.md)。

---

## 第一轮修复摘要（2026-09-19）

全部 6 项 P0 缺陷、P1 中的 7 项缺陷、P2 中的大部分卫生问题已修复，并补充 38 个单元测试与 Dockerfile。
验证方式：`mvn test`（38/38 通过）+ JDK 8 实机启动 + gt-auto SSE 端到端回归（logon/confirm/report 全部 ✅）+ HTTP 冒烟（非法请求 400、reservePrice 缺省回落）。

---

## P0 阻断性缺陷

- [x] **P0-1 运行时仅能在 JDK 8 下启动** 🔧（部分处理）
  exchange-core 0.5.3 → chronicle-bytes 2.19.1 仅兼容 JDK 8（Maven Central 上无更新版本，升级路线不通）。
  已完成：Dockerfile 固化 JDK 8 运行时、readme/设计文档明确约束、提供 Zulu 8 验证记录。
  待办：CI 固定 JDK 8 构建；如需 JDK 11+ 需等待或自行维护 exchange-core 的 chronicle 升级。

- [x] **P0-2 心跳空闲超时检测是死代码** ✅
  `IdleStateHandler` 已移到 ConnectionHandler 之前（两个 Initializer 均调整顺序）；登录时改用 `pipeline.replace` 重建。
  回归测试：`SseBinServerPipelineTest.idleHandlerMustBeBeforeConnectionHandler`（管线顺序防回归）、`idleTimeoutShouldCloseConnection`/`heartbeatShouldResetIdleCounter`（三振断连与计数重置）。
  说明：EmbeddedChannel 的虚拟时钟对 IdleStateHandler 排期任务不生效，因此超时测试采用直接注入 IdleStateEvent 的方式。

- [x] **P0-3 HTTP 接口参数校验完全失效** ✅
  移除 `jakarta.validation-api 3.0.2 + hibernate-validator 8`，改用 `spring-boot-starter-validation`（Boot 2.7 对应 javax 系），注解全部换为 `javax.validation.*`；同时修正 `@Min(0)` 为 `@Positive`，orderId 增加 `@Pattern(\\d+)`（杜绝 Long.parseLong 异常）。
  回归测试：`OrderRequestValidationTest`（含防"误升回 jakarta 导致校验静默失效"的断言）；实机冒烟：非法请求返回 400（修复前为 200）。

- [x] **P0-4 撮合事件回调 NPE 风险** ✅
  SSE/SZSE 的 `tradeEvent` 对 taker/maker 的 cache 查询全部判空：未命中记 warn 并跳过对应回报，不再打断事件处理线程。
  回归测试：`SzseBinServerTest.tradeEventWithUnknownOrdersShouldNotThrow`。

- [x] **P0-5 HTTP 下单 reservePrice 缺省 NPE** ✅
  `ExchangeServiceImpl`：reservePrice 缺省回落到 price（与二进制通道转换器一致）。
  实机冒烟：无 reservePrice 的合法请求返回 200/true（修复前 NPE→false）。

- [x] **P0-6 SZSE 未校验登录态** ✅
  心跳/业务报文分支统一走 `isLogon()` 检查，未登录断开连接（与 SSE 对齐）；exceptionCaught 增加 `ctx.close()`。

## P1 重要缺陷 / 功能缺口

- [x] **P1-7 订单缓存只增不减** ✅
  三条清理路径：申报被拒（确认 ExecType=8 后）、taker 全部成交（takeOrderCompleted）、连接断开（`onChannelInactive` 按会话批量清理）。
  maker 单的部分成交残留（无终态信号）仍会保留到断连，属可接受残余。
  回归测试：`SseBinServerTest.rejectedCommandResultShouldSendRejectAndEvictCache` / `channelInactiveShouldEvictSessionOrders` 等。

- [ ] **P1-8 撤单全链路未实现** ⬜（功能开发，未启动）
  SSE `ORDER_CANCEL(61)`、SZSE `OrderCancelRequest` 转换器 → ApiCancelOrder → 撤单确认（ExecType=4）→ CancelReject。

- [x] **P1-9 未覆盖的委托结果码不下发任何回执** ✅
  SSE/SZSE Confirm 转换器的 default 分支改为按申报拒绝（ExecType=8）兜底下发。
  回归测试：`SseConfirmConvertorTest.unmappedCodeShouldFallbackToReject`、`SzseConfirmConvertorTest.unmappedCodeShouldFallbackToReject`。

- [x] **P1-10 SSE 下行 MsgSeqNum 全局计数** ✅
  序号改为按会话（channel attr）独立自增；SZSE 协议帧无序号字段，无需处理。
  回归测试：`SseBinServerTest.msgSeqNumShouldBePerSession`（双会话序号独立）。

- [ ] **P1-11 成交回报字段语义需对照协议规范核对** ⬜（需协议规范，未启动）
  未修复项：taker 回报 lastPx 取委托价而非成交价（taker 一次成交可能对应多笔 trades，需按规范决定取价规则或逐笔下发）；SZSE leavesQty 未扣减已成交；ordStatus 恒 "0" 未区分部分/全部成交；`tradeDate = transactTime/1e6` 的正确性；CumQty/AvgPx/手续费未填。
  已顺带完成：ExecType 字符串字面量统一为 `common.ExecType` 常量（含新增 TRADE="F"）。

- [ ] **P1-12 HTTP 模块与协议模块撮合核心割裂** ⬜（设计决策，未启动）
  `ExchangeConfig` 仍为 HTTP 独立核心（硬编码演示数据）。已加 TODO 注释标明定位，待决策：打通市场/支持指定市场/移除。
  已顺带完成：事件回调的 `System.out.println` 全部改为日志。

- [x] **P1-13 Netty 端口绑定失败不 fail-fast** ✅
  `bind().sync()`，失败抛 `IllegalStateException` 终止启动。实机验证：占用端口时应用启动失败退出。

- [x] **P1-14 基础数据加载失败静默** ✅
  文件缺失/列数不足/解析异常均抛 `IllegalStateException`（含文件名与行号）；加载成功输出统计日志。
  回归测试：`DataLoadServiceTest`；实机验证：空目录下启动失败并提示 "Account data file not found"。

- [x] **P1-15 重复 Logon 导致 pipeline 操作抛异常** ✅
  登录时改用 `pipeline.replace`（替代 remove+add），重复 Logon 安全；顺带移除了 `HeartBtIntUtil.isMin` 特例（统一按协商值夹取后重建），SZSE 心跳间隔同样夹取到 [5,60] 秒。

## P2 重构与工程卫生

- [x] **P2-16 抽取公共模块** 🔧
  已完成：新建 `common` 包（`Constant`/`CommandWrapper`/`ExecType`/`HeartBtIntUtil`），`szse` 对 `sse` 的反向依赖全部消除；`szse.ExecType`（继承 sse.ExecType）与 `sse.ExecType` 合并为 `common.ExecType`。
  未做：两个 BinServer/ConnectionHandler/Config 的模板化抽象（重复度高但重构面大，暂缓）。

- [x] **P2-17 消除误导性注解与残留代码** ✅
  转换器 `@Component` 移除（注明手动注册）；`@Log4j2`/`@Slf4j` 统一为 `@Slf4j`；`System.out.println` 全部清理；未使用 import 清理；`checkImmediateFill` 死代码删除。

- [x] **P2-18 SZSE 消息类型魔法数字常量化** ✅
  新增 `szse/SzseMsgType`（HEARTBEAT=3、EXECUTION_CONFIRM=200102、EXECUTION_REPORT=200115），全部替换。

- [x] **P2-19 HTTP 接口语义修正** 🔧
  已完成：javadoc 与实现对齐（异步受理）；orderId 非数字由 `@Pattern` 拦截（400）；`@Positive` 语义修正。
  未做：同步等待订单状态（原 javadoc 描述的"等 3 秒返回状态"实现）；`OrderResponse`/`OrderResult` DTO 接入。

- [x] **P2-20 测试与交付工程化** 🔧
  已完成：新增 10 个测试类 38 个用例（转换器映射、确认回执、会话层管线、按会话序号、缓存清理、CSV fail-fast、参数校验）；pom 补 surefire 2.22.2（此前 JUnit 5 用例根本不会执行）；Dockerfile（JDK 8 基础镜像，含 data 目录）。
  未做：SZSE 协议回归用例（gt-auto testcase）；CI 流水线。

- [x] **P2-21 其他小项** ✅
  `HeartBtIntUtil.isMin` 移除；SZSE Initializer 改为 public 与 SSE 一致；`setApplExtend` 内聚到 SZSE 转换器；`cache`/`port` 等 field final 化；初始化器泛型放宽为 `ChannelInitializer<Channel>`（兼容 EmbeddedChannel 测试，生产行为不变）。
  未做：`IApiCommandConverter.get()` raw type 泛型化（Java 泛型协变限制，收益低）。

## P3 长期方向（可选）

- [ ] **P3-1 行情能力**：`orderBook` 事件实现快照/增量行情广播（SSE `PLATFORM_STATE(209)` 等报文已在协议库定义）。
- [ ] **P3-2 状态持久化与恢复**：重启丢失订单/成交/序号；如需长周期测试或故障演练，考虑事件溯源或快照。
- [ ] **P3-3 交易时段模拟**：集合竞价/连续竞价/收盘状态机（SZSE `TradingSessionStatus` 已在协议库定义）。
- [ ] **P3-4 北交所（BJSE）支持**：finproto 已有 `bjse-bin`，可按现有模板扩展。
- [ ] **P3-5 可观测性**：结构化日志、micrometer 指标（委托/成交/拒绝计数、时延）、报文级 trace。

---

## 建议的后续处理顺序

1. **P1-8 撤单链路**（协议完整性最大缺口，需求最明确）；
2. **P1-11 回报字段核对**（需取得 SSE/SZSE 协议规范，逐字段确认 taker 成交价、leavesQty、ordStatus、tradeDate）；
3. **P1-12 HTTP 模块定位决策**；
4. **P2-20 补 SZSE 回归用例 + CI**；
5. P3 按需。

## 本地验证方式（修复后）

```shell
# 单元测试（任意 JDK）
mvn test

# 完整运行（必须 JDK 8）
mvn package -DskipTests
java -jar target/exchange-simulator-1.0-SNAPSHOT.jar   # JDK 8

# SSE 端到端回归（需 gt-auto，应用启动后执行）
./autotest.sh
```
