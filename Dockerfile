# Zweistufig: bauen mit dem JDK, ausliefern mit dem schlanken JRE.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Abhaengigkeiten zuerst — die Schicht bleibt gecacht, solange sich die pom.xml
# nicht aendert, und spart bei jedem Deploy den kompletten Download.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# Nicht als root laufen.
RUN groupadd --system wahlen && useradd --system --gid wahlen --home /app wahlen

COPY --from=build /build/target/wahlen-*.jar app.jar
RUN chown -R wahlen:wahlen /app
USER wahlen

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
