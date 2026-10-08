FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY vaijunto/src ./vaijunto/src

RUN javac -d out vaijunto/src/model/*.java vaijunto/src/Server/*.java

EXPOSE 5000

CMD ["java", "-cp", "out", "vaijunto.src.Server.Servidor"]