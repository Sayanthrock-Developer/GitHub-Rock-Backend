FROM gradle:8.13-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean buildFatJar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/build/libs/github-rock-backend.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
