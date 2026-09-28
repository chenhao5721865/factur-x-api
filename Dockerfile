# 单阶段：拷贝预构建 JAR（多阶段首建在大陆网络太慢，切 CI 时再改）
FROM eclipse-temurin:17-jre-alpine

COPY web/target/web-*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
