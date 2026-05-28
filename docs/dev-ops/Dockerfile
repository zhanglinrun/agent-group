FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai

COPY backend/agent-group-app/target/agent-group-app-1.0-SNAPSHOT.jar /app/agent-group-app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/agent-group-app.jar"]
