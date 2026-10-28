FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
# The local H2 database must be writable by the unprivileged application user.
RUN mkdir /data && chown 10001:10001 /data
VOLUME ["/data"]
COPY --from=build /src/target/service-desk-1.0.0.jar /app/app.jar
USER 10001:10001
ENV DB_URL=jdbc:h2:file:/data/desk
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
