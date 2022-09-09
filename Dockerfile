FROM openjdk:17-alpine
COPY build/libs/fly-io-demo-0.0.1-SNAPSHOT.jar application.jar
CMD ["java","-jar","application.jar"]
