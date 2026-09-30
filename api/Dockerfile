FROM eclipse-temurin:17-jre-alpine
COPY api/target/api-*.jar /app/app.jar
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8081/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
