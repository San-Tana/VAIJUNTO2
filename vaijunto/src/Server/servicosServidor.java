package vaijunto.src.Server;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import vaijunto.src.model.*;

public class servicosServidor {
    private static Map<String, cliente> registros = new HashMap<String, cliente>();
    private static Map<String, corrida> corridas = new HashMap<String, corrida>();
    private static Map<String, List<String>> temp = new HashMap<>(); // Lista temporária para armazenar corridas combinadas durante a busca de corridas

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

        // Dois usuários escolhem o mesmo nome ao registrar ao mesmo tempo, apenas um pode ter o nome
        String checagem = existeUsuario(registro[0]);
        if (checagem.startsWith("ERRO")) return checagem;

        // Cliente tem ou não veiculo
        if (registro.length == 3) {
            // motorista[0] = CNH
            // motorista[1] = tipo
            // motorista[2] = modelo
            // motorista[3] = placa
            String motorista[] = registro[2].split("\\&");
            motorista veiculo = new motorista(motorista[0], motorista[1], motorista[2], motorista[3]);
            cliente novo = new cliente(registro[0], registro[1], veiculo);
            registros.put(novo.getUsuario(), novo);
        }
        else { 
            cliente novo = new cliente(registro[0], registro[1]); 
            registros.put(novo.getUsuario(), novo);
        }

        System.out.println("Cliente registrado: " + registros.get(registro[0]));
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

    // Registra uma nova corrida, seja ela com ou sem paradas, e adiciona a corrida no histórico do usuário
    // corrida[0] = origem
    // corrida[1] = destino
    // corrida[2] = horario
    // corrida[3] = assentos
    // corrida[4] = preco;
    // corrida[5] = paradas
    public synchronized static String publicarCorrida(String usuario, String dados) {
        String corrida[] = dados.split("\\;");
        String data[] = corrida[2].split("\\&");
        data horario = new data(data[0], data[1], data[2], data[3]);
        int bancos = Integer.parseInt(corrida[3]);
        int preco = Integer.parseInt(corrida[4]);

        String ID = String.format("CAR%03d", corridas.size() + 1);

        if (corrida.length == 6) {
            List<String> paradas = Arrays.asList(corrida[5].split("\\&"));
            corridas.put(ID, new corrida(ID, usuario, corrida[0], corrida[1], horario, bancos, preco, paradas));
            registros.get(usuario).addCorrida(ID);
        }
        else { 
            corridas.put(ID, new corrida(ID, usuario, corrida[0], corrida[1], horario, bancos, preco));
            registros.get(usuario).addCorrida(ID);
        }

        System.out.println("Corrida registrada: " + corridas.get(ID).getID());
        return "OK|" + okays.CORRIDA_CRIADA;
    }

