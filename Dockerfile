# syntax=docker/dockerfile:1

# ---------- Etapa 1: compilar ----------
# Imagen con el JDK completo y Maven (vía wrapper). Solo existe durante la construcción.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primero solo lo necesario para descargar dependencias: mientras el pom.xml no cambie,
# Docker reutiliza esta capa y no vuelve a descargar nada.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Después el código. Los tests no se ejecutan aquí: necesitan Docker (Testcontainers)
# y ya los ejecuta GitHub Actions antes de cada despliegue.
COPY src src
RUN ./mvnw -B -ntp package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted \
       --application-filename application.jar

# ---------- Etapa 2: ejecutar ----------
# Solo el JRE: imagen más pequeña y sin compilador ni Maven dentro.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Nunca ejecutar como root dentro del contenedor.
RUN groupadd --system app && useradd --system --gid app app

# Capas de la más estable a la más cambiante: las dependencias casi nunca cambian,
# así que un despliegue normal solo sube la capa "application" (unos pocos KB).
COPY --from=build /app/target/extracted/dependencies/ ./
COPY --from=build /app/target/extracted/spring-boot-loader/ ./
COPY --from=build /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /app/target/extracted/application/ ./

# CDS (Class Data Sharing): se arranca la app una vez durante la construcción y Java guarda
# en app.jsa las clases que cargó. En cada arranque real las lee ya preparadas, que es mucho
# más rápido con la poca CPU del plan gratuito.
# El arranque de entrenamiento se detiene al crear el contexto (spring.context.exit) y no
# necesita base de datos: se desactivan Flyway y la validación del esquema, y la URL es ficticia.
RUN java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
        -Dspring.datasource.url=jdbc:postgresql://localhost/entrenamiento-cds \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        -Djwt.secret=solo-para-el-entrenamiento-de-cds-no-se-usa \
        -jar application.jar

# A partir de aquí, sin privilegios: los archivos son de root y la app solo puede leerlos.
USER app

# Ajustes para el plan gratuito de Render (512 MB de RAM, CPU muy limitada):
# - MaxRAMPercentage: el heap usa como mucho el 70 % del límite del contenedor; el resto
#   queda para metaspace, hilos y buffers (si no, el sistema mata el proceso por memoria).
# - SerialGC: el recolector más ligero; con una sola CPU no compensa uno concurrente.
# - Xss512k: pila más pequeña por hilo (Tomcat crea varios).
# - TieredStopAtLevel=1: solo el compilador JIT rápido (C1). Arranca mucho antes; el código
#   final es algo menos optimizado, irrelevante para el tráfico de una demo.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
# application.jar solo contiene nuestro código y apunta a las librerías extraídas en lib/.
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "application.jar"]
