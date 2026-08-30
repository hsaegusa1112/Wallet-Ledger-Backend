FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /workspace/target/wallet-ledger-backend-0.0.1-SNAPSHOT.jar app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
