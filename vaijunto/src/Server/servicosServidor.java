package vaijunto.src.Server;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import vaijunto.src.model.*;

public class servicosServidor {
    private static String empresa;

    private static Map<String, cliente> registros = new HashMap<String, cliente>();
    private static Map<String, corrida> corridas = new HashMap<String, corrida>();
    private static Map<String, Consorcio.ReservaComposta> reservasCompostas = new HashMap<>();
    private static int IDcounter = 1;

    // registra o nome da empresa responsavel pela instância para identificação
    public static synchronized String configurarEmpresa(String nomeEmpresa) {
        if (nomeEmpresa == null || nomeEmpresa.isBlank()
                || nomeEmpresa.contains(":")) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        empresa = nomeEmpresa;

        try {
            PersistenciaJson.Snapshot snapshot = PersistenciaJson.carregar(empresa);

            if (snapshot != null) {
                // Recupera os dados salvos anteriormente.
                registros = snapshot.registros;
                corridas = snapshot.corridas;
                IDcounter = snapshot.IDcounter;

                System.out.println("Dados recuperados da empresa: " + empresa);
                System.out.println("Clientes carregados: " + registros.size());
                System.out.println("Corridas carregadas: " + corridas.size());
            } else {
                // Primeira inicialização dessa empresa.
                registros = new HashMap<>();
                corridas = new HashMap<>();
                IDcounter = 1;

                System.out.println("Nenhum arquivo anterior encontrado para " + empresa);
            }

            return "OK|" + okays.EMPRESA_CONFIGURADA;

        } catch (java.io.IOException e) {
            System.err.println("Falha ao carregar dados: " + e.getMessage());
            return "ERRO|" + erros.FALHA_AO_CARREGAR;
        }
    }

    public static boolean salvarDados() {
        try {
            PersistenciaJson.salvar(
                    empresa,
                    registros,
                    corridas,
                    reservasCompostas,
                    IDcounter);

            return true;

        } catch (java.io.IOException e) {
            System.err.println(
                    "Falha ao salvar dados da empresa " + empresa
                    + ": " + e.getMessage());

            return false;
        }
    }

    public synchronized static void configurarReservasCompostas(
        Map<String, Consorcio.ReservaComposta> reservas) {
        reservasCompostas = reservas;
    }
    
    public synchronized static java.util.List<corrida> listarCorridasLocais() {
        return new java.util.ArrayList<>(corridas.values());
    }

    public synchronized static corrida obterCorridaLocal(String id) {
        return corridas.get(id);
    }
    
    // Faz o login do cliente, verificando se o usuário não está logado, se a senha está correta e se o usuário existe no sistema
    // login[0] = usuario
    // login[1] = senha
    public synchronized static String login(String dados) {
        String login[] = dados.split("\\;");
        if (!registros.containsKey(login[0])) return "ERRO|" + erros.USUARIO_SEM_REGISTRO;
        if (registros.get(login[0]).getLogado()) return "ERRO|" + erros.USUARIO_LOGADO;
        else if (!registros.get(login[0]).getSenha().equals(login[1])) return "ERRO|" + erros.SENHA_ERRADA;
        else if (registros.get(login[0]).getSenha().equals(login[1])) {
            registros.get(login[0]).logar();
            System.out.println("Cliente logado: " + registros.get(login[0]).getUsuario());
            return "OK|" + okays.USUARIO_LOGADO + "|" + login[0];
        }
        return "ERRO|" + erros.USUARIO_SEM_REGISTRO;
    }

