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

## 自动化回归

依赖 [gt-auto](https://github.com/xinchentechnote/gt-auto) 测试工具：

```shell
go install github.com/xinchentechnote/gt-auto/cmd/gt-auto@v0.2.0
./autotest.sh
```
