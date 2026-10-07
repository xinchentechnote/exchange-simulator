# exchange-simulator

交易所撮合模拟器：基于 [exchange-core2](https://github.com/exchange-core/xchange-core) 撮合引擎，模拟上交所（SSE）与深交所（SZSE）券商网关二进制交易协议，为 OMS / 交易客户端提供测试对手方环境。

- 详细设计：[docs/DESIGN.md](docs/DESIGN.md)
- 待办与已知问题：[docs/TODO.md](docs/TODO.md)

## 快速开始

```shell
# 单元测试（任意 JDK）
mvn test

# 构建（运行时要求 JDK 8，见 docs/TODO.md P0-1）
mvn clean package -DskipTests

# 启动（SSE :9010 / SZSE :9011 / HTTP :8080）
java -jar target/exchange-simulator-1.0-SNAPSHOT.jar

# 或使用 Docker（内置 JDK 8 运行时，规避本机 JDK 版本问题）
docker build -t exchange-simulator .
docker run --rm -p 8080:8080 -p 9010:9010 -p 9011:9011 exchange-simulator
```

| 端口 | 服务 |
| --- | --- |
| 9010 | SSE 二进制协议（`sse.bin.server.port`） |
| 9011 | SZSE 二进制协议（`szse.bin.server.port`） |
| 8080 | HTTP 测试接口（`POST /api/v1/orders/place`、`GET /api/v1/orders/health`） |

证券与账户基础数据位于 `data/{sse,szse}/*.csv`（格式说明见设计文档 §3.5）。

## 本地测试

```shell
./local-test.sh            # 全部阶段：unit(编译+单测+打包) → boot(JDK8启动冒烟) → e2e(gt-auto协议回归)
./local-test.sh unit boot  # 只跑指定阶段
```

- JDK 8 自动探测顺序：`--jdk8` 参数 > `JAVA8_HOME` > `JAVA_HOME` > macOS `java_home -v 1.8`（结果会做版本校验）> 常见安装目录。
  本机安装 JDK 8 推荐放入 `~/Library/Java/JavaVirtualMachines/`（Zulu 等 tar.gz 解压即可被 `java_home` 识别）。
- 无 JDK 8 / gt-auto 时对应阶段自动跳过（SKIP），不影响其他阶段；SKIP 不计入失败。
- e2e 阶段有 10 分钟看门狗（`E2E_TIMEOUT` 可调），gt-auto 异常时不会无限等待。
- `pom.xml` 依赖 `com.xinchentechnote.finproto:{sse-bin,szse-bin}`（传递依赖 `codec`），这三个构件只在
  [fin-proto-java](https://github.com/xinchentechnote/fin-proto-java) 中生成、未发布到 Maven Central。
  新机器上 `mvn` 会报 `Could not resolve dependencies`，需先发布到本地仓库：
  `./gradlew :codec:publishToMavenLocal :sse-bin:publishToMavenLocal :szse-bin:publishToMavenLocal`（Gradle 工具链为 JDK 11）。

## CI

`.github/workflows/ci.yml`（push / PR / 手动触发）：

| Job | 内容 |
| --- | --- |
| finproto-deps | 检出 fin-proto-java，Gradle 发布 codec/sse-bin/szse-bin 成 Maven 仓库 artifact（私有协议依赖，Central 上没有） |
| build-test | 装入 finproto-deps 产物后，JDK 8 + 17 矩阵执行 `mvn clean verify`，JDK 8 产物上传 artifact |
| smoke-test | JDK 8 启动打包产物，做 HTTP 冒烟（复用 `local-test.sh boot`） |
| protocol-e2e | JDK 8 + gt-auto 跑 SSE 协议回归（复用 `local-test.sh e2e`） |

## 自动化回归

依赖 [gt-auto](https://github.com/xinchentechnote/gt-auto) 测试工具：

```shell
# v0.2.1 起 SZSE 成交回报支持全字段校验（ApplExtend 预填修复）
go install github.com/xinchentechnote/gt-auto/cmd/gt-auto@v0.2.1
./autotest.sh
```
