# Imagen de un módulo del backend. Uso: docker build --build-arg MODULE=api .
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY api/pom.xml api/
COPY worker/pom.xml worker/
COPY realtime/pom.xml realtime/
RUN chmod +x mvnw && ./mvnw -B -q -pl ${MODULE} -am dependency:go-offline -DskipTests || true
COPY . .
RUN chmod +x mvnw && ./mvnw -B -q -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/${MODULE}-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