    // Reserva um assento da corrida para o usuario, um por vez para que um usuário não reserve um assento que não existe
    public synchronized static String reservarCorrida(String usuario, String ID) {
        if (ID.startsWith("CAR")) {
            if (!corridas.containsKey(ID)) return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
            // Verifica se a corrida está cheia, se já foi finalizada, se o usuário já reservou um assento ou se o usuário é o motorista da corrida
            if (corridas.get(ID).getBancos() == 0) return "ERRO|" + erros.CORRIDA_CHEIA;
            else if (corridas.get(ID).getFinalizada()) return "ERRO|" + erros.CORRIDA_FINALIZADA;
            else if (corridas.get(ID).getCarona().contains(usuario)) return "ERRO|" + erros.CORRIDA_JA_RESERVADA;
            else if (corridas.get(ID).getmotorista().equals(usuario)) return "ERRO|" + erros.CORRIDA_MOTORISTA;
            else {
                corridas.get(ID).addCarona(usuario);
                registros.get(usuario).addCorrida(ID);

                temp.remove(usuario);
                return "OK|" + okays.CORRIDA_RESERVADA;
            }
        }

        else if (ID.startsWith("COM")) {
            List<String> compostas = temp.get(usuario);

            if (compostas == null || compostas.isEmpty()) return "ERRO|" + erros.ID_INVALIDO;

            for (String comp : compostas) {
                String corrida[] = comp.split(";");
                if (!ID.equals(corrida[0])) continue;

                // Verifica se uma das corridas encheu durante a tentativa de reserva, se o usuário já reservou uma das corridas, 
                // se o usuário é o motorista de uma das corridas ou se uma das corridas foi finalizada
                if (corridas.get(corrida[1]).getBancos() == 0 || corridas.get(corrida[2]).getBancos() == 0) 
                    return "ERRO|" + erros.CORRIDA_CHEIA;
                else if (corridas.get(corrida[1]).getCarona().contains(usuario) || corridas.get(corrida[2]).getCarona().contains(usuario)) 
                    return "ERRO|" + erros.CORRIDA_JA_RESERVADA;
                else if (corridas.get(corrida[1]).getFinalizada() || corridas.get(corrida[2]).getFinalizada()) 
                    return "ERRO|" + erros.CORRIDA_FINALIZADA;
                else if (corridas.get(corrida[1]).getmotorista().equals(usuario) || corridas.get(corrida[2]).getmotorista().equals(usuario)) 
                    return "ERRO|" + erros.CORRIDA_MOTORISTA;

                // Cria uma corrida composta com as duas corridas
                else if (ID.equals(corrida[0])) {
                    corridaComposta corridaDupla = new corridaComposta(ID, corridas.get(corrida[1]), corridas.get(corrida[2]), corrida[3], corrida[4]);
                    corridas.put(corridaDupla.getID(), corridaDupla);
                    corridas.get(ID).addCarona(usuario);
                    corridas.get(corrida[1]).addCarona(usuario);
                    corridas.get(corrida[2]).addCarona(usuario);
                    registros.get(usuario).addCorrida(ID);

                    temp.remove(usuario);
                    return "OK|" + okays.CORRIDA_RESERVADA;
                }
            }
        }
        
        temp.remove(usuario);
        return "ERRO|" + erros.ID_INVALIDO;
    }

    public synchronized static String historico(String usuario) {
        if (registros.get(usuario).getCorridas().isEmpty()) return "ERRO|" + erros.HISTORICO_VAZIO;
        
        List<String> encontrados = new ArrayList<String>();
        for (String corrida : registros.get(usuario).getCorridas()) {
            corrida corridaAtual = corridas.get(corrida);
            encontrados.add(String.format("---------- Corrida finalizada: %s ----------\\n" +
                "motorista: %s\\norigem: %s\\ndestino: %s\\n" +
                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                corridaAtual.getID(), corridaAtual.getmotorista(), corridaAtual.getOrigem(), corridaAtual.getDestino(),
                corridaAtual.getHorario().getDia(), corridaAtual.getHorario().getMes(), corridaAtual.getHorario().getAno(), 
                corridaAtual.getHorario().getHora(), corridaAtual.getBancos(), corridaAtual.getPreco()));
        }

        String historico = String.join(";", encontrados);
        return "OK|" + okays.HISTORICO_ENCONTRADO + "|" + historico;
    }

    // Cancela a corrida para motorista ou passageiro, se for motorista, a corrida é finalizada, se for passageiro, remove a carona do histórico do usuário e da corrida
    public synchronized static String cancelarCorrida(String usuario, String ID) {
        if (ID.startsWith("CAR")) {
            if (!corridas.containsKey(ID)) return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
            else if (corridas.get(ID).getFinalizada()) return "ERRO|" + erros.CORRIDA_FINALIZADA;

            if (corridas.get(ID).getmotorista().equals(usuario)) {
                corridas.get(ID).finalizarCorrida(); // Corrida fica no histórico do motorista e dos passageiros, mas não pode mais ser reservada
            
                // Procura essa corrida entre as corridas compostas
                for (String corrida : corridas.keySet()) {
                    if (corrida.startsWith("COM")) {
                        cancelarComposta((corridaComposta) corridas.get(corrida), usuario, ID);
                    }
                }

                return "OK|" + okays.CORRIDA_CANCELADA;
            }
            
            else if (corridas.get(ID).getCarona().contains(usuario)) {
                corridas.get(ID).removeCarona(usuario);
                registros.get(usuario).removerCorrida(ID);
                return "OK|" + okays.CORRIDA_CANCELADA;
            }

            else return "ERRO|" + erros.CORRIDA_NAO_RESERVADA;
        }

        else if (ID.startsWith("COM")) {
            if (!corridas.containsKey(ID)) return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
            // Verifica se o usuário reservou a corrida composta
            if (!corridas.get(ID).getCarona().contains(usuario)) return "ERRO|" + erros.CORRIDA_NAO_RESERVADA;

            String composta = cancelarComposta((corridaComposta) corridas.get(ID), usuario, ID);
            if (composta.startsWith("OK")) {
                corridas.get(ID).finalizarCorrida();
                return "OK|" + okays.CORRIDA_CANCELADA;
            }
        }

        return "ERRO|" + erros.ID_INVALIDO;
    }

