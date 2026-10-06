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
  已完成：Dockerfile 固化 JDK 8 运行时、readme/设计文档明确约束、提供 Zulu 8 验证记录；CI 构建矩阵 JDK 8（产物）+ JDK 17（兼容性哨兵），运行时冒烟仅在 JDK 8。
  待办：如需 JDK 11+ 需等待或自行维护 exchange-core 的 chronicle 升级。

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

- [x] **P1-8 撤单全链路未实现** ✅（2026-10 功能开发轮实现）
  撤单请求（SSE `ORDER_CANCEL(61)` / SZSE `OrderCancelRequest(190007)`）按 `origClOrdId` 反查原订单（`clOrdIdIndex`）→ 组装 `ApiCancelOrder`（orderId/uid/symbol 取自原委托）提交撮合核心；SUCCESS → 撤单确认（Report/ExecutionReport，ExecType=4、OrdStatus=4、LeavesQty=0）并清理缓存与索引；失败 → 撤单拒绝（SSE `CANCEL_REJECT(59)` CxlRejReason / SZSE `CancelReject(290008)` RejectText），原订单保留。
  回归测试：`SseCancelTest`（4 用例：上下文组装/未知订单拒绝/撤单成功/撤单失败）、`SzseCancelTest`（同构 4 用例）。

- [x] **P1-9 未覆盖的委托结果码不下发任何回执** ✅
  SSE/SZSE Confirm 转换器的 default 分支改为按申报拒绝（ExecType=8）兜底下发。
  回归测试：`SseConfirmConvertorTest.unmappedCodeShouldFallbackToReject`、`SzseConfirmConvertorTest.unmappedCodeShouldFallbackToReject`。

- [x] **P1-10 SSE 下行 MsgSeqNum 全局计数** ✅
  序号改为按会话（channel attr）独立自增；SZSE 协议帧无序号字段，无需处理。
  回归测试：`SseBinServerTest.msgSeqNumShouldBePerSession`（双会话序号独立）。

- [x] **P1-11 成交回报字段语义** 🔧（2026-10 功能开发轮修正主要项，正式规范核对仍开放）
  已修正：回报改为**逐笔生成**（taker/maker 每笔成交各一份），`LastPx`/`LastQty` 取实际成交价量（原 taker 取委托价）；`CommandWrapper.cumQty` 累计成交量，`LeavesQty = 委托量 - CumQty`（SZSE 另填 `CumQty`；SSE Report 无该字段）；撤单确认的 CumQty/LeavesQty 同样正确。
  已验证非问题：`tradeDate = transactTime/1e6` 对 `yyyymmddHHmmss` 格式时间戳即 `yyyymmdd`，与 gt-auto 基准一致。
  保持现状：成交回报 `OrdStatus` 恒 "0"（与 gt-auto 基准数据一致，正式语义待规范确认）；手续费/成交编号等字段协议未用。
  回归测试：`SzseBinServerTest`（leavesQty/cumQty 断言）；gt-auto SSE 端到端回归全绿（LastPx/LeavesQty 逐字段匹配）。

- [x] **P1-12 HTTP 模块与协议模块撮合核心割裂** ✅（2026-10 功能开发轮：打通市场）
  删除独立 demo 撮合核心（`ExchangeConfig`，硬编码 uid 1001/symbol 10086）；HTTP 经 `common.OrderGateway` 按 `market` 参数路由到 SSE/SZSE 真实撮合核心（缺省 sse），与协议通道共用市场数据；HTTP 委托缓存复用（channel/originMsg 为空，回报仅记日志）。
  回归测试：`ExchangeServiceImplTest`（路由/异步/同步/超时 5 用例）；boot 冒烟验证 HTTP 单真实进入 SSE 市场并同步收到确认。

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
  新增 `szse/SzseMsgType`（LOGON=1、HEARTBEAT=3、ORDER_CANCEL_REQUEST=190007、CANCEL_REJECT=290008、EXECUTION_CONFIRM=200102、EXECUTION_REPORT=200115 等），全部替换。

- [x] **P2-19 HTTP 接口语义修正** ✅（2026-10 功能开发轮完成同步等待与 DTO 接入）
  已完成：javadoc 与实现对齐；orderId 非数字由 `@Pattern` 拦截（400）；`@Positive` 语义修正；`waitTimeoutMs>0` 时同步等待撮合确认（先注册等待再提交避免竞态），返回 `OrderResponse`（success/pending/execType/ordStatus）；`OrderResult` 未接入已删除。

- [x] **P2-20 测试与交付工程化** 🔧
  已完成：新增 10 个测试类 38 个用例（转换器映射、确认回执、会话层管线、按会话序号、缓存清理、CSV fail-fast、参数校验）；pom 补 surefire 2.22.2（此前 JUnit 5 用例根本不会执行）；Dockerfile（JDK 8 基础镜像，含 data 目录）；`local-test.sh` 本地基础测试脚本（unit/boot/e2e 三阶段，条件不足自动跳过）；GitHub Actions CI（`.github/workflows/ci.yml`：JDK 8+17 构建矩阵、JDK 8 产物冒烟、gt-auto 协议回归，后两者复用本地脚本）。
  未做：SZSE 协议回归用例（gt-auto testcase 缺失，补齐后可在 CI 的 protocol-e2e job 一并执行）。

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

1. **SZSE gt-auto 回归用例**（撤单/成交回报已实现但仅 SSE 有端到端回归）；
2. **回报字段正式规范核对**（OrdStatus 成交态语义、手续费/成交编号等未填字段）；
3. **双市场模板化抽象**（P2-16 未做项，两套 Server 约 80% 重复）；
4. P3 按需（行情/持久化/交易时段/北交所/可观测性）。

## 变更历史

- 2026-09-19：文档建立 + 第一轮修复（全部 P0、7 项 P1 缺陷、P2 工程问题，38 个测试）。
- 2026-10-07：功能开发轮——撤单全链路（P1-8）、成交回报逐笔化与字段修正（P1-11）、HTTP 打通市场（P1-12）、HTTP 同步等待确认（P2-19），测试增至 53 个。

## 本地验证方式（修复后）

```shell
# 一键基础测试（unit + boot + e2e，条件不足自动跳过）
./local-test.sh

# 或分阶段
./local-test.sh unit        # 编译 + 53 个单元测试 + 打包（任意 JDK）
./local-test.sh boot        # JDK 8 启动冒烟（需 JAVA8_HOME 或 --jdk8）
./local-test.sh e2e         # gt-auto SSE 协议回归（另需 gt-auto）

# CI 同款流程见 .github/workflows/ci.yml
```
