FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 \
               --gid app \
               --no-create-home \
               --shell /usr/sbin/nologin app \
    && chown app:app /app

COPY --chown=app:app target/app.jar /app/app.jar

ENV SERVER_PORT=8080 \
    SERVER_ADDRESS=0.0.0.0

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
