FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY target/*.jar app.jar

USER spring:spring

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
