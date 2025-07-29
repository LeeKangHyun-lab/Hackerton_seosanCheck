FROM openjdk:17-jdk-slim
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build -x test
CMD ["java", "-jar", "build/libs/*.jar"]