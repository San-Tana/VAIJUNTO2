
package vaijunto.src.Server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.reflect.TypeToken;

import vaijunto.src.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;

public final class Consorcio {
    private final String empresa;
    private final List<URI> parceiros;

    // Associa o identificador de uma empresa ao seu endereço.
    private final Map<String, URI> enderecos = new ConcurrentHashMap<>();

    private final Map<String, Oferta> indice = new HashMap<>();

    // Protege a substituicao completa do indice.
    private final Object travaIndice = new Object();

    // Um mapa das cidades disponíveis
    private final Map<String, cidade> mapaCidades = new HashMap<>();

    // Itinerários compostos oferecidos e gerenciados por esse Consórcio.
    private final Map<String, corridaComposta> compostas = new ConcurrentHashMap<>();
    
    // Permite recuperar os dados completos de cada etapa a partir do ID da composta.
    private final Map<String, List<TrechoDisponivel>> etapasCompostas = new ConcurrentHashMap<>();

    // Itinerários compostos que já foram efetivamente reservados
    private final Map<String, ReservaComposta> reservasCompostas = new ConcurrentHashMap<>();

    // Envia requisições aos parceiros.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)) // Limita o tempo de estabelecimento da conexão
            .build();

    // Realiza a sincronização periódica e processa notificações de atualização.
    private final ScheduledExecutorService sincronizador =
            Executors.newSingleThreadScheduledExecutor();

    // Executa consultas aos proprietários das corridas em paralelo.
    private final ExecutorService consultas = Executors.newFixedThreadPool(10);

    // Cria um identificador aleatório para esta execução do servidor.
    private final String instancia = UUID.randomUUID().toString();

    // Contador de alterações no índice local.
    private long revisaoLocal = 0;
    // Guarda uma cópia do último conjunto de ofertas locais para verificar se ele mudou.
    private Map<String, Oferta> ultimoIndiceLocal = Map.of();

    // descreve os dados mínimos necessários para pesquisar uma corrida:
    // - empresa proprietária;
    // - ID local da corrida;
    // - sequência de cidades da rota.
    private record Oferta(String empresa, String id, List<String> cidades) {
        String chave() {
            return empresa + ":" + id;
        }
    }

    // Representa a resposta do endpoint '/api/status.'
    private record Status(String empresa) {}

    // Representa uma fotografia do índice de uma empresa em determinado momento. Contém:
    // - Identidade da empresa
    // - Identificador da instância
    // - Revisão do índice
    // - Lista de ofertas.
    private record Snapshot(
            String empresa,
            String instancia,
            long revisao,
            List<Oferta> ofertas) {}

    // Representa os dados atuais de uma corrida obtidos diretamente no servidor proprietário.
    public record Detalhes(
            String empresa,
            String id,
            boolean disponivel,
            int preco,
            String horario,
            String motorista,
            int vagas) {}
    
    // Representa trechos possiveis para uma corrida
    private record ConsultaTrecho(
            String proprietario,
            String origem,
            String destino) {}

    // Representa uma corrida confirmada para determinado trecho.
    public record TrechoDisponivel(
            Detalhes detalhes,
            String origem,
            String destino) {} 

    private record CaminhoEncontrado(
        List<TrechoDisponivel> trechos,
        List<String> paradas) {}
    
    // Representa a solicitação de uma reserva de corrida
    private record SolicitacaoReserva(
            String passageiro,
            String idCorrida,
            String origem,
            String destino) {}

    public record ReservaComposta(
        String idComposta,
        String transacaoId,
        String passageiro,
        List<TrechoDisponivel> etapas) {}        

    private record RespostaReserva(String resposta) {}

    private record SolicitacaoTransacao(
        String transacaoId,
        String passageiro,
        String idCorrida,
        String origem,
        String destino) {}

    public record SolicitacaoCancelamento(
        String passageiro,
        String idCorrida) {}

    // Diferente de erros, representa a estrutura JSON da resposta.
    private record Erro(String erro) {}

    // Preserva informações de tipo da lista de objetos Detalhes 
    private static final Type TIPO_DETALHES = new TypeToken<List<Detalhes>>() {}.getType();


    // Construtor recebe a identidade da empresa, a porta REST e a lista de parceiros
    public Consorcio(String empresa, int portaRest, List<URI> parceiros) throws IOException {
        this.empresa = Objects.requireNonNull(empresa); // Impede a inicialização com uma empresa nula.
        this.parceiros = List.copyOf(parceiros); // Cria uma cópia imutável da lista de parceiros.

        // Carrega os arquivos salvos uma segunda vez para recuperar reservasCompostas, 
        // evitando criar uma lista separada só para persistência
        PersistenciaJson.Snapshot snapshot = PersistenciaJson.carregar(empresa);

        if (snapshot != null && snapshot.reservasCompostas != null) {
            reservasCompostas.putAll(snapshot.reservasCompostas);
        }

        // InetSocketAddress representa a porta em que o servidor vai escutar.
        HttpServer servidor = HttpServer.create(new InetSocketAddress(portaRest), 0); 

        // Identifica a empresa
        servidor.createContext("/api/status", ex -> {
            if (!verificarMetodo(ex, "GET")) return;

            responder(ex, 200, new Status(this.empresa));
        });
        
        // Fornece as ofertas locais
        servidor.createContext("/api/index", this::enviarIndice);

        // Fornece detalhes atuais de várias corridas
        servidor.createContext("/api/corridas", this::enviarCorridas);

        // Recebe uma notificação de que pode haver alterações
        servidor.createContext("/api/atualizar", this::receberAviso);
        
        // Revisa o histórico de corridas de um usuário
        servidor.createContext("/api/historico", this::enviarHistorico);

        servidor.createContext("/api/reservar", this::reservar);

        servidor.createContext("/api/cancelar", this::cancelar);

        servidor.createContext("/api/reserva/preparar", this::prepararReserva);
        servidor.createContext("/api/reserva/confirmar", this::confirmarReserva);
        servidor.createContext("/api/reserva/abortar", this::abortarReserva);
        servidor.createContext("/api/reserva/cancelar", this::cancelarReservaComposta);

        // Permite que as requisições HTTP sejam tratadas por várias threads 
        servidor.setExecutor(Executors.newFixedThreadPool(10));

        // Inicia efetivamente o servidor.
        servidor.start();

        // Agenda a sincronização para começar imediatamente e repetir a cada cinco segundos após a execução anterior terminar.
        sincronizador.scheduleWithFixedDelay(
                this::sincronizar,
                0, 5, TimeUnit.SECONDS);

        System.out.println("REST de " + empresa + " na porta " + portaRest);
    }

    // Retorna o identificador da empresa
    public String empresa() {
        return empresa;
    }

    public Map<String, ReservaComposta> obterMapaReservasCompostas() {
        return reservasCompostas;
    }

    // Chamado depois de publicar ou alterar corridas locais. Avisa os parceiros sobre mudanças
    public void publicou() {
        atualizarLocal(); // Atualiza o próprio índice 

        // A notificação é executada em uma tarefa separada, sem obrigar a operação principal do cliente a esperar por todos os parceiros.
        sincronizador.execute(() -> {
            for (URI parceiro : parceiros) {
                try {
                    // 1. Constrói o endereço /api/atualizar do parceiro.
                    // 2. Define um timeout para a requisição.
                    // 3. Cria uma requisição POST sem corpo.
                    // 4. Constrói o objeto HttpRequest. 
                    HttpRequest req = HttpRequest.newBuilder(
                            parceiro.resolve("/api/atualizar"))
                            .timeout(Duration.ofSeconds(2))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    
                    // Envia a requisição e descarta o corpo da resposta.
                    http.send(req, HttpResponse.BodyHandlers.discarding());
                
                    // A interrupção é preservada e encerra o trabalho atual.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException e) {
                    System.err.println("Aviso nao entregue: " + parceiro);
                }
            }
        });
    }

