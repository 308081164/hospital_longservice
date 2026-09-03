FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV TZ=Asia/Shanghai

ARG GIT_SHA=local
ARG BUILD_TIME=unknown
ENV APP_GIT_SHA=${GIT_SHA}
ENV APP_BUILD_TIME=${BUILD_TIME}

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl default-mysql-client \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
COPY 测试表格 ./测试表格
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
COPY src/main/resources/db ./db

RUN chmod +x /app/docker-entrypoint.sh \
    && mkdir -p uploads/hospital-reconciliations storage/hospital-reconciliations \
    && printf 'git.sha=%s\nbuild.time=%s\n' "${GIT_SHA}" "${BUILD_TIME}" > /app/build-info.properties

EXPOSE 8000

ENTRYPOINT ["/app/docker-entrypoint.sh"]