    // Adiciona um usuário novo nos registros, verificando se no meio tempo outro usuário não criou uma conta com mesmo nome
    // registro[0] = usuario
    // registro[1] = senha
    // registro[2] = veiculo
    public synchronized static String registro(String dados) {
        String registro[] = dados.split("\\;");

        if (registro.length < 2 || registro.length > 3
                || registro[0].isBlank() || registro[1].isBlank()) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        if (empresa == null) {
            return "ERRO|" + erros.MENSAGEM_INVALIDA;
        }

        // Verifica se o usuário já existe.
        String checagem = existeUsuario(registro[0]);
        if (checagem.startsWith("ERRO")) return checagem;

        cliente novo;

        if (registro.length == 3) {
            String motorista[] = registro[2].split("\\&");

            if (motorista.length != 4) {
                return "ERRO|" + erros.ENTRADA_INVALIDA;
            }

            motorista veiculo = new motorista(
                    motorista[0], motorista[1],
                    motorista[2], motorista[3]
            );

            novo = new cliente(registro[0], registro[1], empresa, veiculo);
        } else {
            novo = new cliente(registro[0], registro[1], empresa);
        }

        registros.put(novo.getUsuario(), novo);

        // Se não conseguir salvar, desfaz o cadastro.
        if (!salvarDados()) {
            registros.remove(novo.getUsuario());
            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        System.out.println("Cliente registrado: " + novo);

        return "OK|" + okays.USUARIO_REGISTRADO + "|" + registro[0];
    }


    // Desloga o usuário quando ele fecha o programa
    public static String deslogar(String usuario) {
        registros.get(usuario).deslogar();
        return "OK|" + okays.USUARIO_DESLOGADO;
    }

    // Checa se o nome escolhido para registro não está em uso por outro usuário, para evitar duplicidade de nomes
    public synchronized static String existeUsuario(String usuario) { 
        if (!registros.containsKey(usuario)) return "OK|" + okays.NOME_DISPONIVEL;
        else return "ERRO|" + erros.USUARIO_IGUAL;
    }

    public static String usuarioMotorista(String usuario) {
        if (registros.get(usuario).getVeiculo() != null) return "OK|" + okays.USUARIO_MOTORISTA;
        return "ERRO|" + erros.USUARIO_PASSAGEIRO;
    }

    // Verifica a disponibilidade de uma corrida
    public synchronized static boolean corridaDisponivel(String id, String origem, String destino) {
        corrida c = corridas.get(id);

        if (c == null || c.getFinalizada()) {
            return false;
        }

        return c.temVagas(origem, destino);
    }   

    // Devolve uma corrida a ser consultada 
    public synchronized static corrida obterCorridaDisponivel(String id, String origem, String destino) {
        corrida c = corridas.get(id);

        if (c == null || c.getFinalizada()) {
            return null;
        }

        try {
            if (!c.temVagas(origem, destino)) {
                return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }

        return c;
    }

    // Registra uma nova corrida, seja ela com ou sem paradas, e adiciona a corrida no histórico do usuário
    // corrida[0] = origem
    // corrida[1] = destino
    // corrida[2] = horario
    // corrida[3] = assentos
    // corrida[4] = preco;
    // corrida[5] = paradas
    public synchronized static String publicarCorrida(String usuario, String dados) {
        String corrida[] = dados.split("\\;");

        if (corrida.length != 6) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        if (!registros.containsKey(usuario)) {
            return "ERRO|" + erros.USUARIO_SEM_REGISTRO;
        }

        String data[] = corrida[2].split("\\&");

        if (data.length != 4) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        data horario = new data(data[0], data[1], data[2], data[3]);

        int bancos;
        int preco;

        try {
            bancos = Integer.parseInt(corrida[3]);
            preco = Integer.parseInt(corrida[4]);
        } catch (NumberFormatException e) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        // Converte a String recebida em uma lista com o percurso completo.
        List<String> paradas = Arrays.asList(corrida[5].split("\\&"));

        // Confere se o percurso corresponde à origem e ao destino informados.
        if (paradas.size() < 2
                || !paradas.get(0).equals(corrida[0])
                || !paradas.get(paradas.size() - 1).equals(corrida[1])) {
            return "ERRO|" + erros.PERCURSO_INVALIDO;
        }

        // O ID só é gerado depois que os dados foram validados.
        String ID = String.format("CAR%03d", IDcounter);

        corrida nova = new corrida(
                ID, usuario, corrida[0], corrida[1],
                horario, bancos, preco, paradas
        );

        corridas.put(ID, nova);
        registros.get(usuario).addCorrida(ID);
        IDcounter++;

        // Se não conseguir salvar, desfaz a publicação.
        if (!salvarDados()) {
            corridas.remove(ID);
            registros.get(usuario).removerCorrida(ID);
            IDcounter--;

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        System.out.println("Corrida registrada: " + nova.getID());

        return "OK|" + okays.CORRIDA_CRIADA;
    }


    // Reserva local: recebe o nome de usuário e gera seu ID global.
    public synchronized static String reservarCorridaLocal(
            String usuario, String idLocal, String idGlobal, String origem, String destino) {

        String idPassageiro = identificadorGlobal.gerar(empresa, usuario);

        return reservarCorridaGlobal(idPassageiro, usuario, idLocal, idGlobal, origem, destino);
    }


    // Reserva distribuída: recebe diretamente o ID global do passageiro.
    public synchronized static String reservarCorridaGlobal(
            String idPassageiro, String usuarioLocal,
            String idLocal, String idGlobal, String origem, String destino) {

        corrida c = corridas.get(idLocal);

        if (c == null) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        if (c.getFinalizada()) {
            return "ERRO|" + erros.CORRIDA_FINALIZADA;
        }

        String idMotorista = identificadorGlobal.gerar(
                empresa, c.getmotorista()
        );

        if (idMotorista.equals(idPassageiro)) {
            return "ERRO|" + erros.CORRIDA_MOTORISTA;
        }

        if (c.getCarona().containsKey(idPassageiro)) {
            return "ERRO|" + erros.CORRIDA_JA_RESERVADA;
        }

        if (!c.addCarona(idPassageiro, origem, destino)) {
            return "ERRO|" + erros.CORRIDA_CHEIA;
        }

        boolean atualizouHistorico = usuarioLocal != null && registros.containsKey(usuarioLocal);

        if (atualizouHistorico) {
            registros.get(usuarioLocal).addCorrida(idGlobal);
        }

        // Persiste a reserva e o histórico atualizado.
        if (!salvarDados()) {
            // Desfaz a reserva em memória.
            c.removeCarona(idPassageiro);

            if (atualizouHistorico) {
                registros.get(usuarioLocal).removerCorrida(idGlobal);
            }

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.CORRIDA_RESERVADA;
    }

    // Prepara uma reserva global e persiste o bloqueio das vagas.
    public synchronized static String prepararReservaGlobal(
            String idTransacao,
            String idPassageiro,
            String idLocal,
            String origem,
            String destino) {

        if (idTransacao == null || idPassageiro == null
                || idLocal == null || origem == null || destino == null) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        corrida c = corridas.get(idLocal);

        if (c == null) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        if (c.getFinalizada()) {
            return "ERRO|" + erros.CORRIDA_FINALIZADA;
        }

        String idMotorista = identificadorGlobal.gerar(
                empresa, c.getmotorista()
        );

        if (idMotorista.equals(idPassageiro)) {
            return "ERRO|" + erros.CORRIDA_MOTORISTA;
        }

        String estado = c.obterEstadoTransacao(idTransacao);

        if (estado != null) {
            if (!c.correspondeTransacao(
                    idTransacao, idPassageiro, origem, destino)) {
                return "ERRO|" + erros.FALHA_NA_RESERVA;
            }

            if (estado.equals("PREPARADA")) {
                return "OK|" + okays.RESERVA_PREPARADA;
            }

            if (estado.equals("CONFIRMADA")) {
                return "OK|" + okays.RESERVA_CONFIRMADA;
            }

            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }

        if (!c.prepararCarona(
                idTransacao, idPassageiro, origem, destino)) {
            return "ERRO|" + erros.CORRIDA_CHEIA;
        }

        // O bloqueio só é considerado preparado após ser salvo.
        if (!salvarDados()) {
            c.abortarCarona(idTransacao);

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.RESERVA_PREPARADA;
    }

    // Confirma uma reserva previamente preparada.
    public synchronized static String confirmarReservaGlobal(String idTransacao,  String idLocal) {
        if (idTransacao == null || idLocal == null) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        corrida c = corridas.get(idLocal);

        if (c == null) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        String estado = c.obterEstadoTransacao(idTransacao);

        if ("CONFIRMADA".equals(estado)) {
            return "OK|" + okays.RESERVA_CONFIRMADA;
        }

        if (!"PREPARADA".equals(estado)) {
            return "ERRO|" + erros.RESERVA_NAO_PREPARADA;
        }

        String idPassageiro = c.obterPassageiroPendente(idTransacao);

        if (idPassageiro == null) {
            return "ERRO|" + erros.RESERVA_NAO_PREPARADA;
        }

        if (!c.confirmarCarona(idTransacao)) {
            return "ERRO|" + erros.FALHA_AO_CONFIRMAR;
        }

        if (!salvarDados()) {
            // Recupera a pendência se a persistência falhar.
            boolean restaurou = c.reverterConfirmacao(
                    idTransacao, idPassageiro
            );

            if (!restaurou) {
                System.err.println(
                        "ERRO CRÍTICO: falha ao reverter COMMIT "
                        + idTransacao
                );
            }

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.RESERVA_CONFIRMADA;
    }

    // Aborta uma reserva preparada e devolve as vagas.
    public synchronized static String abortarReservaGlobal(
            String idTransacao,
            String idLocal) {

        if (idTransacao == null || idLocal == null) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        corrida c = corridas.get(idLocal);

        if (c == null) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        String estado = c.obterEstadoTransacao(idTransacao);

        if ("ABORTADA".equals(estado)) {
            return "OK|" + okays.RESERVA_ABORTADA;
        }

        if (!"PREPARADA".equals(estado)) {
            return "ERRO|" + erros.RESERVA_NAO_PREPARADA;
        }

        String idPassageiro = c.obterPassageiroPendente(idTransacao);
        int[] reserva = c.obterVetorPendente(idTransacao);

        if (idPassageiro == null || reserva == null) {
            return "ERRO|" + erros.RESERVA_NAO_PREPARADA;
        }

        if (!c.abortarCarona(idTransacao)) {
            return "ERRO|" + erros.FALHA_AO_ABORTAR;
        }

        if (!salvarDados()) {
            // Restaura o bloqueio caso o cancelamento não seja persistido.
            boolean restaurou = c.restaurarPendencia(
                    idTransacao, idPassageiro, reserva
            );

            if (!restaurou) {
                System.err.println(
                        "ERRO CRÍTICO: falha ao restaurar ABORT "
                        + idTransacao
                );
            }

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.RESERVA_ABORTADA;
    }

    // Gera um ID global para o passageiro local e reaproveita o método de cancelamento global
    public synchronized static String cancelarCorridaLocal(String usuario, String idLocal, String IdGlobal) {
        String idPassageiro = identificadorGlobal.gerar(empresa, usuario);

        return cancelarCorridaGlobal(idPassageiro, usuario, idLocal, IdGlobal);
    }

    // Cancela a reserva de um passageiro em uma corrida local, independende de onde seja o usuario
    public synchronized static String cancelarCorridaGlobal(
        String idPassageiro,
        String usuarioLocal,
        String idLocal,
        String idGlobal) {

        corrida c = corridas.get(idLocal);

        if (c == null) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        if (c.getFinalizada()) {
            return "ERRO|" + erros.CORRIDA_FINALIZADA;
        }

        if (!identificadorGlobal.gerar(empresa, idLocal).equals(idGlobal)) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        int[] reservaOriginal = c.getCarona().get(idPassageiro);

        if (reservaOriginal == null) {
            return "ERRO|" + erros.CORRIDA_NAO_RESERVADA;
        }

        // Preserva uma cópia para eventual rollback.
        int[] reserva = reservaOriginal.clone();

        // Verifica se a reserva pertence a uma transação distribuída.
        String idTransacao =
                c.obterTransacaoConfirmada(idPassageiro);

        if ("".equals(idTransacao)) {
            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }

        boolean cancelou;

        if (idTransacao != null) {
            // Reserva composta: atualiza também o estado da transação.
            cancelou = c.cancelarReservaConfirmada(
                    idTransacao, idPassageiro
            );
        } else {
            // Reserva direta: não existe transação distribuída.
            cancelou = c.removeCarona(idPassageiro);
        }

        if (!cancelou) {
            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }

        boolean atualizouHistorico =
                usuarioLocal != null && registros.containsKey(usuarioLocal);

        if (atualizouHistorico) {
            registros.get(usuarioLocal).removerCorrida(idGlobal);
        }

        // Persiste o cancelamento.
        if (!salvarDados()) {

            boolean restaurou;

            if (idTransacao != null) {
                // Restaura a reserva composta e seu estado CONFIRMADA.
                restaurou = c.restaurarCancelamento(
                        idTransacao, idPassageiro
                );
            } else {
                // Restaura a reserva direta.
                restaurou = c.restaurarCarona(
                        idPassageiro, reserva
                );
            }

            if (atualizouHistorico) {
                registros.get(usuarioLocal).addCorrida(idGlobal);
            }

            if (!restaurou) {
                System.err.println(
                        "ERRO CRÍTICO: não foi possível restaurar a reserva "
                        + idPassageiro + " na corrida " + idGlobal
                );
            }

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.CORRIDA_CANCELADA;
    }

    // Cancela uma etapa pertencente a uma reserva composta.
    public synchronized static String cancelarReservaCompostaGlobal(
            String transacaoId,
            String idPassageiro,
            String idLocal,
            String idGlobal,
            String origem,
            String destino) {

        if (transacaoId == null || transacaoId.isBlank()
                || idPassageiro == null || idPassageiro.isBlank()
                || idLocal == null || idLocal.isBlank()
                || idGlobal == null || idGlobal.isBlank()
                || origem == null || origem.isBlank()
                || destino == null || destino.isBlank()) {

            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        corrida c = corridas.get(idLocal);

        if (c == null) {
            return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        }

        if (!identificadorGlobal.gerar(empresa, idLocal).equals(idGlobal)) {
            return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        if (c.getFinalizada()) {
            return "ERRO|" + erros.CORRIDA_FINALIZADA;
        }

        // Confere se a transação realmente pertence a esta reserva.
        if (!c.correspondeTransacao(
                transacaoId, idPassageiro, origem, destino)) {

            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }

        String estado = c.obterEstadoTransacao(transacaoId);

        // Permite repetir a solicitação sem cancelar duas vezes.
        if ("CANCELADA".equals(estado)) {
            return "OK|" + okays.CORRIDA_CANCELADA;
        }

        if (!"CONFIRMADA".equals(estado)) {
            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }

        // Preserva a reserva para eventual rollback.
        int[] reservaOriginal = c.getCarona().get(idPassageiro);

        if (reservaOriginal == null) {
            return "ERRO|" + erros.CORRIDA_NAO_RESERVADA;
        }

        boolean cancelou = c.cancelarReservaConfirmada(
                transacaoId, idPassageiro
        );

        if (!cancelou) {
            return "ERRO|" + erros.FALHA_NA_RESERVA;
        }

        // Persiste o cancelamento.
        if (!salvarDados()) {

            boolean restaurou = c.restaurarCancelamento(
                    transacaoId, idPassageiro
            );

            if (!restaurou) {
                System.err.println(
                        "ERRO CRÍTICO: não foi possível restaurar a reserva "
                        + idPassageiro + " na corrida " + idGlobal
                );
            }

            return "ERRO|" + erros.FALHA_AO_SALVAR;
        }

        return "OK|" + okays.CORRIDA_CANCELADA;
    }


    public static synchronized List<String> historicoLocal(String passageiroGlobal) {
        List<String> resultado = new ArrayList<>();

        for (Map.Entry<String, corrida> entrada : corridas.entrySet()) {
            corrida c = entrada.getValue();

            if (c.getCarona().containsKey(passageiroGlobal)) {
                String registro = "DIRETA;"
                        + identificadorGlobal.gerar(empresa, entrada.getKey()) + ";"
                        + c.getOrigem() + ";"
                        + c.getDestino() + ";"
                        + c.getPreco() + ";"
                        + c.getHorario().toString() + ";"
                        + c.getmotorista() + ";"
                        + c.getTrechos().stream().mapToInt(Integer::intValue).min().orElse(0);

                resultado.add(registro);
            }
        }

        return resultado;
    }

    public static synchronized boolean registrarHistoricoComposto(
        String passageiroGlobal,
        List<String> idsCorridas) {

        cliente passageiro = null;

        // Localiza o passageiro pelo identificador global.
        for (cliente c : registros.values()) {
            if (c.getIdGlobal().equals(passageiroGlobal)) {
                passageiro = c;
                break;
            }
        }

        if (passageiro == null) {
            return false;
        }

        // Guarda quais IDs foram adicionados para permitir rollback.
        List<String> adicionados = new ArrayList<>();

        for (String id : idsCorridas) {
            if (!passageiro.getCorridas().contains(id)) {
                passageiro.addCorrida(id);
                adicionados.add(id);
            }
        }

        // Persiste o histórico atualizado.
        if (salvarDados()) {
            return true;
        }

        // Se a persistência falhar, desfaz as alterações em memória.
        for (String id : adicionados) {
            passageiro.removerCorrida(id);
        }

        return false;
    }

    // Remove do histórico todas as etapas de uma reserva composta.
    public synchronized static boolean removerHistoricoComposto(
            String passageiroGlobal,
            List<String> idsCorridas) {

        if (passageiroGlobal == null || passageiroGlobal.isBlank()
                || idsCorridas == null || idsCorridas.isEmpty()) {
            return false;
        }

        // Localiza o passageiro pelo identificador global.
        cliente passageiro = null;

        for (cliente c : registros.values()) {
            if (c.getIdGlobal().equals(passageiroGlobal)) {
                passageiro = c;
                break;
            }
        }

        if (passageiro == null) {
            return false;
        }

        // Guarda o histórico original para eventual rollback.
        List<String> historicoOriginal =
                new ArrayList<>(passageiro.getCorridas());

        // Remove as corridas pertencentes à composta.
        passageiro.getCorridas().removeAll(idsCorridas);

        // Persiste a alteração.
        if (!salvarDados()) {

            // Restaura exatamente o histórico anterior.
            passageiro.getCorridas().clear();
            passageiro.getCorridas().addAll(historicoOriginal);

            return false;
        }

        return true;
    }
}
