
package vaijunto.src.Server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.reflect.TypeToken;

import vaijunto.src.model.corrida;
import vaijunto.src.model.erros;
import vaijunto.src.model.okays;

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

public final class Consorcio {
    private final String empresa;
    private final List<URI> parceiros;

    // Associa o identificador de uma empresa ao seu endereço.
    private final Map<String, URI> enderecos = new ConcurrentHashMap<>();

    // Protege a substituicao completa do indice.
    private final Object travaIndice = new Object();

    // Chave utiliza a empresa e o ID da corrida (Ex: EMP_A:CAR001)
    private final Map<String, Oferta> indice = new HashMap<>();

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
            List<Oferta> ofertas
    ) {}

    // Representa os dados atuais de uma corrida obtidos diretamente no servidor proprietário.
    private record Detalhes(
            String empresa,
            String id,
            int vagas,
            int preco
    ) {}

    // Diferente de erros, representa a estrutura JSON da resposta.
    private record Erro(String erro) {}

    // Preserva informações de tipo da lista de objetos Detalhes 
    private static final Type TIPO_DETALHES = new TypeToken<List<Detalhes>>() {}.getType();


    // Construtor recebe a identidade da empresa, a porta REST e a lista de parceiros
    public Consorcio(String empresa, int portaRest, List<URI> parceiros) throws IOException {
        this.empresa = Objects.requireNonNull(empresa); // Impede a inicialização com uma empresa nula.
        this.parceiros = List.copyOf(parceiros); // Cria uma cópia imutável da lista de parceiros.

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
            List<String> rota = new ArrayList<>();
            rota.add(c.getOrigem());
            rota.addAll(c.getParadas());
            rota.add(c.getDestino());

            // Cria a oferta com uma cópia imutável da rota e armazenamos a oferta no mapa temporário.
            // Usamos 'List.copyOf(rota)' para impedir que uma alteração posterior na lista original modifique silenciosamente os dados da oferta.
            Oferta oferta = new Oferta(empresa, c.getID(), List.copyOf(rota));
            atuais.put(oferta.chave(), oferta);
        }

        /* Funciona como um cadeado: enquanto uma thread está executando uma seção protegida, 
        outra thread que precise do mesmo cadeado deve esperar. Não bloqueia diretamente os outros servidores da rede. */
        synchronized (travaIndice) {
            if (atuais.equals(ultimoIndiceLocal))
                return;

            // Remove do índice as ofertas pertencentes à própria empresa.
            indice.entrySet().removeIf(e -> e.getValue().empresa().equals(empresa));

            indice.putAll(atuais);
            ultimoIndiceLocal = Map.copyOf(atuais);
            revisaoLocal++;
        }
    }

    // Atualiza todos os parceiros em sequencia.
    private void sincronizar() {
        try {
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
            // envia uma requisição HTTP GET e devolve a resposta
            HttpResponse<String> statusResp = get(parceiro.resolve("/api/status"));

            // Abandona a tentativa se o parceiro não responder com HTTP 200 (OK)
            if (statusResp.statusCode() != 200)
                return;

            // Converte o corpo JSON em um objeto 'Status'.
            Status status = AdaptadorJson.ler(statusResp.body(), Status.class);

            // Verifica status ausente, sem identidade ou se é o próprio servidor.
            if (status == null || vazio(status.empresa()) || status.empresa().equals(empresa))
                return;


            String proprietario = status.empresa();

            // Busca o snapshot das ofertas do parceiro.
            HttpResponse<String> resp = get(parceiro.resolve("/api/index"));
            if (resp.statusCode() != 200) 
                return;

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
                indice.entrySet().removeIf(e ->
                        e.getValue().empresa()
                                .equals(proprietario));

                indice.putAll(novas);
            }
            
            // Guarda o endereço do proprietário
            enderecos.put(proprietario, parceiro);
        
        // A thread foi interrompida; preservamos esse estado.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        // Houve falha de comunicação.
        } catch (IOException e) {
            System.err.println("Falha de rede: " + parceiro);
        
        // Ocorreu um erro inesperado no processamento
        } catch (RuntimeException e) {
            System.err.println("Resposta invalida de " + parceiro + ": " + e.getMessage());
        }
    }

    // Recebe o snapshot e a identidade que esperamos encontrar nele
    private boolean validarSnapshot(Snapshot s, String proprietario) {
        // Rejeita a Snapshot se: Ele for nulo; A empresa declarada não corresponder à identidade esperada; 
        // O UUID da instância estiver vazio; A revisão for negativa; A lista de ofertas estiver ausente.
        if (s == null || !proprietario.equals(s.empresa()) || vazio(s.instancia())
            || s.revisao() < 0 || s.ofertas() == null)
            return false;

        // Cria um conjunto para detectar IDs repetidos dentro do snapshot.
        Set<String> ids = new HashSet<>();


        for (Oferta o : s.ofertas()) {
            // Cada oferta precisa ter empresa e ID válidos, uma rota com pelo menos duas cidades e um ID que ainda não tenha aparecido.
            if (o == null || !proprietario.equals(o.empresa()) || vazio(o.id())
                || o.cidades() == null || o.cidades().size() < 2 || !ids.add(o.id()))
                return false;

            // Verifica se as cidades estão preenchidas
            for (String cidade : o.cidades()) {
                if (vazio(cidade)) return false;
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

    // Consulta a quantidade atual de vagas deve ser consultada no proprietário.
    private void enviarCorridas(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "GET")) return;

        // Extraí o parâmetro 'ids' da URL:
        String parametro = parametro(ex.getRequestURI().getRawQuery(), "ids");

        // Retorna HTTP 400 (Bad Request/Solicitação inválida)
        if (vazio(parametro)) {
            responder(ex, 400, new Erro(erros.IDS_OBRIGATORIOS));
            return;
        }

        String[] ids = parametro.split(",");

        // Limita a quantidade de ids por consulta
        if (ids.length > 40) {
            responder(ex, 400, new Erro(erros.LIMITE_DE_IDS));
            return;
        }

        List<Detalhes> detalhes = new ArrayList<>();

        // 'Arrays.asList(ids)' transforma o vetor em uma lista, e 'LinkedHashSet' elimina IDs duplicados preservando a ordem.
        // Ignoramos IDs vazios, corridas inexistentes e corridas finalizadas.
        for (String id : new LinkedHashSet<>(Arrays.asList(ids))) {
            if (vazio(id)) continue;

            corrida c = servicosServidor.obterCorridaLocal(id);

            if (c == null || c.getFinalizada())
                continue;

            // Devolve uma lista com os detalhes atuais das corridas encontradas.
            detalhes.add(new Detalhes(
                    empresa,
                    c.getID(),
                    c.getBancos(),
                    c.getPreco()));
        }

        responder(ex, 200, detalhes);
    }

    // POST /api/atualizar
    private void receberAviso(HttpExchange ex) throws IOException {
        if (!verificarMetodo(ex, "POST")) return;

        responder(ex, 202, Map.of("recebido", true));

        // O executor serializa este trabalho com o polling.
        sincronizador.execute(this::sincronizar);
    }

    public String buscar(String dados) {
        String[] partes = dados.split(";", -1);

        if (partes.length < 2 || vazio(partes[0]) || vazio(partes[1]))
            return "ERRO|" + erros.BUSCA_INVALIDA;

        String origem = partes[0].trim();
        String destino = partes[1].trim();

        Map<String, List<Oferta>> grupos = new TreeMap<>();

        // Primeira fase: somente o indice local.
        synchronized (travaIndice) {
            for (Oferta o : indice.values()) {
                int inicio = o.cidades().indexOf(origem);
                int fim = o.cidades().indexOf(destino);

                if (inicio >= 0 && fim > inicio) {
                    grupos.computeIfAbsent(
                            o.empresa(),
                            k -> new ArrayList<>()).add(o);
                }
            }
        }

        // Segunda fase: detalhes dos proprietarios.
        List<CompletableFuture<List<String>>> futuros = new ArrayList<>();

        for (var grupo : grupos.entrySet()) {
            String proprietario = grupo.getKey();
            List<Oferta> ofertas = List.copyOf(grupo.getValue());

            futuros.add(CompletableFuture.supplyAsync(
                    () -> consultarGrupo(
                            proprietario, ofertas,
                            origem, destino),
                    consultas));
        }

        List<String> resultados = new ArrayList<>();

        for (CompletableFuture<List<String>> futuro : futuros) {
            try {
                resultados.addAll(futuro.get(4, TimeUnit.SECONDS));

            } catch (TimeoutException e) {
                System.err.println("Timeout aguardando grupo");
                futuro.cancel(true);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERRO|" + erros.BUSCA_INTERROMPIDA;

            } catch (ExecutionException e) {
                System.err.println("Erro consultando grupo: " + e.getCause());
            }
        }

        Collections.sort(resultados);

        if (resultados.isEmpty())
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;

        return "OK|" + okays.CORRIDA_ENCONTRADA + "|" + String.join(";", resultados);
    }

    private List<String> consultarGrupo(String proprietario, List<Oferta> ofertas, String origem, String destino) {

        List<String> resultados = new ArrayList<>();

        try {
            List<Detalhes> detalhes = new ArrayList<>();

            if (proprietario.equals(empresa)) {
                for (Oferta o : ofertas) {
                    corrida c = servicosServidor.obterCorridaLocal(o.id());

                    if (c != null && !c.getFinalizada()) {
                        detalhes.add(new Detalhes(
                                empresa, c.getID(),
                                c.getBancos(), c.getPreco()));
                    }
                }

            } else {
                URI base = enderecos.get(proprietario);
                if (base == null) return resultados;

                // Divide as consultas em lotes de ate 40 IDs.
                for (int i = 0; i < ofertas.size(); i += 40) {
                    int fim = Math.min(i + 40, ofertas.size());

                    String ids = String.join(",",
                            ofertas.subList(i, fim).stream()
                                    .map(Oferta::id).toList());

                    URI uri = base.resolve("/api/corridas?ids=" + URLEncoder.encode(ids, StandardCharsets.UTF_8));

                    HttpResponse<String> resp = get(uri);

                    if (resp.statusCode() != 200)
                        return new ArrayList<>();

                    List<Detalhes> lote = AdaptadorJson.ler(resp.body(), TIPO_DETALHES);

                    if (lote == null)
                        return new ArrayList<>();

                    detalhes.addAll(lote);
                }
            }

            Set<String> permitidos = new HashSet<>();

            for (Oferta o : ofertas)
                permitidos.add(o.id());

            for (Detalhes d : detalhes) {
                if (d == null || !permitidos.contains(d.id()) || d.vagas() <= 0 || d.preco() < 0)
                    continue;

                if (d.empresa() != null && !proprietario.equals(d.empresa()))
                    continue;

                resultados.add(proprietario + ":" + d.id()
                                + " - " + origem + " a " + destino
                                + " - vagas: " + d.vagas()
                                + " - preco: " + d.preco());
            }

        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("ERRO|" + erros.TEMPO_ESGOTADO + "|Empresa: " + proprietario);

        } catch (IOException e) {
            System.err.println( "ERRO|" + erros.SERVIDOR_INDISPONIVEL + "|Empresa: " + proprietario);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            System.err.println("ERRO|" + erros.BUSCA_INTERROMPIDA);

        } catch (RuntimeException e) {
            System.err.println("ERRO|" + erros.RESPOSTA_INVALIDA + 
                                "|Empresa: " + proprietario + "|Detalhes: " + e.getMessage());
        }
        return resultados;
    }

    private HttpResponse<String> get(URI uri)
            throws IOException, InterruptedException {

        return http.send(
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static boolean verificarMetodo(HttpExchange ex, String esperado) throws IOException {
        if (esperado.equals(ex.getRequestMethod()))
            return true;

        responder(ex, 405, new Erro(erros.METODO_INVALIDO));

        return false;
    }

    private static String parametro(String query, String nome) {
        if (query == null) return "";

        for (String parte : query.split("&")) {
            String[] kv = parte.split("=", 2);

            if (kv[0].equals(nome)) {
                return URLDecoder.decode(
                        kv.length > 1 ? kv[1] : "",
                        StandardCharsets.UTF_8);
            }
        }

        return "";
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static void responder(HttpExchange ex, int status, Object dados) throws IOException {

        byte[] bytes = AdaptadorJson.gerar(dados)
                .getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
