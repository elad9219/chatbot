FROM openjdk:11

COPY target/chatbot-0.0.1-SNAPSHOT.jar /usr/src/chatbot.jar

COPY src/main/resources/application.properties /opt/conf/application.properties

COPY src/main/resources/static /usr/src/static

COPY credentials/dialogflow-service.json /app/credentials.json

ENV GOOGLE_APPLICATION_CREDENTIALS=/Users/eladtennenboim/IdeaProjects/chatbot/credentials/dialogflow-service.json

CMD ["java", "-jar", "/usr/src/chatbot.jar", "--spring.config.location=file:/opt/conf/application.properties"]
