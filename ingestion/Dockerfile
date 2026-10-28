FROM maven:3.9.9-eclipse-temurin-21 AS connectors
COPY connectors-pom.xml /tmp/pom.xml
RUN mvn -B -f /tmp/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/connectors

FROM python:3.12-slim-bookworm AS api
WORKDIR /app
COPY requirements-api.lock /app/
RUN pip install --no-cache-dir -r requirements-api.lock
RUN useradd --uid 10001 --create-home service
COPY app /app/app
COPY sql /app/sql
COPY config /app/config
USER 10001:10001
EXPOSE 8080
CMD ["uvicorn","app.api:create_app","--factory","--host","0.0.0.0","--port","8080"]

FROM api AS worker
USER root
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jre-headless && rm -rf /var/lib/apt/lists/*
COPY requirements-worker.lock /app/
RUN pip install --no-cache-dir -r requirements-worker.lock
COPY --from=connectors /connectors /opt/connectors
ENV SPARK_CONNECTOR_JARS=/opt/connectors/hadoop-aws-3.4.1.jar,/opt/connectors/bundle-2.24.6.jar,/opt/connectors/postgresql-42.7.7.jar,/opt/connectors/wildfly-openssl-1.1.3.Final.jar
ENV SPARK_LOCAL_IP=127.0.0.1
COPY scripts /app/scripts
USER 10001:10001
CMD ["python","-m","app.worker"]