    // Constrói um snapshot local coerente.
    private void atualizarLocal() {
        // Cria um mapa temporário para construir o novo índice sem alterar o índice compartilhado a cada corrida encontrada.
        Map<String, Oferta> atuais = new HashMap<>();

        // Consulta o serviço responsável pelas corridas locais
        for (corrida c : servicosServidor.listarCorridasLocais()) {
            if (c.getFinalizada()) continue; // Corridas finalizadas são ignoradas

            // constrói a rota:
            List<String> rota = new ArrayList<>(c.getParadas());

            // Cria a oferta com uma cópia imutável da rota e armazenamos a oferta no mapa temporário.
            // Usamos 'List.copyOf(rota)' para impedir que uma alteração posterior na lista original modifique silenciosamente os dados da oferta.
            Oferta oferta = new Oferta(empresa, c.getID(), List.copyOf(rota));
            atuais.put(oferta.chave(), oferta);

            System.out.println( "Corrida: " + c.getID() + " | Rota: " + c.getParadas());
        }

        /* Funciona como um cadeado: enquanto uma thread está executando uma seção protegida, 
        outra thread que precise do mesmo cadeado deve esperar. Não bloqueia diretamente os outros servidores da rede. */
        synchronized (travaIndice) {
            if (atuais.equals(ultimoIndiceLocal)) {
                return;
            }

            indice.entrySet().removeIf(e -> e.getValue().empresa().equals(empresa));

            indice.putAll(atuais);

            ultimoIndiceLocal = Map.copyOf(atuais);
            revisaoLocal++;

            reconstruirMapa();
        }

        System.out.println("=== ÍNDICE LOCAL ===");

        synchronized (travaIndice) {
            for (Oferta o : indice.values()) {
                System.out.println(o.chave() + " -> " + o.cidades());
            }
        }
    }

    // Atualiza todos os parceiros em sequencia.
    private void sincronizar() {
        try {
            System.out.println("[" + empresa + "] Executando sincronização...");
            atualizarLocal();

            for (URI parceiro : parceiros)
                atualizarParceiro(parceiro);

        } catch (RuntimeException e) {
            System.err.println("Erro na sincronizacao: " + e.getMessage());
        }
    }

    // Faz a comunicação de leitura com um parceiro.
    private void atualizarParceiro(URI parceiro) {
        try {
            System.out.println("Sincronizando com: " + parceiro);

            // envia uma requisição HTTP GET e devolve a resposta
            HttpResponse<String> statusResp = get(parceiro.resolve("/api/status"));

            // Abandona a tentativa se o parceiro não responder com HTTP 200 (OK)
            if (statusResp.statusCode() != 200) {
                falhaParceiro(parceiro, "Falha ao consultar status");
                return;
            }

            // Converte o corpo JSON em um objeto 'Status'.
            Status status = AdaptadorJson.ler(statusResp.body(), Status.class);

            // Verifica status ausente, sem identidade ou se é o próprio servidor.
            if (status == null || vazio(status.empresa()) || status.empresa().equals(empresa))
                return;

            String proprietario = status.empresa();

            // Busca o snapshot das ofertas do parceiro.
            HttpResponse<String> resp = get(parceiro.resolve("/api/index"));
            if (resp.statusCode() != 200) {
                falhaParceiro(parceiro, "Falha ao consultar índice");
                return;
            }

            // JSON é convertido em um 'Snapshot'.
            Snapshot snapshot = AdaptadorJson.ler(resp.body(), Snapshot.class);

            // Verifica se é um Snapshot válido
            if (!validarSnapshot(snapshot, proprietario)) {
                System.err.println("Indice invalido: " + proprietario);
                return;
            }

            Map<String, Oferta> novas = new HashMap<>();
            
            // Transforma a lista recebida em um mapa
            for (Oferta o : snapshot.ofertas())
                novas.put(o.chave(), o);

            // Remove as ofertas antigas do parceiro e coloca as novas sob a mesma trava
            synchronized (travaIndice) {
                indice.entrySet().removeIf(e -> e.getValue().empresa().equals(proprietario));

                indice.putAll(novas);

                // Guarda o endereço do proprietário
                enderecos.put(proprietario, parceiro);

                reconstruirMapa();
            }

            System.out.println("Índice recebido de: " + parceiro);
        
        // A thread foi interrompida; preservamos esse estado.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        // Houve falha de comunicação.
        } catch (IOException e) {
            falhaParceiro(parceiro, "Falha de rede");
        
        // Ocorreu um erro inesperado no processamento
        } catch (RuntimeException e) {
            System.err.println("Resposta invalida de " + parceiro + ": " + e.getMessage());
        }
    }

    private void falhaParceiro(URI parceiro, String motivo) {
        removerParceiroIndisponivel(parceiro);
        System.err.println(motivo + ": " + parceiro);
    }

    private void removerParceiroIndisponivel(URI parceiro) {
        synchronized (travaIndice) {
            // Descobre qual empresa estava associada a esse endereço.
            String proprietario = enderecos.entrySet().stream()
                .filter(e -> e.getValue().equals(parceiro))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

            // Se nunca conseguimos registrar esse parceiro, não há nada a remover.
            if (proprietario == null) {
                return;
            }

            // Remove as ofertas pertencentes ao parceiro.
            indice.entrySet().removeIf(
                e -> e.getValue().empresa().equals(proprietario)
            );

            // Remove o endereço conhecido.
            enderecos.remove(proprietario);

            // Atualiza o mapa de cidades sem as ofertas removidas.
            reconstruirMapa();
        }
    }

    // Recebe o snapshot e a identidade que esperamos encontrar nele
    private boolean validarSnapshot(Snapshot s, String proprietario) {
        if (s == null) {
            System.err.println("Snapshot nulo");
            return false;
        }

        if (!proprietario.equals(s.empresa())) {
            System.err.println("Empresa do snapshot divergente: " + s.empresa());
            return false;
        }

        if (vazio(s.instancia())) {
            System.err.println("UUID da instancia vazio: " + s.instancia());
            return false;
        }

        if (s.revisao() < 0) {
            System.err.println("Revisao negativa: " + s.revisao());
            return false;
        }

        if (s.ofertas() == null) {
            System.err.println("Lista de ofertas nula");
            return false;
        }

        Set<String> ids = new HashSet<>();

        for (Oferta o : s.ofertas()) {
            if (o == null) {
                System.err.println("Oferta nula");
                return false;
            }

            if (!proprietario.equals(o.empresa())) {
                System.err.println("Empresa da oferta divergente: " + o.empresa());
                return false;
            }

            if (vazio(o.id())) {
                System.err.println("ID da oferta vazio: " + o.id());
                return false;
            }

            if (o.cidades() == null || o.cidades().size() < 2) {
                System.err.println("Rota invalida para " + o.id() + ": " + o.cidades());
                return false;
            }

            if (!ids.add(o.id())) {
                System.err.println("ID duplicado no snapshot: " + o.id());
                return false;
            }

            for (String cidade : o.cidades()) {
                if (vazio(cidade)) {
                    System.err.println("Cidade vazia na rota de " + o.id() + ": " + o.cidades());
                    return false;
                }
            }
        }

        return true;
    }

