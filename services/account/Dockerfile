# ── Build stage ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Build + install the shared libraries first, then build the service standalone.
# The service pom uses spring-boot-starter-parent (not the root aggregator) and
# resolves the fasttrans-* jars from the local .m2, so the aggregator pom is
# not needed inside the image.
COPY libs/ ./libs/
RUN mvn -q -B -f libs/fasttrans-web-commons/pom.xml -DskipTests install
RUN mvn -q -B -f libs/fasttrans-grpc-contract/pom.xml -DskipTests install
COPY services/account/ ./services/account/
RUN mvn -q -B -f services/account/pom.xml -DskipTests clean package

# ── Runtime stage ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/services/account/target/*.jar app.jar
EXPOSE 9090
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