    public synchronized static String cancelarComposta(corridaComposta corrida, String usuario, String ID) {
        String corrida1 = corrida.getCorrida1();
        String corrida2 = corrida.getCorrida2();

        // Corrida1 é a corrida cancelada e o usuário é o motorista
        if (corrida1.equals(ID) && corridas.get(corrida1).getmotorista().equals(usuario)) {
            corridas.get(corrida.getID()).finalizarCorrida();
            corridas.get(corrida2).removeCarona(usuario);
        }

        // Corrida2 é a corrida cancelada e o usuário é o motorista
        else if (corrida2.equals(ID) && corridas.get(corrida2).getmotorista().equals(usuario)) {
            corridas.get(corrida.getID()).finalizarCorrida();
            corridas.get(corrida1).removeCarona(usuario);
        }

        // Corrida1 é a corrida cancelada e usuário é passageiro
        else if (corrida1.equals(ID) && corridas.get(corrida1).getCarona().contains(usuario)) {
            corridas.get(corrida1).removeCarona(usuario);
        }

        // Corrida2 é a corrida cancelada e usuário é passageiro
        else if (corrida2.equals(ID) && corridas.get(corrida2).getCarona().contains(usuario)) {
            corridas.get(corrida2).removeCarona(usuario);
        }

        // corrida composta é a corrida cancelada
        else if (corrida.getID().equals(ID)) {
            corridas.get(corrida1).removeCarona(usuario);
            corridas.get(corrida2).removeCarona(usuario);
        }

        return "OK|" + okays.CORRIDA_CANCELADA;
    }

    // Compara combinações de corrida para evitar repetir uma mesma corrida combinada
    public static boolean compararComps(List<String> comp, String corrida1, String corrida2) {
        for (String corrida : comp) {
            String corridas[] = corrida.split(";");
            if (corrida1.equals(corridas[1]) && corrida2.equals(corridas[2])) return true;
            else if (corrida1.equals(corridas[2]) && corrida2.equals(corridas[1])) return true;
        }
        return false;
    }    