    // Executado quando outro servidor solicita /api/index.
    private void enviarIndice(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "GET")) return; // Garante que a requisição seja GET

        // Atualiza as ofertas locais para evitar que um parceiro receba informações desatualizadas.
        atualizarLocal();

        //O stream() percorre as ofertas, e filter() seleciona apenas as pertencentes à empresa atual.
        synchronized (travaIndice) {
            List<Oferta> locais = indice.values().stream()
                    .filter(o -> o.empresa().equals(empresa))
                    .toList();
            
            // Criamos o snapshot e o enviamos com HTTP 200 (OK)
            responder(ex, 200, new Snapshot(empresa, instancia, revisaoLocal, locais));
        }
    }

    // Busca uma cidade. Se não existe, cria ela
    private cidade obterCidade(String nome) {
        return mapaCidades.computeIfAbsent(nome, cidade::new);
    }

    public ReservaComposta obterReservaComposta(String idComposta) {
        return reservasCompostas.get(idComposta);
    }

    // Adiciona corridas ao mapa de cidades
    private void atualizarMapa(Oferta oferta) {
        
        List<String> rota = oferta.cidades();

        for (int i = 0; i < rota.size(); i++) {
            cidade atual = obterCidade(rota.get(i));

            // Registra a corrida de forma global e a posição da cidade na rota.
            atual.addCorrida(oferta.chave(), oferta.empresa(), i);

            // Cria conexões bidirecionais entre cidades consecutivas.
            if (i > 0) {
                cidade anterior = obterCidade(rota.get(i - 1));

                atual.addVizinha(anterior);
                anterior.addVizinha(atual);
            }
        }
    }

    // Reconstroí o mapa a partir do índice de ofertas recebido dos servidores.
    private void reconstruirMapa() {
        synchronized (travaIndice) {

            mapaCidades.clear();

            for (Oferta oferta : indice.values()) {
                atualizarMapa(oferta);
            }
        }
    }

    // Consulta a quantidade atual de vagas deve ser consultada no proprietário.
    private void enviarCorridas(HttpExchange ex) throws IOException {
        
        if (!verificarMetodo(ex, "GET")) {
            return;
        }

        String query = ex.getRequestURI().getRawQuery();

        String parametroIds = parametro(query, "ids");
        String origem = parametro(query, "origem");
        String destino = parametro(query, "destino");

        if (vazio(parametroIds) || vazio(origem) || vazio(destino)) {
            responder(ex, 400, new Erro(erros.BUSCA_INVALIDA));
            return;
        }

        String[] ids = parametroIds.split(",");

        if (ids.length > 40) {
            responder(ex, 400, new Erro(erros.LIMITE_DE_IDS));
            return;
        }

        List<Detalhes> detalhes = new ArrayList<>();

        for (String id : new LinkedHashSet<>(Arrays.asList(ids))) {

            if (vazio(id)) {
                continue;
            }

            String proprietario = identificadorGlobal.obterEmpresa(id);

            if (!empresa.equals(proprietario)) {
                continue;
            }

            String idLocal = identificadorGlobal.obterIdLocal(id);

            corrida c = servicosServidor.obterCorridaLocal(idLocal);

            if (c == null || c.getFinalizada()) {
                continue;
            }

            boolean disponivel =  servicosServidor.corridaDisponivel(idLocal, origem, destino);

            detalhes.add(new Detalhes(
                    empresa,
                    id,
                    disponivel,
                    c.getPreco(),
                    c.getHorario().toString(),
                    c.getmotorista(),
                    vagasDisponiveis(c, origem, destino)));
        }

        responder(ex, 200, detalhes);
    }

    // Agenda uma sincronização ao receber POST /api/atualizar
    private void receberAviso(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        // Responde com status 202, indicando que a solicitação foi aceita.
        responder(ex, 202, Map.of("recebido", true));

        // O executor serializa este trabalho com o polling.
        sincronizador.execute(this::sincronizar);
    }

    // Responde a requisição de histórico de um cliente de outro servidor
    private void enviarHistorico(HttpExchange ex) throws IOException {

        if (!verificarMetodo(ex, "GET")) {
            return;
        }

        // Recebe o identificador global do passageiro.
        String passageiro = parametro(ex.getRequestURI().getRawQuery(), "passageiro");

        if (vazio(passageiro) || !passageiro.matches("[^:]+:[^:]+")) {
            responder(ex, 400, new Erro("PASSAGEIRO_INVALIDO"));
            return;
        }

        // Consulta somente as corridas hospedadas neste servidor.
        List<String> resultado = servicosServidor.historicoLocal(passageiro);

        // Mesmo que não existam reservas, retorna uma lista vazia.
        responder(ex, 200, resultado);
    }

    // Consulta o histórico de um usuário, procurando corridas locais e pedindo corridas de outros servidores
    public String historico(String passageiro) {
        if (vazio(passageiro) || !passageiro.matches("[^:]+:[^:]+")) {
            return "ERRO|" + erros.PASSAGEIRO_INVALIDO;
        }

        // Começa consultando as corridas hospedadas localmente.
        List<String> resultados = servicosServidor.historicoLocal(passageiro);

        // Cada parceiro será consultado uma única vez.
        List<CompletableFuture<List<String>>> futuros = new ArrayList<>();

        for (URI parceiro : parceiros) {
            futuros.add(
                CompletableFuture.supplyAsync(() -> {

                    try {
                        String parametro = URLEncoder.encode(
                            passageiro,
                            StandardCharsets.UTF_8);

                        URI uri = parceiro.resolve("/api/historico?passageiro=" + parametro);

                        HttpResponse<String> resposta = get(uri);

                        if (resposta.statusCode() != 200) {
                            return null;
                        }

                        return AdaptadorJson.ler(
                            resposta.body(),
                            new TypeToken<List<String>>() {}.getType());

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;

                    } catch (IOException | RuntimeException e) {
                        System.err.println("Falha consultando histórico em " + parceiro);
                        return null;
                    }

                }, consultas)
            );
        }

        // Reúne as respostas dos parceiros.
        boolean parcial = false;

        for (CompletableFuture<List<String>> futuro : futuros) {

            try {
                List<String> recebidos = futuro.get(5, TimeUnit.SECONDS);

                if (recebidos == null) {
                    parcial = true;
                } else {
                    resultados.addAll(recebidos);
                }

            } catch (TimeoutException e) {
                futuro.cancel(true);
                parcial = true;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERRO|" + erros.BUSCA_INTERROMPIDA;

            } catch (ExecutionException e) {
                parcial = true;
            }
        }

        // Identifica as etapas pertencentes às reservas compostas
        // e prepara os registros para exibição.
        Set<String> etapasCompostas = new HashSet<>();
        List<String> registrosCompostos = new ArrayList<>();

        for (ReservaComposta reserva : reservasCompostas.values()) {

            if (!passageiro.equals(reserva.passageiro())) {
                continue;
            }

            List<TrechoDisponivel> etapas = reserva.etapas();

            if (etapas == null || etapas.isEmpty()) {
                continue;
            }

            int precoTotal = 0;
            List<String> dadosEtapas = new ArrayList<>();
            
            boolean etapasValidas = etapas.stream().allMatch(e ->
                e != null
                && e.detalhes() != null
                && !vazio(e.detalhes().id())
                && !vazio(e.origem())
                && !vazio(e.destino()));

            if (!etapasValidas) {
                continue;
            }

            for (TrechoDisponivel etapa : etapas) {

                Detalhes detalhes = etapa.detalhes();

                // O ID já é global: não gerar outro prefixo.
                String idGlobal = detalhes.id();

                etapasCompostas.add(idGlobal);
                precoTotal += detalhes.preco();

                String dados = idGlobal + ";"
                    + etapa.origem() + ";"
                    + etapa.destino() + ";"
                    + detalhes.preco() + ";"
                    + detalhes.horario() + ";"
                    + detalhes.motorista() + ";"
                    + detalhes.vagas();

                dadosEtapas.add(dados);
            }

            // Não apresenta uma reserva incompleta.
            if (dadosEtapas.size() != etapas.size()) {
                continue;
            }

            String registro = "COMPOSTA;"
                + reserva.idComposta() + ";"
                + etapas.get(0).origem() + ";"
                + etapas.get(etapas.size() - 1).destino() + ";"
                + precoTotal + ";"
                + String.join("#", dadosEtapas);

            registrosCompostos.add(registro);
        }

        // Retira as etapas compostas dos registros de corridas diretas.
        resultados.removeIf(registro -> {
            String[] campos = registro.split(";", -1);

            return campos.length > 1
                && campos[0].equals("DIRETA")
                && etapasCompostas.contains(campos[1]);
        });

        // Adiciona as reservas compostas.
        resultados.addAll(registrosCompostos);

        // Ordena os resultados para facilitar a visualização.
        Collections.sort(resultados);

        if (resultados.isEmpty() && !parcial) {
            return "ERRO|" + erros.HISTORICO_VAZIO;
        }

        String status = parcial ? "PARCIAL" : "COMPLETO";

        return "OK|HISTORICO_" + status + "|" +
            String.join("~", resultados);
    }

    // Envia uma solicitação de reserva ou cancelamento a um parceiro.
    private String enviarSolicitacao(
            URI base,
            String endpoint,
            Object solicitacao)
            throws IOException, InterruptedException {

        String json = AdaptadorJson.gerar(solicitacao);

        HttpRequest requisicao = HttpRequest.newBuilder(
                base.resolve(endpoint))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(
                json, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resposta = http.send(
            requisicao,
            HttpResponse.BodyHandlers.ofString()
        );

        if (resposta.statusCode() != 200) {
            System.out.println("Erro HTTP: " + resposta.statusCode());
            System.out.println("Corpo recebido: " + resposta.body());

            return "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
        }

        RespostaReserva resultado = AdaptadorJson.ler(
            resposta.body(), RespostaReserva.class
        );

        if (resultado == null || vazio(resultado.resposta())) {
            return "ERRO|" + erros.RESPOSTA_INVALIDA;
        }

        return resultado.resposta();
    }

    private String enviarSolicitacaoTransacao(URI base, String endpoint, SolicitacaoTransacao solicitacao)
        throws IOException, InterruptedException {

        String json = AdaptadorJson.gerar(solicitacao);

        HttpRequest requisicao = HttpRequest.newBuilder(
                base.resolve(endpoint))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(
                json, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resposta = http.send(
            requisicao,
            HttpResponse.BodyHandlers.ofString()
        );

        if (resposta.statusCode() != 200) {
            System.out.println("Erro HTTP: " + resposta.statusCode());
            System.out.println("Corpo recebido: " + resposta.body());

            return "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
        }

        RespostaReserva resultado = AdaptadorJson.ler(
            resposta.body(), RespostaReserva.class
        );

        if (resultado == null || vazio(resultado.resposta())) {
            return "ERRO|" + erros.RESPOSTA_INVALIDA;
        }

        return resultado.resposta();
    }

    // Recebe uma solicitação de reserva de outro servidor.
    private void reservar(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        SolicitacaoReserva solicitacao;

        try {
            String corpo = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
            );

            solicitacao = AdaptadorJson.ler(
                corpo, SolicitacaoReserva.class
            );
        } catch (RuntimeException e) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        if (solicitacao == null
                || vazio(solicitacao.passageiro())
                || vazio(solicitacao.idCorrida())
                || vazio(solicitacao.origem())
                || vazio(solicitacao.destino())
                || !solicitacao.passageiro().matches("[^:]+:[^:]+")
                || !solicitacao.idCorrida().matches("[^:]+:[^:]+")) {

            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        // Confirma que a corrida pertence a este servidor.
        String proprietario =
            identificadorGlobal.obterEmpresa(solicitacao.idCorrida());

        if (!empresa.equals(proprietario)) {
            responder(ex, 403, new Erro("PROPRIETARIO_INCORRETO"));
            return;
        }

        String idLocal = identificadorGlobal.obterIdLocal(solicitacao.idCorrida());

        // O passageiro pertence a outro servidor, portanto não procuramos
        // seu cadastro nos registros locais.
        String resultado = servicosServidor.reservarCorridaGlobal(
            solicitacao.passageiro(),
            null,
            idLocal, solicitacao.idCorrida(),
            solicitacao.origem(),
            solicitacao.destino()
        );

        responder(ex, 200, new RespostaReserva(resultado));
    }

    public String reservarDireta(String usuario, String idGlobal, String origem, String destino) {

        if (vazio(usuario) || vazio(idGlobal)
                || vazio(origem) || vazio(destino)) {
            return "ERRO|" + erros.BUSCA_INVALIDA;
        }

        try {
            String proprietario = identificadorGlobal.obterEmpresa(idGlobal);

            String idLocal = identificadorGlobal.obterIdLocal(idGlobal);

            String passageiro = identificadorGlobal.gerar(empresa, usuario);

            // A corrida pertence a este servidor.
            if (empresa.equals(proprietario)) {
                return servicosServidor.reservarCorridaGlobal(
                    passageiro, usuario, idLocal, idGlobal, origem, destino
                );
            }

            // A corrida pertence a outro servidor.
            URI base = enderecos.get(proprietario);

            if (base == null) {
                return "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
            }

            SolicitacaoReserva solicitacao =
                new SolicitacaoReserva(
                    passageiro, idGlobal, origem, destino
                );

            return enviarSolicitacao(
                base, "/api/reservar", solicitacao
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERRO|" + erros.BUSCA_INTERROMPIDA;

        } catch (IOException | RuntimeException e) {
            System.err.println(
                "Erro na reserva direta: " + e.getMessage()
            );
            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }
    }

    // Reserva todas as etapas de um itinerário composto.
    public synchronized String reservarComposta(String usuario, String idComposta) {
        if (vazio(usuario) || vazio(idComposta)) {
            return "ERRO|" + erros.BUSCA_INVALIDA;
        }

        List<TrechoDisponivel> etapas = etapasCompostas.get(idComposta);

        if (etapas == null || etapas.isEmpty() || !compostas.containsKey(idComposta)) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        String passageiro = identificadorGlobal.gerar(empresa, usuario);
        String transacaoId = UUID.randomUUID().toString();

        List<TrechoDisponivel> preparadas = new ArrayList<>();
        Set<String> idsProcessados = new HashSet<>();

        // FASE 1: PREPARE
        for (TrechoDisponivel etapa : etapas) {

            Detalhes detalhes = etapa.detalhes();

            if (detalhes == null || vazio(detalhes.id())
                    || vazio(etapa.origem()) || vazio(etapa.destino())) {

                abortarEtapasPreparadas(preparadas, passageiro, transacaoId);

                return "ERRO|" + erros.ETAPA_INVALIDA;
            }

            String idGlobal = detalhes.id();

            if (!idsProcessados.add(idGlobal)) {

                abortarEtapasPreparadas(
                    preparadas, passageiro, transacaoId
                );

                return "ERRO|" + erros.ETAPA_DUPLICADA;
            }

            String proprietario;
            String idLocal;

            try {
                proprietario = identificadorGlobal.obterEmpresa(idGlobal);
                idLocal = identificadorGlobal.obterIdLocal(idGlobal);

            } catch (RuntimeException e) {

                abortarEtapasPreparadas(
                    preparadas, passageiro, transacaoId
                );

                return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
            }

            SolicitacaoTransacao solicitacao = new SolicitacaoTransacao(
                transacaoId,
                passageiro,
                idGlobal,
                etapa.origem(),
                etapa.destino()
            );

            String resultado;
            boolean preparacaoAmbigua = false;

            try {
                if (empresa.equals(proprietario)) {

                    resultado = servicosServidor.prepararReservaGlobal(
                        transacaoId,
                        passageiro,
                        idLocal,
                        etapa.origem(),
                        etapa.destino()
                    );

                } else {

                    URI base = enderecos.get(proprietario);

                    if (base == null) {
                        resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
                    } else {
                        resultado = enviarPrepare(base, solicitacao);
                    }
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                resultado = "ERRO|" + erros.BUSCA_INTERROMPIDA;

            } catch (IOException e) {

                // O servidor pode ter preparado a reserva,
                // mas a resposta pode ter sido perdida.
                preparacaoAmbigua = true;
                resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;

            } catch (RuntimeException e) {

                resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
            }

            if (resultado == null || !resultado.startsWith("OK|")) {
                List<TrechoDisponivel> paraAbortar =
                    new ArrayList<>(preparadas);

                // Se houve falha de comunicação, incluímos também
                // a etapa atual, pois ela pode ter sido preparada.
                if (preparacaoAmbigua) {
                    paraAbortar.add(etapa);
                }

                boolean abortouTudo = abortarEtapasPreparadas(
                    paraAbortar, passageiro, transacaoId
                );

                if (!abortouTudo) {
                    return "ERRO|" + erros.RESERVA_PARCIAL;
                }

                return resultado == null
                    ? "ERRO|" + erros.FALHA_NA_RESERVA
                    : resultado;
            }

            preparadas.add(etapa);
        }

        // FASE 2: COMMIT
        // Todas as etapas foram preparadas.
        for (TrechoDisponivel etapa : preparadas) {

            String idGlobal = etapa.detalhes().id();
            String proprietario = identificadorGlobal.obterEmpresa(idGlobal);
            String idLocal = identificadorGlobal.obterIdLocal(idGlobal);

            SolicitacaoTransacao solicitacao = new SolicitacaoTransacao(
                transacaoId,
                passageiro,
                idGlobal,
                etapa.origem(),
                etapa.destino()
            );

            String resultado;

            try {
                if (empresa.equals(proprietario)) {

                    resultado = servicosServidor.confirmarReservaGlobal(
                        transacaoId, idLocal
                    );

                } else {

                    URI base = enderecos.get(proprietario);

                    if (base == null) {
                        resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
                    } else {
                        resultado = enviarCommit(base, solicitacao);
                    }
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                resultado = "ERRO|" + erros.BUSCA_INTERROMPIDA;

            } catch (IOException | RuntimeException e) {
                resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
            }

            if (resultado == null || !resultado.startsWith("OK|")) {

                // Não abortar aqui!
                // A decisão de COMMIT já foi tomada.
                // A transação precisa ser recuperada.
                return "ERRO|" + erros.TRANSACAO_PENDENTE + "|" + transacaoId;
            }
        }

        List<String> idsCorridas = new ArrayList<>();

        for (TrechoDisponivel etapa : etapas) {
            idsCorridas.add(etapa.detalhes().id());
        }

        // Guarda a relação entre a composta e suas etapas reservadas.
        ReservaComposta reserva = new ReservaComposta(
                idComposta,
                transacaoId,
                passageiro,
                new ArrayList<>(etapas)
        );

        reservasCompostas.put(idComposta, reserva);
        
        // Persiste a reserva antes de atualizar o histórico.
        if (!servicosServidor.salvarDados()) {
            System.err.println(
                "Reserva confirmada, mas não foi possível persistir "
                + "seu registro: " + idComposta
            );

            return "ERRO|" + erros.TRANSACAO_PENDENTE + "|" + transacaoId;
        }

        // Atualiza o histórico do passageiro.
        if (!servicosServidor.registrarHistoricoComposto(
                passageiro, idsCorridas)) {

            System.err.println(
                "Reserva confirmada e registrada, mas houve falha "
                + "ao atualizar o histórico: " + idComposta
            );

            return "ERRO|" + erros.HISTORICO_NAO_SALVO;
        }

        return "OK|" + okays.CORRIDA_RESERVADA + "|" + idComposta;
    }

    public String cancelarDireta(String usuario, String idGlobal) {
        if (vazio(usuario) || vazio(idGlobal)) {
            return "ERRO|" + erros.MENSAGEM_INVALIDA;
        }

        try {
            String proprietario =
                identificadorGlobal.obterEmpresa(idGlobal);

            String idLocal =
                identificadorGlobal.obterIdLocal(idGlobal);

            String passageiro =
                identificadorGlobal.gerar(empresa, usuario);

            // A corrida pertence a este servidor.
            if (empresa.equals(proprietario)) {
                return servicosServidor.cancelarCorridaLocal(
                    usuario, idLocal, idGlobal
                );
            }

            // A corrida pertence a outro servidor.
            URI base = enderecos.get(proprietario);

            if (base == null) {
                return "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
            }

            SolicitacaoCancelamento solicitacao =
                new SolicitacaoCancelamento(passageiro, idGlobal);

            return enviarSolicitacao(
                base, "/api/cancelar", solicitacao
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERRO|" + erros.BUSCA_INTERROMPIDA;

        } catch (IOException | RuntimeException e) {
            System.err.println(
                "Erro no cancelamento direto: " + e.getMessage()
            );
            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }
    }



    // Cancela todas as etapas de uma reserva composta.
    public synchronized String cancelarComposta(
            String usuario, String idComposta) {

        if (vazio(usuario) || vazio(idComposta)) {
            return "ERRO|" + erros.BUSCA_INVALIDA;
        }

        ReservaComposta reserva = reservasCompostas.get(idComposta);

        if (reserva == null) {
            return "ERRO|" + erros.CORRIDA_NAO_RESERVADA;
        }

        String passageiro = identificadorGlobal.gerar(empresa, usuario);

        if (!reserva.passageiro().equals(passageiro)) {
            return "ERRO|" + erros.CORRIDA_NAO_RESERVADA;
        }

        List<TrechoDisponivel> etapas = reserva.etapas();
        

        // Cancela as etapas na ordem inversa.
        for (int i = etapas.size() - 1; i >= 0; i--) {

            TrechoDisponivel etapa = etapas.get(i);
            Detalhes detalhes = etapa.detalhes();

            if (detalhes == null || vazio(detalhes.id())) {
                return "ERRO|" + erros.ETAPA_INVALIDA;
            }

            String idGlobal = detalhes.id();
            String proprietario;
            String idLocal;

            try {
                proprietario = identificadorGlobal.obterEmpresa(idGlobal);
                idLocal = identificadorGlobal.obterIdLocal(idGlobal);

            } catch (RuntimeException e) {
                return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
            }

            SolicitacaoTransacao solicitacao = new SolicitacaoTransacao(
                reserva.transacaoId(),
                reserva.passageiro(),
                idGlobal,
                etapa.origem(),
                etapa.destino()
            );

            String resultado;

            try {
                if (empresa.equals(proprietario)) {

                    resultado = servicosServidor.cancelarReservaCompostaGlobal(
                        reserva.transacaoId(),
                        reserva.passageiro(),
                        idLocal,
                        idGlobal,
                        etapa.origem(),
                        etapa.destino()
                    );

                } else {

                    URI base = enderecos.get(proprietario);

                    if (base == null) {
                        resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
                    } else {
                        resultado = enviarCancelamento(base, solicitacao);
                    }
                }

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                resultado = "ERRO|" + erros.BUSCA_INTERROMPIDA;

            } catch (IOException | RuntimeException e) {

                resultado = "ERRO|" + erros.SERVIDOR_INDISPONIVEL;
            }

            if (resultado == null || !resultado.startsWith("OK|")) {

                System.err.println(
                    "Falha ao cancelar a etapa " + idGlobal
                    + " da composta " + idComposta
                );

                return "ERRO|" + erros.RESERVA_PARCIAL;
            }
            
        }

        // Todas as etapas foram canceladas.
        if (!servicosServidor.removerHistoricoComposto(
                reserva.passageiro(),
                etapas.stream()
                    .map(e -> e.detalhes().id())
                    .toList())) {

            System.err.println(
                "Etapas canceladas, mas houve falha ao atualizar o histórico: "
                + idComposta
            );

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }
        // Remove a reserva composta do histórico de reservas ativas.
        reservasCompostas.remove(idComposta);

        // Persiste a remoção.
        if (!servicosServidor.salvarDados()) {

            // Restaura a entrada em memória.
            reservasCompostas.put(idComposta, reserva);

            System.err.println("Etapas canceladas, mas não foi possível persistir "
                + "a remoção da reserva composta: " + idComposta);

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.CORRIDA_CANCELADA;
    }



    private String enviarPrepare(URI base, SolicitacaoTransacao s) throws IOException, InterruptedException {
        return enviarSolicitacaoTransacao(
            base, "/api/reserva/preparar", s);
    }


    private String enviarCommit(URI base, SolicitacaoTransacao s) throws IOException, InterruptedException {
        return enviarSolicitacaoTransacao(
            base, "/api/reserva/confirmar", s);
    }


    private String enviarAbort(URI base, SolicitacaoTransacao s) throws IOException, InterruptedException {
        return enviarSolicitacaoTransacao(
            base, "/api/reserva/abortar", s);
    }

    private String enviarCancelamento(URI base, SolicitacaoTransacao s) throws IOException, InterruptedException {
        return enviarSolicitacaoTransacao(
            base, "/api/reserva/cancelar", s);
    }

    private SolicitacaoTransacao lerSolicitacaoTransacao(HttpExchange ex) throws IOException {
        String corpo = new String(
            ex.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8);

        return AdaptadorJson.ler(corpo, SolicitacaoTransacao.class);
    }

    private boolean validarSolicitacaoTransacao(SolicitacaoTransacao s) {
        return s != null && !vazio(s.transacaoId()) && !vazio(s.passageiro()) && !vazio(s.idCorrida())
            && !vazio(s.origem()) && !vazio(s.destino())
            && s.passageiro().matches("[^:]+:[^:]+") && s.idCorrida().matches("[^:]+:[^:]+");
    }

    private void prepararReserva(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        SolicitacaoTransacao s;

        try {
            s = lerSolicitacaoTransacao(ex);
        } catch (RuntimeException e) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        if (!validarSolicitacaoTransacao(s)) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        String proprietario =
            identificadorGlobal.obterEmpresa(s.idCorrida());

        if (!empresa.equals(proprietario)) {
            responder(ex, 403, new Erro("PROPRIETARIO_INCORRETO"));
            return;
        }

        String idLocal =
            identificadorGlobal.obterIdLocal(s.idCorrida());

        String resultado = servicosServidor.prepararReservaGlobal(
            s.transacaoId(),
            s.passageiro(),
            idLocal,
            s.origem(),
            s.destino()
        );

        responder(ex, 200, new RespostaReserva(resultado));
    }

    private void confirmarReserva(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        SolicitacaoTransacao s;

        try {
            s = lerSolicitacaoTransacao(ex);
        } catch (RuntimeException e) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        if (!validarSolicitacaoTransacao(s)) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        String proprietario =
            identificadorGlobal.obterEmpresa(s.idCorrida());

        if (!empresa.equals(proprietario)) {
            responder(ex, 403, new Erro("PROPRIETARIO_INCORRETO"));
            return;
        }

        String idLocal =
            identificadorGlobal.obterIdLocal(s.idCorrida());

        String resultado = servicosServidor.confirmarReservaGlobal(
            s.transacaoId(),
            idLocal
        );

        responder(ex, 200, new RespostaReserva(resultado));
    }

    private void abortarReserva(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        SolicitacaoTransacao s;

        try {
            s = lerSolicitacaoTransacao(ex);
        } catch (RuntimeException e) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        if (!validarSolicitacaoTransacao(s)) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        String proprietario =
            identificadorGlobal.obterEmpresa(s.idCorrida());

        if (!empresa.equals(proprietario)) {
            responder(ex, 403, new Erro("PROPRIETARIO_INCORRETO"));
            return;
        }

        String idLocal =
            identificadorGlobal.obterIdLocal(s.idCorrida());

        String resultado = servicosServidor.abortarReservaGlobal(
            s.transacaoId(),
            idLocal
        );

        responder(ex, 200, new RespostaReserva(resultado));
    }

    private void cancelarReservaComposta(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        SolicitacaoTransacao s;

        try {
            s = lerSolicitacaoTransacao(ex);
        } catch (RuntimeException e) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        if (!validarSolicitacaoTransacao(s)) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        String proprietario =
            identificadorGlobal.obterEmpresa(s.idCorrida());

        if (!empresa.equals(proprietario)) {
            responder(ex, 403, new Erro("PROPRIETARIO_INCORRETO"));
            return;
        }

        String idLocal =
            identificadorGlobal.obterIdLocal(s.idCorrida());

        String resultado =
            servicosServidor.cancelarReservaCompostaGlobal(
                s.transacaoId(),
                s.passageiro(),
                idLocal,
                s.idCorrida(),
                s.origem(),
                s.destino()
            );

        responder(ex, 200, new RespostaReserva(resultado));
    }

    private boolean abortarEtapasPreparadas(List<TrechoDisponivel> preparadas, String passageiro, String transacaoId) {

        boolean sucesso = true;

        for (TrechoDisponivel etapa : preparadas) {

            String idGlobal = etapa.detalhes().id();
            String proprietario = identificadorGlobal.obterEmpresa(idGlobal);
            String idLocal = identificadorGlobal.obterIdLocal(idGlobal);

            SolicitacaoTransacao solicitacao = new SolicitacaoTransacao(
                transacaoId,
                passageiro,
                idGlobal,
                etapa.origem(),
                etapa.destino()
            );

            try {
                String resultado;

                if (empresa.equals(proprietario)) {

                    resultado = servicosServidor.abortarReservaGlobal(
                        transacaoId, idLocal
                    );

                } else {

                    URI base = enderecos.get(proprietario);

                    if (base == null) {
                        sucesso = false;
                        continue;
                    }

                    resultado = enviarAbort(base, solicitacao);
                }

                if (resultado == null || !resultado.startsWith("OK|")) {
                    sucesso = false;
                }

            } catch (IOException | InterruptedException | RuntimeException e) {

                sucesso = false;

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return sucesso;
    }

    // Recebe uma solicitação para desfazer uma reserva.
    private void cancelar(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        SolicitacaoReserva solicitacao;

        try {
            String corpo = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
            );

            solicitacao = AdaptadorJson.ler(
                corpo, SolicitacaoReserva.class
            );
        } catch (RuntimeException e) {
            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        if (solicitacao == null
                || vazio(solicitacao.passageiro())
                || vazio(solicitacao.idCorrida())
                || !solicitacao.passageiro().matches("[^:]+:[^:]+")
                || !solicitacao.idCorrida().matches("[^:]+:[^:]+")) {

            responder(ex, 400, new Erro("SOLICITACAO_INVALIDA"));
            return;
        }

        String proprietario =
            identificadorGlobal.obterEmpresa(solicitacao.idCorrida());

        if (!empresa.equals(proprietario)) {
            responder(ex, 403, new Erro("PROPRIETARIO_INCORRETO"));
            return;
        }

        String idLocal =
            identificadorGlobal.obterIdLocal(solicitacao.idCorrida());

        String resultado = servicosServidor.cancelarCorridaGlobal(
            solicitacao.passageiro(),
            null,
            idLocal,
            solicitacao.idCorrida()
        );

        if (resultado.startsWith("OK")) {
            publicou();
        }

        responder(ex, 200, new RespostaReserva(resultado));
    }

    private int vagasDisponiveis(corrida c, String origem, String destino) {
        int[] reserva;

        try {
            reserva = c.vetorReserva(origem, destino);
        } catch (IllegalArgumentException e) {
            return 0;
        }

        List<Integer> vagas = c.getTrechos();
        int minimo = Integer.MAX_VALUE;

        for (int i = 0; i < reserva.length; i++) {
            if (reserva[i] == 1) {
                minimo = Math.min(minimo, vagas.get(i));
            }
        }

        return minimo == Integer.MAX_VALUE ? 0 : minimo;
    }

    private LocalDateTime converterHorario(String horario) {
        if (horario == null || horario.isBlank()) {
            throw new IllegalArgumentException("Horario vazio");
        }

        String[] partes = horario.split("&", -1);

        if (partes.length != 4) {
            throw new IllegalArgumentException("Formato de horario invalido");
        }

        int dia = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);
        int ano = Integer.parseInt(partes[2]);

        String[] horaMinuto = partes[3].split(":", -1);

        if (horaMinuto.length != 2) {
            throw new IllegalArgumentException("Horario invalido");
        }

        int hora = Integer.parseInt(horaMinuto[0]);
        int minuto = Integer.parseInt(horaMinuto[1]);

        return LocalDateTime.of(ano, mes, dia, hora, minuto);
    }

    // Recebe a consulta do cliente e combina o índice local com as consultas aos proprietários.
    public String buscar(String dados) {
        System.out.println("=== INICIANDO BUSCA ===");
        
        String[] partes = dados.split(";", -1);

        if (partes.length < 2 || vazio(partes[0]) || vazio(partes[1])) {
            return "ERRO|" + erros.BUSCA_INVALIDA;
        }

        String origem = partes[0].trim();
        String destino = partes[1].trim();

        if (origem.equals(destino)) {
            return "ERRO|" + erros.BUSCA_INVALIDA;
        }

        // 1. Gera os trechos possíveis usando o índice replicado.
        Map<ConsultaTrecho, List<String>> candidatos =
                gerarTrechosCandidatos();

        // 2. Consulta os proprietários e confirma a disponibilidade.
        List<TrechoDisponivel> trechosDisponiveis =
                consultarTodosTrechos(candidatos);

        // 3. Separa as corridas diretas.
        List<TrechoDisponivel> diretas = new ArrayList<>();

        for (TrechoDisponivel trecho : trechosDisponiveis) {

            if (trecho.origem().equals(origem)
                    && trecho.destino().equals(destino)) {

                diretas.add(trecho);
            }
        }

        // 4. Organiza os trechos por cidade de origem.
        Map<String, List<TrechoDisponivel>> porOrigem = new HashMap<>();

        for (TrechoDisponivel trecho : trechosDisponiveis) {

            porOrigem.computeIfAbsent(
                    trecho.origem(),
                    k -> new ArrayList<>()
            ).add(trecho);
        }

        // 5. Procura soluções compostas por quantidade de corridas.
        List<CaminhoEncontrado> solucoes = new ArrayList<>();

        int quantidade = 2;

        while (quantidade <= trechosDisponiveis.size()) {

            buscarCaminhos(origem, destino, quantidade,
                    null,
                    new ArrayList<>(),
                    new ArrayList<>(List.of(origem)),
                    new HashSet<>(),
                    porOrigem, solucoes);

            // Encontrou soluções neste nível: não procura níveis maiores.
            if (!solucoes.isEmpty()) {
                break;
            }

            quantidade++;
        }

        // 6. Monta os objetos corridaComposta encontrados.
        List<String> resultados = new ArrayList<>();

        for (TrechoDisponivel direta : diretas) {
            resultados.add(formatarDireta(direta));
        }

        for (CaminhoEncontrado caminho : solucoes) {

            corridaComposta composta = criarComposta(caminho);

            compostas.put(composta.getID(), composta);

            resultados.add(formatarComposta(composta, caminho.trechos()));
        }

        if (resultados.isEmpty()) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        return "OK|" + okays.CORRIDA_ENCONTRADA + "|" + String.join("~", resultados);
    }

    private void buscarCaminhos(String atual, String destino, int quantidadeMaxima, LocalDateTime ultimaPartida,
        List<TrechoDisponivel> caminho, List<String> paradas, Set<String> idsUtilizados,
        Map<String, List<TrechoDisponivel>> porOrigem, List<CaminhoEncontrado> solucoes) {

        // Chegamos ao destino.
        if (atual.equals(destino)) {

            if (caminho.size() == quantidadeMaxima) {

                solucoes.add(new CaminhoEncontrado(
                        new ArrayList<>(caminho),
                        new ArrayList<>(paradas)
                ));
            }

            return;
        }

        // Já utilizamos a quantidade máxima de corridas.
        if (caminho.size() >= quantidadeMaxima) {
            return;
        }

        List<TrechoDisponivel> proximos = porOrigem.get(atual);

        if (proximos == null) {
            return;
        }

        for (TrechoDisponivel proximo : proximos) {

            Detalhes detalhes = proximo.detalhes();

            String id = detalhes.id();
            String proximaCidade = proximo.destino();

            // Não reutiliza a mesma corrida.
            if (idsUtilizados.contains(id)) {
                continue;
            }

            // Evita retornar a uma cidade já visitada.
            if (paradas.contains(proximaCidade)) {
                continue;
            }

            LocalDateTime horario;

            try {
                horario = converterHorario(detalhes.horario());
            } catch (RuntimeException e) {
                continue;
            }

            // A próxima partida deve ocorrer pelo menos uma hora
            // depois da partida da corrida anterior.
            if (ultimaPartida != null
                    && horario.isBefore(ultimaPartida.plusHours(1))) {
                continue;
            }

            // Adiciona a corrida e a cidade ao caminho atual.
            caminho.add(proximo);
            paradas.add(proximaCidade);
            idsUtilizados.add(id);

            buscarCaminhos(proximaCidade, destino, quantidadeMaxima,
                    horario, caminho, paradas, idsUtilizados,
                    porOrigem, solucoes);

            // Desfaz as alterações para testar outras possibilidades.
            caminho.remove(caminho.size() - 1);
            paradas.remove(paradas.size() - 1);
            idsUtilizados.remove(id);
        }
    }


    private corridaComposta criarComposta(CaminhoEncontrado caminho) {
        List<TrechoDisponivel> trechos = caminho.trechos();

        Map<String, int[]> corridas = new LinkedHashMap<>();
        List<String> motoristas = new ArrayList<>();

        int precoTotal = 0;

        // Cada corrida cobre um trecho do itinerário.
        for (int i = 0; i < trechos.size(); i++) {

            Detalhes detalhes = trechos.get(i).detalhes();

            int[] vetor = new int[trechos.size()];
            vetor[i] = 1;

            corridas.put(detalhes.id(), vetor);

            motoristas.add(detalhes.motorista());

            precoTotal += detalhes.preco();
        }

        String id = "COM" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 8);

        corridaComposta composta = new corridaComposta(id, caminho.paradas(),
            corridas, motoristas, precoTotal);

        etapasCompostas.put(composta.getID(), new ArrayList<>(caminho.trechos()));

        compostas.put(composta.getID(), composta);

        return composta;
    }

    private String formatarDireta(TrechoDisponivel trecho) {
        Detalhes d = trecho.detalhes();

        return "DIRETA;" +
            d.id() + ";" +
            trecho.origem() + ";" +
            trecho.destino() + ";" +
            d.preco() + ";" +
            d.horario() + ";" +
            d.motorista() + ";" +
            d.vagas();
    }

    private String formatarComposta(
            corridaComposta composta,
            List<TrechoDisponivel> etapas) {

        List<String> dadosEtapas = new ArrayList<>();

        for (TrechoDisponivel etapa : etapas) {
            Detalhes d = etapa.detalhes();

            dadosEtapas.add(
                d.id() + ";" +
                etapa.origem() + ";" +
                etapa.destino() + ";" +
                d.preco() + ";" +
                d.horario() + ";" +
                d.motorista() + ";" +
                d.vagas()
            );
        }

        return "COMPOSTA;" +
            composta.getID() + ";" +
            composta.getOrigem() + ";" +
            composta.getDestino() + ";" +
            composta.getPreco() + ";" +
            String.join("#", dadosEtapas);
    }

    private Map<ConsultaTrecho, List<String>> gerarTrechosCandidatos() {
        Map<ConsultaTrecho, List<String>> candidatos = new HashMap<>();

        synchronized (travaIndice) {

            for (Oferta oferta : indice.values()) {

                List<String> cidades = oferta.cidades();

                // Gera todos os pares possíveis de cidades da rota.
                for (int i = 0; i < cidades.size() - 1; i++) {

                    for (int j = i + 1; j < cidades.size(); j++) {

                        String origem = cidades.get(i);
                        String destino = cidades.get(j);

                        ConsultaTrecho consulta = new ConsultaTrecho(
                            oferta.empresa(),
                            origem,
                            destino
                        );

                        candidatos.computeIfAbsent(
                            consulta,
                            k -> new ArrayList<>()
                        ).add(oferta.chave());
                    }
                }
            }
        }

        return candidatos;
    }
        
    private List<TrechoDisponivel> consultarTodosTrechos(Map<ConsultaTrecho, List<String>> candidatos) {

        List<CompletableFuture<List<TrechoDisponivel>>> futuros =
            new ArrayList<>();

        // Cria uma consulta paralela para cada grupo de empresa, origem e destino.
        for (var entrada : candidatos.entrySet()) {

            ConsultaTrecho consulta = entrada.getKey();
            List<String> ids = List.copyOf(entrada.getValue());

            futuros.add(
                CompletableFuture.supplyAsync(() -> {

                    List<Detalhes> detalhes = consultarGrupo(consulta, ids);

                    List<TrechoDisponivel> confirmados = new ArrayList<>();

                    for (Detalhes d : detalhes) {
                        confirmados.add(new TrechoDisponivel(
                            d,
                            consulta.origem(),
                            consulta.destino()
                        ));
                    }

                    return confirmados;

                }, consultas)
            );
        }

        // Reúne as respostas.
        List<TrechoDisponivel> resultados = new ArrayList<>();

        for (CompletableFuture<List<TrechoDisponivel>> futuro : futuros) {

            try {
                resultados.addAll(futuro.get(5, TimeUnit.SECONDS));

            } catch (TimeoutException e) {
                futuro.cancel(true);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return resultados;

            } catch (ExecutionException e) {
                System.err.println(
                    "Erro consultando trechos: " + e.getCause()
                );
            }
        }
        
        System.out.println("=== TRECHOS CANDIDATOS ===");

        for (var entrada : candidatos.entrySet()) {
            System.out.println(
                entrada.getKey() + " -> " + entrada.getValue()
            );
        }

        return resultados;
    }


    // Recebe uma empresa, as ofertas candidatas dessa empresa e a origem e o destino desejados.
    private List<Detalhes> consultarGrupo(ConsultaTrecho consulta, List<String> ids) {
        // Lista local de resultados
        List<Detalhes> detalhes = new ArrayList<>();

        String proprietario = consulta.proprietario();
        String origem = consulta.origem();
        String destino = consulta.destino();

        try {

            // Consulta diretamente o armazenamento local.
            if (proprietario.equals(empresa)) {

                for (String id : ids) {
                    String idLocal = identificadorGlobal.obterIdLocal(id);

                    corrida c = servicosServidor.obterCorridaDisponivel(idLocal, origem, destino);

                    if (c != null) {
                         detalhes.add(new Detalhes(
                                empresa,
                                id,
                                true,
                                c.getPreco(),
                                c.getHorario().toString(),
                                c.getmotorista(),
                                vagasDisponiveis(c, origem, destino)));
                    }
                }

            } else {
                // Consulta a empresa proprietária pela REST.
                URI base = enderecos.get(proprietario);

                if (base == null) {
                    return detalhes;
                }

                for (int i = 0; i < ids.size(); i += 40) {

                    int fim = Math.min(i + 40, ids.size());

                    String lote = String.join(",", ids.subList(i, fim));

                    String parametros = "ids=" + URLEncoder.encode(lote, StandardCharsets.UTF_8) +
                                    "&origem=" + URLEncoder.encode(origem, StandardCharsets.UTF_8) +
                                    "&destino=" + URLEncoder.encode(destino, StandardCharsets.UTF_8);

                    URI uri = base.resolve("/api/corridas?" + parametros);
                    
                    System.out.println("Consultando servidor: " + proprietario);
                    System.out.println("URI: " + uri);
                    System.out.println("IDs: " + ids);

                    HttpResponse<String> resp = get(uri);

                    if (resp.statusCode() != 200) {
                        return detalhes;
                    }

                    System.out.println("Status HTTP: " + resp.statusCode());
                    System.out.println("Resposta: " + resp.body());

                    List<Detalhes> recebidos = AdaptadorJson.ler(resp.body(), TIPO_DETALHES);

                    if (recebidos != null) {
                        detalhes.addAll(recebidos);
                    }
                }
            }

            // Filtra os resultados confirmados pelo proprietário.
            Set<String> idsPermitidos = new HashSet<>(ids);

             detalhes.removeIf(d -> d == null || !idsPermitidos.contains(d.id()) ||
                    !d.disponivel() || d.preco() < 0 || !proprietario.equals(d.empresa()));

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } catch (IOException | RuntimeException e) {

            System.err.println("Falha consultando " + proprietario + ": " + e.getMessage());
        }

        return detalhes;
    }

    // Concentra a criação e o envio das requisições GET.
    private HttpResponse<String> get(URI uri) throws IOException, InterruptedException {

        return http.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(3)) // define um limite para a requisição
                        .GET() // define o método HTTP
                        .build(),
                            HttpResponse.BodyHandlers.ofString());
    }

    // Valida o verbo HTTP
    private static boolean verificarMetodo(HttpExchange ex, String esperado) throws IOException {
        // Se o método recebido for o esperado, a função retorna true, permitindo continuar o processamento.
        if (esperado.equals(ex.getRequestMethod()))
            return true;

        // Retorna HTTP 405, que indica método não permitido para aquele endpoint
        responder(ex, 405, new Erro(erros.METODO_INVALIDO));

        return false;
    }

    // Extraí parâmetros da URL
    private static String parametro(String query, String nome) {
        // Se a URL não tiver parâmetros, retornamos uma string vazia.
        if (query == null) return "";

        // Cada parte é separada em chave e valor, limitando a divisão a duas partes
        for (String parte : query.split("&")) {
            String[] kv = parte.split("=", 2);
            
            // Quando a chave procurada é encontrada, decodificamos o valor e o devolvemos
            if (kv[0].equals(nome)) {
                return URLDecoder.decode(
                        kv.length > 1 ? kv[1] : "",
                        StandardCharsets.UTF_8);
            }
        }

        // Retornamos uma string vazia se a chave não existir
        return "";
    }   

    // Retorna true quando a string é nula, vazia ou composta somente por espaços em branco.
    // Evita repetir verificações de nulidade e espaços em todos os métodos.
    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    // Recebe a requisição HTTP, o código de resposta e o objeto que será enviado e envia Json pela API REST
    private static void responder(HttpExchange ex, int status, Object dados) throws IOException {

        byte[] bytes = AdaptadorJson.gerar(dados) // Transforma o objeto em JSON
                .getBytes(StandardCharsets.UTF_8); // Converte o texto em bytes para a transmissão pela rede.

        // Informa ao destinatário que o corpo da resposta é JSON codificado em UTF-8.
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

        // Envia o código HTTP e o tamanho do corpo.
        ex.sendResponseHeaders(status, bytes.length);
        
        // Escreve os bytes da resposta e fecha o fluxo automaticamente ao sair do try.
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
