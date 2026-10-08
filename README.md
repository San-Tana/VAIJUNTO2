# VAIJUNTO

O VaiJunto é um Sistema cliente-servidor para gerenciamento de caronas compartilhadas, desenvolvido em Java utilizando comunicação via TCP/IP. O sistema possui um servidor central responsável por manter os usuários, corridas e reservas. Os clientes se comunicam com esse servidor através de um protocolo textual utilizando sockets.

## Estrutura do projeto

A estrutura principal do projeto é:

```text
VAIJUNTO/
├── Dockerfile
├── Makefile
├── README.md
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
│       │   ├── data.java
│       │   ├── erros.java
│       │   ├── motorista.java
│       │   └── okays.java
│       │
│       └── Server/
│           ├── servicosServidor.java
│           └── Servidor.java
│
└── out/
```

De forma geral:

- `App/`: contém a aplicação cliente e os serviços utilizados pelo cliente.
- `Server/`: contém o servidor e as operações realizadas por ele.
- `model/`: contém as classes que representam os dados do sistema.
- `Dockerfile`: descreve como criar o ambiente do servidor.
- `Makefile`: fornece comandos simplificados para construir e executar o servidor.
- `out/`: contém os arquivos `.class` gerados pela compilação.

---

## Requisitos

Para executar o projeto utilizando o ambiente preparado no repositório, é necessário possuir:
  - Docker
  - Make
  - Java/JDK para executar o cliente

Para verificar se estão disponíveis, acesse o terminal e digite os comandos:
```bash
docker --version
make --version
java --version
javac --version
```

---

# Docker

O Docker é utilizado para executar o servidor em um ambiente isolado. O arquivo `Dockerfile` descreve como esse ambiente deve ser construído. Durante a criação da imagem, o código do servidor e das classes utilizadas por ele é copiado para o container e compilado.

De forma simplificada:

```text
Código Java
    ↓
Dockerfile
    ↓
Imagem Docker
    ↓
Container
    ↓
Servidor VAIJUNTO
```

Isso permite que o servidor seja executado de maneira semelhante em diferentes computadores, desde que eles possuam Docker.

## Imagem e container

É importante diferenciar os dois conceitos.

A **imagem** é o modelo utilizado para criar o ambiente do servidor.

O **container** é uma instância em execução dessa imagem.

Por exemplo:

```text
Dockerfile
    ↓
vaijunto-server (imagem)
    ↓
vaijunto-server (container)
    ↓
Servidor Java executando
```

Quando o código do servidor é alterado, normalmente é necessário reconstruir a imagem para que o novo código seja incluído.

---

# Makefile

O `Makefile` serve para simplificar os comandos utilizados durante o desenvolvimento.

Em vez de escrever manualmente vários comandos do Docker, podemos utilizar comandos como:

```bash
make run
```

ou:

```bash
make rebuild
```

O Make procura pelo arquivo `Makefile` na pasta atual e executa as instruções associadas ao comando informado.

Por isso, os comandos devem ser executados na **raiz do projeto**, onde estão:

```text
Dockerfile
Makefile
README.md
vaijunto/
```

---

# Executando o servidor

## Primeira execução

Na raiz do projeto:

```bash
make run
```

Esse comando constrói a imagem Docker e inicia o container do servidor.

O servidor utiliza a porta:

```text
5000
```

O container deve permanecer executando enquanto os clientes estiverem utilizando o sistema.

---

## Após alterar o servidor

Caso alguma classe utilizada pelo servidor tenha sido modificada, a imagem Docker precisa ser reconstruída.

Utilize:

```bash
make rebuild
```

Esse comando remove o container anterior, reconstrói a imagem com o código atualizado e inicia novamente o servidor.

---

## Parar o servidor

Para interromper o container:

```bash
make stop
```

---

## Remover o container

Para remover o container criado anteriormente:

```bash
make clean
```

Isso é útil, por exemplo, caso seja necessário criar novamente o container ou corrigir algum problema de execução.

---

# Executando o cliente

O servidor deve estar executando antes de iniciar o cliente.

