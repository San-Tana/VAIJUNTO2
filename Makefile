IMAGE = vaijunto-server
PORT_A = 5000
PORT_REST_A = 8081
PORT_B = 5001
PORT_REST_B = 8082
PORT_C = 5002
PORT_REST_C = 8083

build:
	javac -cp "lib/gson-2.15.0.jar" -d out $(find vaijunto -name "*.java")

# Comandos para rodar individualmente em terminais separados
run-a:
	java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.Server.Servidor EMP_A $(PORT_A) $(PORT_REST_A) http://localhost:$(PORT_REST_B),http://localhost:$(PORT_REST_C)

run-b:
	java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.Server.Servidor EMP_B $(PORT_B) $(PORT_REST_B) http://localhost:$(PORT_REST_A),http://localhost:$(PORT_REST_C)

run-c:
	java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.Server.Servidor EMP_C $(PORT_C) $(PORT_REST_C) http://localhost:$(PORT_REST_A),http://localhost:$(PORT_REST_B)

clean:
	rm -rf out