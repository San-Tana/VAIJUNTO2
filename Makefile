IMAGE = vaijunto-server

# IPs dos computadores que hospedam os servidores
IP_A = localhost
IP_B = localhost
IP_C = localhost

# Portas TCP
PORT_A = 5000
PORT_B = 5001
PORT_C = 5002

# Portas REST
PORT_REST_A = 8081
PORT_REST_B = 8082
PORT_REST_C = 8083

# Endereços dos servidores parceiros
PEERS_A = http://$(IP_B):$(PORT_REST_B),http://$(IP_C):$(PORT_REST_C)
PEERS_B = http://$(IP_A):$(PORT_REST_A),http://$(IP_C):$(PORT_REST_C)
PEERS_C = http://$(IP_A):$(PORT_REST_A),http://$(IP_B):$(PORT_REST_B)

build:
	docker build -t $(IMAGE) .

# Inicialização dos servidores
run-a:
	docker run -d --name servidor-a \
	-p $(PORT_A):$(PORT_A) \
	-p $(PORT_REST_A):$(PORT_REST_A) \
	$(IMAGE) EMP_A $(PORT_A) $(PORT_REST_A) $(PEERS_A)

run-b:
	docker run -d --name servidor-b \
	-p $(PORT_B):$(PORT_B) \
	-p $(PORT_REST_B):$(PORT_REST_B) \
	$(IMAGE) EMP_B $(PORT_B) $(PORT_REST_B) $(PEERS_B)

run-c:
	docker run -d --name servidor-c \
	-p $(PORT_C):$(PORT_C) \
	-p $(PORT_REST_C):$(PORT_REST_C) \
	$(IMAGE) EMP_C $(PORT_C) $(PORT_REST_C) $(PEERS_C)

clean:
	rm -rf out