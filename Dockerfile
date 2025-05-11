FROM openjdk:11

COPY target/chatbot-*.jar /usr/src/chatbot.jar
COPY src/main/resources/application.properties /opt/conf/application.properties
COPY credentials/dialogflow-service.json /app/credentials.json
COPY src/main/resources/static /usr/src/static

ENV GOOGLE_APPLICATION_CREDENTIALS=/app/credentials.json

CMD ["java", "-jar", "/usr/src/chatbot.jar", "--spring.config.location=file:/opt/conf/application.properties"]
