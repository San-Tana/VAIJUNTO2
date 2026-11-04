# VAIJUNTO - Consórcio de Caronas

O VaiJunto é um sistema cliente-servidor distribuído para gerenciamento de caronas compartilhadas. Originalmente um sistema centralizado, o VaiJunto evoluiu para um **consórcio de empresas independentes**. Cada empresa mantém seu próprio servidor, seus próprios usuários e caronas.

Os clientes se conectam ao servidor de sua respectiva empresa via protocolo textual sobre TCP/IP. Os servidores, por sua vez, comunicam-se entre si através de uma **API REST**, permitindo que passageiros reservem itinerários compostos por trechos de empresas diferentes de forma transparente e atômica, utilizando o protocolo de *Two-Phase Commit* (2PC) simplificado.\
\
Você pode acessar o repositório original por este link: https://github.com/San-Tana/VAIJUNTO

## Estrutura do projeto

A estrutura principal do projeto é:

```
VAIJUNTO/
├── Dockerfile
├── Makefile
├── README.md
├── dados/ (gerado automaticamente para persistência)
├── lib/
│   └── gson-2.15.0.jar
├── vaijunto/
│   └── src/
│       ├── App/
│       │   ├── Aplicacao.java
│       │   └── servicosClient.java
│       │
│       ├── model/
│       │   ├── cliente.java
│       │   ├── corrida.java
│       │   ├── corridaComposta.java
│       │   ├── identificadorGlobal.java
│       │   └── ... (outras classes de modelo)
│       │
│       ├── Server/
│       │   ├── Consorcio.java
│       │   ├── PersistenciaJson.java
│       │   ├── servicosServidor.java
│       │   └── Servidor.java
│       │
│       └── Test/
│           ├── TesteConcorrencia.java
│           ├── TesteConcorrenciaComposta.java
│           ├── TesteIntegracao.java
│           └── TesteReservaComposta.java
│
└── out/

```

De forma geral:

* `App/`: aplicação interativa do cliente (passageiro/motorista).

* `Server/`: servidor da empresa, API REST do consórcio, controle de transações e persistência.

* `model/`: classes que representam os dados e lógica de negócio.

* `Test/`: testes automatizados de integração, carga e concorrência remota.

* `lib/`: dependências externas (Gson para serialização JSON).

* `Dockerfile` & `Makefile`: orquestração do ambiente distribuído.

## Requisitos

* Docker

* Make

* Java/JDK (versão 17+) para compilar e executar clientes/testes.

Para verificar se estão disponíveis, acesse o terminal e digite:

```
docker --version
make --version
java -version

```

## Arquitetura Docker e Makefile

Para simular o consórcio de maneira realista, o sistema utiliza o Docker para rodar cada servidor de empresa em um ambiente isolado. O `Makefile` orquestra uma **Rede local,**  permitindo que as empresas se descubram e comuniquem por nome de host (ex: `localhost:8082`), enquanto mapeiam portas diferentes para a sua máquina local (`5000`, `5001`, `5002` para TCP).

### Compilando o Projeto

Na raiz do projeto, execute:

```
make build

```

*Isso compilará todas as classes Java incluindo a dependência do Gson e criará a imagem Docker do servidor.*

## Executando o Consórcio (Servidores)

Você pode levantar as instâncias das três empresas (EMP_A, EMP_B, EMP_C) em terminais separados, podendo acompanhar os logs (como sincronização REST e *Gossip*) em tempo real. Abra três terminais distintos e execute um comando em cada:

* Terminal 1: `make run-a`

* Terminal 2: `make run-b`

* Terminal 3: `make run-c`

Para limpar a rede virtual:

```
make clean

```

## Executando o Cliente Interativo

O cliente deve se conectar a um dos servidores ativos do consórcio através do protocolo TCP.
*Nota: É necessário incluir o Gson no classpath.*

Execute a aplicação informando o `host` e a `porta TCP` do servidor desejado.

Para conectar à **EMP_A**:

```
java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.App.Aplicacao localhost 5000

```

Para conectar à **EMP_B**:

```
java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.App.Aplicacao localhost 5001

```

(No Windows, substitua o separador `:` por `;` no `-cp`)*.*

## Testes de Concorrência Distribuída

Os testes automatizados simulam dezenas de passageiros tentando reservar as mesmas vagas (gargalos) simultaneamente a partir de empresas distintas. Eles provam a eficácia da exclusão mútua distribuída e do algoritmo de **Two-Phase Commit (2PC)** na prevenção de *overbooking* e falhas parciais.

Com o consórcio em execução (`make start-all`), execute em um terminal separado:

**Teste de Concorrência Simples (Uma Corrida Direta, 3 servidores disputando):**

```
java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.Test.TesteConcorrencia

```

**Teste de Concorrência Composta (Múltiplos Trechos, 2PC):**

```
java -cp "out:lib/gson-2.15.0.jar" vaijunto.src.Test.TesteConcorrenciaComposta

```

### Injeção de Falhas (Chaos Testing)

Para testar a resiliência do consórcio a falhas, durante a execução de um teste ou através da aplicação cliente, você pode derrubar abruptamente um dos servidores em outro terminal e o sistema garantirá que nenhuma vaga em `EMP_A` ou `EMP_C` fique travada em estado `PREPARADA` pela metade, abortando transações em andamento de forma segura.

## Comunicação Cliente-Servidor (TCP)

O cliente e o servidor da empresa comunicam-se via Sockets. O protocolo segue a estrutura:
`OPERACAO|usuario|dados`

Novos comandos adicionados no Consórcio:

* `RESERVAR|usuario|ID_GLOBAL;origem;destino`: Solicita reserva direta global.

* `RESERVAR_COMPOSTA|usuario|ID_COMPOSTO`: Inicia transação distribuída (2PC).

## Comunicação Servidor-Servidor (REST)

Para garantir independência comercial e driblar firewalls, os servidores se comunicam através da porta HTTP interna usando JSON.

**Principais Endpoints (`Consorcio.java`):**

* `GET /api/status`: Identidade da empresa.

* `GET /api/index`: Fotografia (Snapshot) do índice de rotas.

* `POST /api/atualizar`: Gatilho de sincronização (Gossip Protocol).

* `GET /api/corridas`: Consulta o estado atual (Vagas, Preço).

* `POST /api/reserva/preparar`: Fase 1 do 2PC (Bloqueio de recursos).

* `POST /api/reserva/confirmar`: Fase 2 do 2PC (Confirmação).

* `POST /api/reserva/abortar`: Rollback da Fase 1 em caso de conflitos de rede ou concorrência.

## Persistência de Dados

A classe `PersistenciaJson.java` salva o estado do servidor periodicamente na pasta `dados/`. O salvamento utiliza um arquivo `.tmp` intermediário aliado ao `StandardCopyOption.ATOMIC_MOVE` para garantir que um crash de energia nunca corrompa o arquivo de banco de dados oficial da empresa, mantendo o estado das reservas seguro entre reinicializações.
