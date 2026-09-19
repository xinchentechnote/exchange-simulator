# exchange-simulator 运行时镜像
# 注意：撮合核心依赖 chronicle 2.19，仅兼容 JDK 8（见 docs/TODO.md P0-1）
FROM eclipse-temurin:8-jre

WORKDIR /app
COPY target/exchange-simulator-1.0-SNAPSHOT.jar app.jar
COPY data data

EXPOSE 8080 9010 9011
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
