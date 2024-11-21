#FROM sbtscala/scala-sbt:eclipse-temurin-17.0.4_1.10.1_3.2.0 as builder
FROM sbtscala/scala-sbt:eclipse-temurin-17.0.4_1.7.1_3.2.0 as builder
WORKDIR /build
# Cache dependencies first
COPY ./project project
COPY ./build.sbt .
RUN sbt update
# Then build
COPY . .
RUN sbt --no-server assembly

#FROM openjdk:17-jdk-slim
FROM eclipse-temurin:17.0.13_11-jre-ubi9-minimal
WORKDIR /app
COPY --from=builder /build/target/scala-3.4.1/olx-assembly-0.4.0.jar olx.jar
EXPOSE 5005 8080
ENTRYPOINT ["java"]
#CMD ["-cp", "/app/olx.jar", "org.olx.Main"]
CMD ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005","-cp", "/app/olx.jar", "org.olx.Main"]