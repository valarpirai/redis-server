FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/redis-server-1.0-SNAPSHOT.jar app.jar

EXPOSE 6379

ENV PORT=6379
ENV POOL_SIZE=1000
ENV CLEAN_INTERVAL_MS=10000
ENV MAX_MEMORY_BYTES=0
ENV EVICTION_POLICY=noeviction
ENV AOF_FILE=
ENV AOF_FSYNC=everysec

ENTRYPOINT ["java", "-jar", "app.jar"]