Caso ainda não tenha compilado o cliente:

```bash
javac -d out vaijunto/src/model/*.java vaijunto/src/App/*.java
```

Depois:

```bash
java -cp out vaijunto.src.App.Aplicacao
```

O cliente atualmente se conecta ao servidor através da porta `5000`.

Durante testes locais, cliente e servidor podem estar na mesma máquina, utilizando:

```text
localhost:5000
```

Para testes distribuídos em computadores diferentes, o endereço utilizado pelo cliente deverá apontar para o computador no qual o servidor está sendo executado.

---

# Desenvolvimento

## Quando usar `make rebuild`?

Uma regra prática:

```text
Alterou Server/ ou model/ e essa classe é usada pelo servidor?
    → make rebuild

Alterou somente App/?
    → não é necessário reconstruir o container
       basta recompilar/reexecutar o cliente
```

Isso acontece porque o servidor é executado dentro do container, enquanto o cliente é executado separadamente.

---

# Comunicação cliente-servidor

O VAIJUNTO utiliza um protocolo textual próprio sobre TCP. Uma requisição possui uma operação seguida pelo nome do usuário e os dados a serem utilizados, ou apenas os dados, caso a verificação de usuário não seja necessária. 

```text
OPERACAO|usuario|dados
```

Por exemplo, uma publicação de corrida pode seguir a estrutura:

```text
PUBLICAR|usuario|origem;destino;data;assentos;preco
```

Os separadores possuem significados diferentes no protocolo:

```text
|   separa os campos principais da mensagem
;   separa campos internos
&   separa informações agrupadas, como data ou paradas
```

Ao alterar uma mensagem, é necessário verificar **tanto o cliente quanto o servidor**.

Por exemplo, se o cliente envia:

```text
BUSCAR|usuario|origem;destino
```

o servidor precisa interpretar exatamente essa estrutura. Alterar somente um dos lados pode fazer o servidor interpretar posições erradas do vetor gerado pelo `split()`.

---

# Cuidados ao modificar o protocolo

Antes de alterar o formato de uma operação, verifique:
  1. Onde a mensagem é criada no cliente.
  2. Como ela é separada no servidor.
  3. Qual método recebe cada campo.
  4. Qual resposta o servidor envia.
  5. Como o cliente interpreta essa resposta.

Por exemplo:

```java
mensagem.split("\\|")
```

separa os campos principais.

Enquanto:

```java
dados.split("\\;")
```

separa os dados internos.

E:

```java
dados.split("\\&")
```

é utilizado em estruturas menores, como datas, veículos e listas de paradas.

Evite utilizar esses caracteres diretamente nos valores inseridos pelo usuário sem algum mecanismo de escape, pois eles fazem parte da estrutura do protocolo.

---

# Quebras de linha nas mensagens

O cliente utiliza `readLine()` para receber uma resposta do servidor. Por esse motivo, uma resposta enviada pelo servidor não deve possuir uma quebra de linha real (`\n`) no meio da mensagem, pois o cliente entenderia essa quebra como o final da resposta.

Para representar uma quebra de linha dentro do protocolo, o servidor pode enviar:

```java
"\\n"
```

Em seguida, o cliente converte essa representação para uma quebra real antes de exibir:

```java
corrida.replace("\\n", "\n")
```

Assim:

```text
Servidor envia:

  origem: Feira de Santana\ndestino: Salvador

Cliente exibe:

  origem: Feira de Santana
  destino: Salvador
```

---

# Concorrência

O servidor pode atender múltiplos clientes.

Por isso, alterações nas estruturas compartilhadas devem ser feitas com cuidado, principalmente:

```java
registros
corridas
temp
```

As operações de reserva são especialmente importantes, pois dois clientes podem tentar reservar o último assento de uma corrida praticamente ao mesmo tempo.

Uma operação como:

```text
verificar assento
        ↓
reservar assento
```

deve ser tratada como uma operação atômica: outro cliente não deve conseguir reservar o mesmo assento entre essas duas etapas.

