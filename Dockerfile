# Krok 1: Build aplikace pomocí Maven
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Zkopírujeme pom.xml a stáhneme závislosti (pro rychlejší buildy při změně kódu)
COPY pom.xml .
RUN mvn dependency:go-offline

# Zkopírujeme zdrojové kódy a zkompilujeme "Fat JAR"
COPY src ./src
RUN mvn clean package -DskipTests

# Krok 2: Spuštění aplikace v odlehčeném JRE
FROM eclipse-temurin:25-jre
WORKDIR /app

# Vytvoření adresářů pro perzistentní data
RUN mkdir -p /data

# Zkopírování zkompilovaného .jar z předchozího kroku
COPY --from=build /app/target/YoloRaker-1.0.0.jar /app/yoloraker.jar
# Nebo pro shaded verzi: COPY --from=build /app/target/YoloRaker-1.0.0-shaded.jar /app/yoloraker.jar
# Upraveno pro jistotu, protože shade plugin vytvoří klasický jar jako shaded
RUN mv /app/yoloraker.jar /app/yoloraker-tmp.jar || true
COPY --from=build /app/target/*shaded*.jar /app/yoloraker.jar 2>/dev/null || COPY --from=build /app/target/YoloRaker-1.0.0.jar /app/yoloraker.jar

# Port, na kterém aplikace poběží
EXPOSE 8080

# Příkaz pro spuštění
ENTRYPOINT ["java", "-jar", "/app/yoloraker.jar"]
