# syntax=docker/dockerfile:1

# ---------- Stage 1: build (has JDK + Maven, discarded afterwards) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 1) Copy only the build descriptors first so the dependency layer is cached
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# 2) Then the source code (changes often): only these layers rebuild on code changes
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package

# 3) Split the fat jar into layers (dependencies rarely change, app code often)
RUN java -Djarmode=tools -jar target/product-service-0.0.1-SNAPSHOT.jar \
        extract --layers --launcher --destination extracted

# ---------- Stage 2: runtime (JRE only, small, no build tools) ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
