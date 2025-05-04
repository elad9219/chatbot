FROM openjdk:11
COPY target/chatbot*.jar /usr/src/chatbot.jar
COPY src/main/resources/application.properties /opt/conf/application.properties
CMD ["java", "-jar", "/usr/src/chatbot.jar", "--spring.config.location=file:/opt/conf/application.properties"]

