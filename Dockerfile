FROM openjdk:11-jre-slim
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ignite_user_service.jar
ENTRYPOINT ["java", "-jar", "/ignite_user_service.jar"]