    /* Ferramenta de busca de corridas. Busca combinações de origem-destino, parada-destino, origem-parada, parada-parada 
       para entregar uma possível corrida para o usuário, inclusive combinando corridas. */
    public synchronized static String buscarCorridas(String usuario, String dados) {
        String busca[] = dados.split("\\;");
        String origem = busca[0];
        String destino = busca[1];
        List<String> encontradas = new ArrayList<String>();
        List<String> compostas = new ArrayList<String>();

        // Busca corridas que tenham a cidade de origem e destino desejada entre a sua origem, destino e paradas.
        for (String corrida : corridas.keySet()) {
            corrida corridaAtual = corridas.get(corrida);
            if (corridaAtual.getFinalizada() || corridaAtual.getBancos() == 0) continue;

            // Corrida sai da cidade de origem e termina na cidade de destino
            else if (corridaAtual.getOrigem().equals(origem) && corridaAtual.getDestino().equals(destino)) {
                encontradas.add(String.format("---------- Corrida até destino (final): %s ----------\\n" + //
                    "motorista: %s\\norigem: %s\\ndestino: %s\\n" +
                    "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                    corridaAtual.getID(), corridaAtual.getmotorista(), corridaAtual.getOrigem(), corridaAtual.getDestino(), 
                    corridaAtual.getHorario().getDia(), corridaAtual.getHorario().getMes(), corridaAtual.getHorario().getAno(), 
                    corridaAtual.getHorario().getHora(), corridaAtual.getBancos(), corridaAtual.getPreco()));
            }

            else if (!corridaAtual.getParadas().isEmpty()) {
                for (String parada : corridaAtual.getParadas()) {
                    // Corrida sai da origem e passa pelo destino
                    if (corridaAtual.getOrigem().equals(origem) && parada.equals(destino)) {
                        encontradas.add(String.format("---------- Corrida até destino (parada): %s ----------\\n" +
                            "motorista: %s\\norigem: %s\\nparada de descida: %s (%dª parada)\\n" +
                            "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço por trecho: %d", 
                            corridaAtual.getID(), corridaAtual.getmotorista(), corridaAtual.getOrigem(), parada, 
                            corridaAtual.getParadas().indexOf(parada) + 1, 
                            corridaAtual.getHorario().getDia(), corridaAtual.getHorario().getMes(), corridaAtual.getHorario().getAno(), 
                            corridaAtual.getHorario().getHora(), corridaAtual.getBancos(), corridaAtual.getPreco()));
                    }

                    // Corrida passa pela origem e termina no destino
                    else if (parada.equals(origem) && corridaAtual.getDestino().equals(destino)) {
                        encontradas.add(String.format("---------- Corrida até destino (parada): %s ----------\\n" +
                            "motorista: %s\\nparada de embarque: %s (%dª parada)\\ndestino: %s\\n" +
                            "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço por trecho: %d", 
                            corridaAtual.getID(), corridaAtual.getmotorista(), parada, 
                            corridaAtual.getParadas().indexOf(parada) + 1, corridaAtual.getDestino(), 
                            corridaAtual.getHorario().getDia(), corridaAtual.getHorario().getMes(), corridaAtual.getHorario().getAno(), 
                            corridaAtual.getHorario().getHora(), corridaAtual.getBancos(), corridaAtual.getPreco()));
                    }

                    // Verifica se a Corrida passa pela origem e pelo destino
                    else if (parada.equals(origem)) {
                        for (String parada2 : corridaAtual.getParadas()) {
                            // Ignora se a parada destino vem antes de parada origem ou se é a mesma parada
                            if (corridaAtual.getParadas().indexOf(parada2) <= corridaAtual.getParadas().indexOf(parada)) continue;

                            else if (parada2.equals(destino)) {
                                encontradas.add(String.format("---------- Corrida até destino (paradas): %s ----------\\n" +
                                    "motorista: %s\\nparada de embarque: %s (%dª parada)\\nparada de descida: %s (%dª parada)\\n" +
                                    "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço por trecho: %d", 
                                    corridaAtual.getID(), corridaAtual.getmotorista(), parada, corridaAtual.getParadas().indexOf(parada) + 1, 
                                    parada2, corridaAtual.getParadas().indexOf(parada2) + 1, 
                                    corridaAtual.getHorario().getDia(), corridaAtual.getHorario().getMes(), corridaAtual.getHorario().getAno(), 
                                    corridaAtual.getHorario().getHora(), corridaAtual.getBancos(), corridaAtual.getPreco()));
                                break; // Já achou o destino, não precisa continuar procurando
                            }
                        }
                    }
                }
            }
        }

        /* Busca duas corridas, onde a primeira sai da cidade de origem e a segunda sai de uma parada em comum
           com a primeira para a cidade destino, então monta uma corrida combinada */
        for (String corrida1 : corridas.keySet()) {
            corrida corridaAtual1 = corridas.get(corrida1);
            if (corridaAtual1.getFinalizada() || corridaAtual1.getBancos() == 0) continue;
            
            for (String corrida2 : corridas.keySet()) {
                corrida corridaAtual2 = corridas.get(corrida2);
                if (corridaAtual2.getFinalizada() || corridaAtual2.getBancos() == 0 || corridaAtual2.equals(corridaAtual1) ||
                    corridaAtual2.compararHora(corridaAtual1.getHorario())) continue;
                
                // Corrida 1 sai da origem e termina na origem da corrida 2, corrida 2 termina no destino
                else if (corridaAtual1.getOrigem().equals(origem) && corridaAtual2.getOrigem().equals(corridaAtual1.getDestino())
                    && corridaAtual2.getDestino().equals(destino) && !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                    String ID = String.format("COM%03d", corridas.size() + 1);
                    String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() + ";" + origem + ";" + destino;
                    compostas.add(composta);

                    encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                        "motorista 1: %s\\norigem: %s\\ndestino: %s\\n" +
                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                        "motorista 2: %s\\norigem: %s\\ndestino: %s\\n" + 
                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                        ID, corridaAtual1.getmotorista(), 
                        corridaAtual1.getOrigem(), corridaAtual1.getDestino(), 
                        corridaAtual1.getHorario().getDia(), 
                        corridaAtual1.getHorario().getMes(), 
                        corridaAtual1.getHorario().getAno(), 
                        corridaAtual1.getHorario().getHora(), 
                        corridaAtual1.getBancos(), 
                        corridaAtual1.getPreco(), 
                        corridaAtual2.getmotorista(), 
                        corridaAtual2.getOrigem(), 
                        corridaAtual2.getDestino(), 
                        corridaAtual2.getHorario().getDia(), 
                        corridaAtual2.getHorario().getMes(), 
                        corridaAtual2.getHorario().getAno(), 
                        corridaAtual2.getHorario().getHora(), 
                        corridaAtual2.getBancos(), 
                        corridaAtual2.getPreco()));
                }

                else if (!corridaAtual1.getParadas().isEmpty()) {
                    for (String parada : corridaAtual1.getParadas()) {

                        // Corrida 1 sai da cidade de origem e passa pela origem da corrida 2, corrida 2 termina no destino
                        if (corridaAtual1.getOrigem().equals(origem) && corridaAtual2.getOrigem().equals(parada) && corridaAtual2.getDestino().equals(destino)
                            && !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                            String ID = String.format("COM%03d", corridas.size() + 1);
                            String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() + ";" + origem + ";" + destino;
                            compostas.add(composta);

                            encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                                "motorista 1: %s\\norigem: %s\\nparada de descida: %s (%dª parada)\\n" +
                                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                                "motorista 2: %s\\norigem: %s\\ndestino: %s\\n" + 
                                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                                ID, corridaAtual1.getmotorista(), 
                                corridaAtual1.getOrigem(), 
                                parada, corridaAtual1.getParadas().indexOf(parada) + 1, 
                                corridaAtual1.getHorario().getDia(), 
                                corridaAtual1.getHorario().getMes(), 
                                corridaAtual1.getHorario().getAno(), 
                                corridaAtual1.getHorario().getHora(), 
                                corridaAtual1.getBancos(), 
                                corridaAtual1.getPreco(), 
                                corridaAtual2.getmotorista(), 
                                corridaAtual2.getOrigem(), 
                                corridaAtual2.getDestino(), 
                                corridaAtual2.getHorario().getDia(), 
                                corridaAtual2.getHorario().getMes(), 
                                corridaAtual2.getHorario().getAno(), 
                                corridaAtual2.getHorario().getHora(), 
                                corridaAtual2.getBancos(), 
                                corridaAtual2.getPreco()));
                        }

                        // Corrida 1 sai da origem
                        else if (corridaAtual1.getOrigem().equals(origem) && !corridaAtual2.getParadas().isEmpty()) {
                            for (String parada3 : corridaAtual2.getParadas()) { 

                                // Corrida 1 passa pela parada da corrida 2 e a corrida 2 termina no destino
                                if (parada.equals(parada3) && corridaAtual2.getDestino().equals(destino)
                                    && !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                                    String ID = String.format("COM%03d", corridas.size() + 1);
                                    String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() + ";" + origem + ";" + destino;
                                    compostas.add(composta);

                                    encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                                        "motorista 1: %s\\norigem: %s\\nparada de descida: %s (%dª parada)\\n" +
                                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                                        "motorista 2: %s\\nparada de embarque: %s (%dª parada)\\ndestino: %s\\n" + 
                                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                                        ID, corridaAtual1.getmotorista(), 
                                        corridaAtual1.getOrigem(), 
                                        parada, corridaAtual1.getParadas().indexOf(parada) + 1, 
                                        corridaAtual1.getHorario().getDia(), 
                                        corridaAtual1.getHorario().getMes(), 
                                        corridaAtual1.getHorario().getAno(), 
                                        corridaAtual1.getHorario().getHora(), 
                                        corridaAtual1.getBancos(), 
                                        corridaAtual1.getPreco(), 
                                        corridaAtual2.getmotorista(), 
                                        parada3, corridaAtual2.getParadas().indexOf(parada3) + 1, 
                                        corridaAtual2.getDestino(), 
                                        corridaAtual2.getHorario().getDia(), 
                                        corridaAtual2.getHorario().getMes(), 
                                        corridaAtual2.getHorario().getAno(), 
                                        corridaAtual2.getHorario().getHora(), 
                                        corridaAtual2.getBancos(), 
                                        corridaAtual2.getPreco()));
                                }
                                
                                // Corrida 1 passa pela parada da corrida 2 e corrida 2 passa pelo destino
                                else if (parada.equals(parada3)) {
                                    for (String parada4 : corridaAtual2.getParadas()) {
                                        if (corridaAtual2.getParadas().indexOf(parada4) <= corridaAtual2.getParadas().indexOf(parada3)) continue;

                                        else if (parada4.equals(destino) && !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                                            String ID = String.format("COM%03d", corridas.size() + 1);
                                            String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() + ";" + origem + ";" + destino;
                                            compostas.add(composta);

                                            encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                                                "motorista 1: %s\\norigem: %s\\nparada de descida: %s (%dª parada)\\n" +
                                                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                                                "motorista 2: %s\\nparada de embarque: %s (%dª parada)\\ndestino: %s\\n" + 
                                                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                                                ID, corridaAtual1.getmotorista(), 
                                                corridaAtual1.getOrigem(), 
                                                parada, corridaAtual1.getParadas().indexOf(parada) + 1, 
                                                corridaAtual1.getHorario().getDia(), 
                                                corridaAtual1.getHorario().getMes(), 
                                                corridaAtual1.getHorario().getAno(), 
                                                corridaAtual1.getHorario().getHora(), 
                                                corridaAtual1.getBancos(), 
                                                corridaAtual1.getPreco(), 
                                                corridaAtual2.getmotorista(), 
                                                parada3, corridaAtual2.getParadas().indexOf(parada3) + 1, 
                                                parada4, corridaAtual2.getParadas().indexOf(parada4) + 1,  
                                                corridaAtual2.getHorario().getDia(), 
                                                corridaAtual2.getHorario().getMes(), corridaAtual2.getHorario().getAno(), 
                                                corridaAtual2.getHorario().getHora(), 
                                                corridaAtual2.getBancos(), 
                                                corridaAtual2.getPreco()));
                                        }
                                    }
                                }
                            }
                        }

                        // Corrida 1 passa pela origem
                        else if (parada.equals(origem) && !corridaAtual2.getParadas().isEmpty()) {
                            for (String parada2 : corridaAtual1.getParadas()) {
                                if (corridaAtual1.getParadas().indexOf(parada2) <= corridaAtual1.getParadas().indexOf(parada)) continue;
                                
                                // corrida 1 passa pela origem da corrida 2 e corrida 2 termina no destino
                                else if (parada2.equals(corridaAtual2.getOrigem()) && corridaAtual2.getDestino().equals(destino)
                                    && !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                                    String ID = String.format("COM%03d", corridas.size() + 1);
                                    String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() + ";" + origem + ";" + destino;
                                    compostas.add(composta);

                                    encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                                        "motorista 1: %s\\nparada de embarque: %s (%dª parada)\\nparada de descida: %s (%dª parada)\\n" +
                                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                                        "motorista 2: %s\\norigem: %s\\ndestino: %s\\n" + 
                                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                                        ID, corridaAtual1.getmotorista(), 
                                        parada, corridaAtual1.getParadas().indexOf(parada) + 1, 
                                        parada2, corridaAtual1.getParadas().indexOf(parada2) + 1, 
                                        corridaAtual1.getHorario().getDia(), 
                                        corridaAtual1.getHorario().getMes(), 
                                        corridaAtual1.getHorario().getAno(), 
                                        corridaAtual1.getHorario().getHora(), 
                                        corridaAtual1.getBancos(), 
                                        corridaAtual1.getPreco(), 
                                        corridaAtual2.getmotorista(), 
                                        corridaAtual2.getOrigem(), 
                                        corridaAtual2.getDestino(), 
                                        corridaAtual2.getHorario().getDia(), 
                                        corridaAtual2.getHorario().getMes(), 
                                        corridaAtual2.getHorario().getAno(), 
                                        corridaAtual2.getHorario().getHora(), 
                                        corridaAtual2.getBancos(), 
                                        corridaAtual2.getPreco()));
                                }

                                // Corrida 1 passa pela parada da corrida 2
                                else if (corridaAtual2.getOrigem().equals(parada2)) {
                                    for (String parada3 : corridaAtual2.getParadas()) {
                                        
                                        // corrida 2 termina no destino
                                        if (parada3.equals(parada2) && corridaAtual2.getDestino().equals(destino) && 
                                            !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                                            String ID = String.format("COM%03d", corridas.size() + 1);
                                            String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() + ";" + origem + ";" + destino;
                                            compostas.add(composta);

                                            encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                                                "motorista 1: %s\\nparada de embarque: %s (%dª parada)\\nparada de descida: %s (%dª parada)\\n" +
                                                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                                                "motorista 2: %s\\nparada de embarque: %s (%dª parada)\\ndestino: %s\\n" + 
                                                "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                                                ID, corridaAtual1.getmotorista(), 
                                                parada, corridaAtual1.getParadas().indexOf(parada) + 1, 
                                                parada2, corridaAtual1.getParadas().indexOf(parada2) + 1, 
                                                corridaAtual1.getHorario().getDia(), 
                                                corridaAtual1.getHorario().getMes(), 
                                                corridaAtual1.getHorario().getAno(), 
                                                corridaAtual1.getHorario().getHora(), 
                                                corridaAtual1.getBancos(), 
                                                corridaAtual1.getPreco(), 
                                                corridaAtual2.getmotorista(), 
                                                parada3, corridaAtual2.getParadas().indexOf(parada3) + 1, 
                                                corridaAtual2.getDestino(), 
                                                corridaAtual2.getHorario().getDia(), 
                                                corridaAtual2.getHorario().getMes(), 
                                                corridaAtual2.getHorario().getAno(), 
                                                corridaAtual2.getHorario().getHora(), 
                                                corridaAtual2.getBancos(), 
                                                corridaAtual2.getPreco()));
                                        }
                                        
                                        // Corrida 2 passa pelo destino
                                        else if (parada3.equals(parada2)) {
                                            for (String parada4 : corridaAtual2.getParadas()) {
                                                if (corridaAtual2.getParadas().indexOf(parada4) <= corridaAtual2.getParadas().indexOf(parada3)) continue;

                                                else if (parada4.equals(destino) && !compararComps(compostas, corridaAtual1.getID(), corridaAtual2.getID())) {
                                                    String ID = String.format("COM%03d", corridas.size() + 1);
                                                    String composta = ID + ";" + corridaAtual1.getID() + ";" + corridaAtual2.getID() 
                                                                    + ";" + origem + ";" + destino;
                                                    compostas.add(composta);

                                                    encontradas.add(String.format("---------- Corrida combinada: %s ----------\\n" +
                                                        "motorista 1: %s\\nparada de embarque: %s (%dª parada)\\nparada de descida: %s (%dª parada)\\n" +
                                                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d" +
                                                        "motorista 2: %s\\nparada de embarque: %s (%dª parada)\\nparada de descida: %s (%dª parada)\\n" + 
                                                        "data e horário: %s/%s/%s %s\\nassentos disponíveis: %d\\npreço: %d", 
                                                        ID, corridaAtual1.getmotorista(), 
                                                        parada, corridaAtual1.getParadas().indexOf(parada) + 1, 
                                                        parada2, corridaAtual1.getParadas().indexOf(parada2) + 1, 
                                                        corridaAtual1.getHorario().getDia(), 
                                                        corridaAtual1.getHorario().getMes(), 
                                                        corridaAtual1.getHorario().getAno(), 
                                                        corridaAtual1.getHorario().getHora(), 
                                                        corridaAtual1.getBancos(), 
                                                        corridaAtual1.getPreco(), 
                                                        corridaAtual2.getmotorista(), 
                                                        parada3, corridaAtual2.getParadas().indexOf(parada3) + 1, 
                                                        parada4, corridaAtual2.getParadas().indexOf(parada4) + 1,
                                                        corridaAtual2.getHorario().getDia(), 
                                                        corridaAtual2.getHorario().getMes(), 
                                                        corridaAtual2.getHorario().getAno(), 
                                                        corridaAtual2.getHorario().getHora(), 
                                                        corridaAtual2.getBancos(), 
                                                        corridaAtual2.getPreco()));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }       
        }

        if (encontradas.isEmpty()) return "ERRO|" + erros.CORRIDA_NAO_ENCONTRADA;
        if (!compostas.isEmpty()) temp.put(usuario, compostas);

        String lista = String.join(";", encontradas);
        return "OK|" + okays.CORRIDA_ENCONTRADA + "|" + lista;
    }
}
