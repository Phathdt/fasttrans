# ── Build stage ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Build + install the shared library first, then build the service standalone.
# The service pom uses spring-boot-starter-parent (not the root aggregator) and
# resolves fasttrans-web-commons from the local .m2, so the aggregator pom is
# not needed inside the image.
COPY libs/ ./libs/
RUN mvn -q -B -f libs/fasttrans-web-commons/pom.xml -DskipTests install
COPY services/auth/ ./services/auth/
RUN mvn -q -B -f services/auth/pom.xml -DskipTests clean package

# ── Runtime stage ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/auth/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
