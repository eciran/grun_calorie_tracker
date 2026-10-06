FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN if [ ! -f src/main/resources/application.yml ]; then \
      cp src/main/resources/application-example.yml src/main/resources/application.yml; \
    fi
RUN mvn -B -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 grun && adduser -S -D -H -u 10001 -G grun grun \
    && mkdir -p /app/storage /app/logs \
    && chown -R grun:grun /app/storage /app/logs \
    && chmod 0444 /app/app.jar
USER 10001:10001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