Corridas compostas exigem cuidado adicional, pois os dois trechos precisam ser confirmados juntos:

```text
CAR001 + CAR002

ou reserva as duas
ou não reserva nenhuma
```

Nunca deve existir uma reserva parcial de uma corrida composta. Por isso também, mesmo manipulando corridas normais, o sistema deve ter certeza que essa corrida também não faz parte de uma corrida composta feita para algum dos usuários.

---

# Resultados temporários de busca

As combinações de corridas encontradas durante uma busca são armazenadas temporariamente por usuário.

A estrutura utilizada pelo servidor é semelhante a:

```java
Map<String, List<String>> temp;
```

Isso é importante porque diferentes clientes podem realizar buscas simultaneamente, uma estrutura global acabaria atrapalhando a busca de outros clientes, lotando a lista com corridas aleatórias ou até apagando elas caso um trecho do programa use `temp.clear()` para limpar as corridas encontradas por um dos clientes.

---

# IDs de corridas

O sistema utiliza identificadores para diferenciar os tipos de corrida.

Exemplos:

```text
CAR001
CAR002
CAR003
```

para corridas normais, e:

```text
COM001
COM002
```

para corridas compostas.

Ao verificar o tipo de uma corrida, prefira:

```java
ID.startsWith("CAR")
```

ou:

```java
ID.startsWith("COM")
```

em vez de depender de posições específicas com `substring()`.

---

# Testes recomendados

Antes de considerar uma alteração concluída, teste pelo menos:

- cadastro de usuário sem veículo;
- cadastro de usuário com veículo;
- login;
- publicação de corrida sem paradas;
- publicação de corrida com uma ou mais paradas;
- busca de corrida direta;
- busca utilizando paradas;
- busca que gere corrida composta;
- reserva de corrida normal;
- reserva de corrida composta;
- tentativa de reservar corrida cheia;
- tentativa de reservar a própria corrida;
- cancelamento;
- histórico;
- dois clientes tentando reservar simultaneamente o último assento.

Para testar concorrência, abra mais de um terminal e execute múltiplos clientes enquanto mantém o mesmo servidor ativo.

---

# Problemas comuns

### Porta 5000 já está em uso

Caso apareça uma mensagem semelhante a:

```text
address already in use
```

algum processo já está utilizando a porta `5000`.

No Linux, é possível verificar com:

```bash
lsof -i :5000
```

Caso seja um servidor Java antigo que ficou aberto, encerre o processo antes de iniciar o container novamente.

Também verifique containers existentes:

```bash
docker ps
```

---

### Container com o mesmo nome já existe

Caso o Docker informe que já existe um container com o nome utilizado pelo projeto, simplesmente use:

```bash
make rebuild
```

Para parar o container caso esteja rodando e removê-lo, depois construir e executar o servidor novamente.

---

### Alterei o servidor, mas nada mudou

Provavelmente o container ainda está utilizando uma imagem criada com o código anterior.

Execute:

```bash
make rebuild
```

---

### Alterei o cliente, mas nada mudou

Recompile:

```bash
javac -d out vaijunto/src/model/*.java vaijunto/src/App/*.java
```

e execute novamente:

```bash
java -cp out vaijunto.src.App.Aplicacao
```

---

# Observações

Durante o desenvolvimento, mantenha o protocolo cliente-servidor documentado e sincronizado entre os dois lados.

Ao adicionar uma nova operação, uma boa sequência é:

```text
1. Definir a mensagem no protocolo
        ↓
2. Implementar a criação da requisição no cliente
        ↓
3. Adicionar a operação ao servidor
        ↓
4. Implementar o serviço correspondente
        ↓
5. Definir as respostas OK/ERRO
        ↓
6. Implementar a interpretação da resposta no cliente
        ↓
7. Testar com um cliente
        ↓
8. Testar com múltiplos clientes
```

Como o projeto envolve concorrência e comunicação por rede, uma funcionalidade funcionar com apenas um cliente não garante que ela esteja correta quando vários clientes estiverem conectados simultaneamente.
