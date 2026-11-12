FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --chown=10001:10001 target/chatbot-0.0.1-SNAPSHOT.jar /app/chatbot.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/chatbot.jar"]