FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S forgegate && adduser -S -G forgegate -u 10001 forgegate
WORKDIR /app
COPY --from=build --chown=forgegate:forgegate /workspace/target/forgegate-api-1.0.0.jar /app/app.jar
USER 10001:forgegate
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=4s --start-period=40s --retries=5 CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
