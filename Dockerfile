FROM openjdk:17-alpine
COPY build/libs/fly-io-demo-*.jar application.jar
CMD ["java","-jar","application.jar"]
