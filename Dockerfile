FROM eclipse-temurin:25-jdk

WORKDIR /app

# Copia as bibliotecas (Gson) e o código-fonte
COPY lib ./lib
COPY vaijunto/src ./vaijunto/src

# Compila o projeto incluindo o Gson no classpath (separador ':' no Linux)
RUN javac -cp "lib/*" -d out vaijunto/src/model/*.java vaijunto/src/Server/*.java

# ENTRYPOINT repassa os argumentos do 'docker run' diretamente para a classe Servidor
ENTRYPOINT ["java", "-cp", "out:lib/*", "vaijunto.src.Server.Servidor"]