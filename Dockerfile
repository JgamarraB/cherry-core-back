# ---------------------------------------------------------------
# Imagen de producción de CherryCore.
#   docker build -t cherrycore:latest .
# Configuración por variables de entorno: ver docs/arranque.md
# ---------------------------------------------------------------

# 1) Compilación (los tests se ejecutan en CI, aquí no: necesitan Docker).
FROM eclipse-temurin:21.0.9_10-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests \
    && java -Djarmode=tools -jar target/cherry-core.jar extract --destination extracted

# 2) Ejecución: solo JRE, usuario sin privilegios, sin herramientas de compilación.
FROM eclipse-temurin:21.0.9_10-jre-noble
RUN groupadd --system --gid 10001 cherrycore \
    && useradd --system --uid 10001 --gid cherrycore --no-create-home cherrycore
WORKDIR /app
COPY --from=build --chown=cherrycore:cherrycore /workspace/extracted/ ./
USER 10001
EXPOSE 8080
# Usa como mucho el 75 % de la memoria del contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/cherry-core.jar"]
