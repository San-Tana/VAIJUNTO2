package vaijunto.src.App;

import java.util.Scanner;
import java.util.stream.Stream;
import java.util.List;

import vaijunto.src.model.*;

public class servicosClient {

    public static String logarCliente(Scanner scan) {
        System.out.print("\nUsuario: ");
        String usuario = scan.nextLine().trim();

        System.out.print("\nSenha: ");
        String senha = scan.nextLine();

        if (Stream.of(usuario, senha).anyMatch(campo -> campo == null || campo.trim().isEmpty()))
            return "ERRO|" + erros.CAMPO_VAZIO;

        return usuario + ";" + senha;
    }

    public static String registroNome(Scanner scan) {
        System.out.print("\nUsuario: ");
        return scan.nextLine().trim();
    }

    public static String registrarCliente(Scanner scan, String usuario) {
        System.out.print("\nSenha: ");
        String senha = scan.nextLine();

        if (Stream.of(usuario, senha).anyMatch(campo -> campo == null || campo.trim().isEmpty()))
            return "ERRO|" + erros.CAMPO_VAZIO;

        System.out.print("\nDeseja registrar um veículo? [S/N]: ");
        String resposta = scan.nextLine().trim().toUpperCase();
        if (resposta.equals("S") || resposta.equals("SIM")) {
            String veiculo = registrarVeiculo(scan);
            if (veiculo.startsWith("ERRO")) return veiculo;
            
            return usuario + ";" + senha + ";" + veiculo;
        }

        return usuario + ";" + senha;
    }

    public static String registrarVeiculo(Scanner scan) {
        System.out.print("\nRegistre a CNH: ");
        String CNH = scan.nextLine().trim();

        System.out.print("\nTipo de veículo (Ex: carro, moto ou caminhão): ");
        String tipo = scan.nextLine().trim();

        System.out.print("\nModelo do veículo: ");
        String modelo = scan.nextLine().trim();

        System.out.print("\nPlaca do veículo: ");
        String placa = scan.nextLine().trim();

        if (Stream.of(CNH, tipo, modelo, placa).anyMatch(campo -> campo == null || campo.trim().isEmpty()))
            return "ERRO|" + erros.CAMPO_VAZIO;

        return CNH + "&" + tipo + "&" + modelo + "&" + placa;
    }

    public static String buscarCorrida(Scanner scan) {
        System.out.print("\nQual a origem da viajem? ");
        String origem = scan.nextLine().trim();

        System.out.print("\nQual o destino da viajem?: ");
        String destino = scan.nextLine().trim();

        if (origem.isEmpty() || destino.isEmpty())
            return "ERRO|" + erros.CAMPO_VAZIO;

        return origem + ";" + destino;
    }

    public static String novaViajem(Scanner scan) {
        String paradas = "";

        System.out.print("\nInforme a cidade de origem: ");
        String origem = scan.nextLine().trim();

        System.out.print("\nInforme a cidade destino: ");
        String destino = scan.nextLine().trim();

        String horario = definirHorario(scan);
        if (horario.startsWith("ERRO")) return horario;

        System.out.print("\nInforme a quantidade de assentos disponíveis: ");
        String assentos = scan.nextLine().trim();

        System.out.print("\nInforme o preço médio por cada trecho (Somente números inteiros): ");
        String preco = scan.nextLine().trim();
        if (!assentos.matches("[0-9]+") || !preco.matches("[0-9]+")) return "ERRO|" + erros.ENTRADA_INVALIDA;

        System.out.print("\nDeseja adicionar paradas? [S/N]");
        String parada = scan.nextLine().trim();
        if (parada.matches("(?i)^(sim|s)$")) {
            paradas = adicionarParadas(scan);
            if (paradas.startsWith("ERRO")) return paradas;
        }
        else if (!parada.matches("(?i)^(nao|não|n)$"))
            return "ERRO|" + erros.ENTRADA_INVALIDA;

        if (Stream.of(origem, destino, assentos).anyMatch(campo -> campo == null || campo.trim().isEmpty()))
            return "ERRO|" + erros.CAMPO_VAZIO;

        if (paradas.isEmpty()) {
            paradas = origem + "&" + destino;
        } else {
            paradas = origem + "&" + paradas + "&" + destino;
        }
        return origem + ";" + destino + ";" + horario + ";" + assentos + ";" + preco + ";" + paradas;
    }

    public static String definirHorario(Scanner scan) {
        System.out.print("\nInforme o dia da viajem: ");
        String dia = scan.nextLine().trim();

        System.out.print("\nInforme o mês da viajem (Em forma numérica): ");
        String mes = scan.nextLine().trim();

        System.out.print("\nInforme o ano da viajem: ");
        String ano = scan.nextLine().trim();

        System.out.print("\nInforme o horário da viajem: ");
        String horario = scan.nextLine().trim();

        if (Stream.of(dia, mes, ano, horario).anyMatch(campo -> campo == null || campo.trim().isEmpty()))
            return "ERRO|" + erros.CAMPO_VAZIO;
        else if (!Stream.of(dia, mes, ano).allMatch(campo -> campo.matches("[0-9]+")) 
            || !horario.matches("[0-9]{2}:[0-9]{2}"))
            return "ERRO|" + erros.ENTRADA_INVALIDA;

        return dia + "&" + mes + "&" + ano + "&" + horario;
    }

    public static String adicionarParadas(Scanner scan) {
        int sair = 0;
        String paradas = "";

        while (sair != 1) {
            System.out.print("\nInforme o nome da cidade: ");
            String cidade = scan.nextLine().trim();

            if (cidade.isEmpty())
                return "ERRO|" + erros.ENTRADA_INVALIDA;

            paradas += cidade;

            System.out.print("\nDeseja adicionar outra parada? [S/N]: ");
            String resposta = scan.nextLine().trim();

            if (resposta.matches("(?i)^(nao|não|n)$"))
                sair = 1;
            else if (resposta.matches("(?i)^(sim|s)$")) {
                paradas += "&";
                continue;
            }
            else
                return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        return paradas;
    }

    public static void exibirCorridas(List<String> corridas) {
        int contador = 1;

        for (String corrida : corridas) {

            // Identifica o tipo antes de interpretar os campos.
            String tipo = corrida.startsWith("COMPOSTA;")
                    ? "COMPOSTA" : "DIRETA";

            // A corrida composta possui etapas com seus próprios ';'.
            String[] dados = tipo.equals("COMPOSTA")
                    ? corrida.split(";", 6)
                    : corrida.split(";", -1);

            System.out.println("\n========== OPÇÃO " + contador + " ==========");

            if (tipo.equals("DIRETA")) {

                if (dados.length != 8) {
                    System.out.println("Dados da corrida inválidos.");
                    continue;
                }

                String dia = formatarHorario(dados[5]);

                System.out.println("Tipo: Corrida direta");
                System.out.println("ID: " + dados[1]);
                System.out.println("Origem: " + dados[2]);
                System.out.println("Destino: " + dados[3]);
                System.out.println("Preço: R$ " + dados[4]);
                System.out.println("Horário: " + dia);
                System.out.println("Motorista: " + dados[6]);
                System.out.println("Vagas disponíveis: " + dados[7]);

            } else {

                if (dados.length != 6) {
                    System.out.println("Dados da corrida composta inválidos.");
                    continue;
                }

                System.out.println("Tipo: Corrida composta");
                System.out.println("ID: " + dados[1]);
                System.out.println("Origem: " + dados[2]);
                System.out.println("Destino: " + dados[3]);
                System.out.println("Preço total: R$ " + dados[4]);

                String[] etapas = dados[5].split("#", -1);

                for (int i = 0; i < etapas.length; i++) {
                    String[] etapa = etapas[i].split(";", -1);

                    if (etapa.length != 7) {
                        System.out.println("Dados da etapa inválidos.");
                        continue;
                    }

                    String dia = formatarHorario(etapa[4]);

                    System.out.println("\n  Etapa " + (i + 1));
                    System.out.println("  ID: " + etapa[0]);
                    System.out.println("  Origem: " + etapa[1]);
                    System.out.println("  Destino: " + etapa[2]);
                    System.out.println("  Preço: R$ " + etapa[3]);
                    System.out.println("  Horário: " + dia);
                    System.out.println("  Motorista: " + etapa[5]);
                    System.out.println("  Vagas disponíveis: " + etapa[6]);
                }
            }

            contador++;
        }
    }

    // Transforma o horario do formato que é armazenado para o formato de apresentação
    public static String formatarHorario(String horarioCompleto) {
        String[] horario = horarioCompleto.split("&");

        if (horario.length != 4)
            return horarioCompleto;

        return horario[0] + "/" + horario[1] + "/" + horario[2]
                + ", às " + horario[3];
    }

    public static void mensagensOkays(String mensagem) {
        if (mensagem.equals(okays.USUARIO_LOGADO))
            System.out.println("Cliente logado com sucesso, bem vindo de volta!");
        else if (mensagem.equals(okays.USUARIO_REGISTRADO))
            System.out.println("Cliente registrado com sucesso, bem vindo!");
        else if (mensagem.equals(okays.USUARIO_DESLOGADO))
            System.out.println("Cliente deslogado com sucesso, até a próxima!");
        else if (mensagem.equals(okays.NOME_DISPONIVEL))
            System.out.println("Nome de usuário disponível.");
        else if (mensagem.equals(okays.CORRIDA_CRIADA))
            System.out.println("Corrida criada com sucesso!");
        else if (mensagem.equals(okays.CORRIDA_ENCONTRADA))
            System.out.println("Corrida encontrada com sucesso!");
        else if (mensagem.equals(okays.CORRIDA_RESERVADA))
            System.out.println("Corrida reservada com sucesso!");
        else if (mensagem.equals(okays.CORRIDA_CANCELADA))
            System.out.println("Corrida cancelada com sucesso!");
        else if (mensagem.equals(okays.HISTORICO_ENCONTRADO))
            System.out.println("Histórico encontrado.");
        else if (mensagem.equals(okays.EMPRESA_CONFIGURADA))
            System.out.println("Empresa configurada com sucesso.");
        else if (mensagem.equals(okays.RESERVA_PREPARADA))
            System.out.println("Reserva preparada com sucesso.");
        else if (mensagem.equals(okays.RESERVA_CONFIRMADA))
            System.out.println("Reserva confirmada com sucesso.");
        else if (mensagem.equals(okays.RESERVA_ABORTADA))
            System.out.println("Reserva abortada com sucesso.");
    }

    public static void mensagensErros(String mensagem) {
        if (mensagem.equals(erros.USUARIO_LOGADO))
            System.out.println("Usuário já está logado em outra máquina.");
        else if (mensagem.equals(erros.CAMPO_VAZIO)) 
            System.out.println("Campo vazio, tente novamente.");
        else if (mensagem.equals(erros.USUARIO_SEM_REGISTRO)) 
            System.out.println("Usuario não encontrado. Faça registro no sistema. ");
        else if (mensagem.equals(erros.SENHA_ERRADA))
            System.out.println("Senha incorreta, tente novamente.");
        else if (mensagem.equals(erros.USUARIO_IGUAL)) 
            System.out.println("Esse nome de usuário já está em uso, escolha outro.");
        else if (mensagem.equals(erros.USUARIO_PASSAGEIRO))
            System.out.println("Não é possível criar uma viajem sem um veiculo registrado");
        else if (mensagem.equals(erros.ENTRADA_INVALIDA)) 
            System.out.println("Entrada inválida, tente novamente.");
        else if (mensagem.equals(erros.ID_INVALIDO))
            System.out.println("ID inválido, tente novamente.");
        else if (mensagem.equals(erros.CORRIDA_CHEIA))
            System.out.println("Corrida cheia, tente outra.");
        else if (mensagem.equals(erros.CORRIDA_NAO_ENCONTRADA))
            System.out.println("Corrida não encontrada, tente outra.");
        else if (mensagem.equals(erros.CORRIDA_FINALIZADA))
            System.out.println("Corrida já finalizada, tente outra.");
        else if (mensagem.equals(erros.CORRIDA_JA_RESERVADA))
            System.out.println("Você já reservou essa corrida.");
        else if (mensagem.equals(erros.CORRIDA_MOTORISTA))
            System.out.println("Você é o motorista dessa corrida.");
        else if (mensagem.equals(erros.CORRIDA_NAO_RESERVADA))
            System.out.println("Você não reservou essa corrida.");
        else if (mensagem.equals(erros.HISTORICO_VAZIO))
            System.out.println("Você não possui corridas no histórico.");     
        else if(mensagem.equals(erros.HISTORICO_NAO_SALVO)) 
            System.out.println("Reserva realizada com sucesso, mas persistência do histôrico falhou");
        else if (mensagem.equals(erros.MENSAGEM_INVALIDA))
            System.out.println("Mensagem recebida em formato inválido.");
        else if (mensagem.equals(erros.BUSCA_INVALIDA))
            System.out.println("Parâmetros da busca inválidos.");
        else if (mensagem.equals(erros.SERVIDOR_INDISPONIVEL))
            System.out.println("Servidor indisponível no momento.");
        else if (mensagem.equals(erros.TEMPO_ESGOTADO))
            System.out.println("Tempo limite da operação esgotado.");
        else if (mensagem.equals(erros.RESPOSTA_INVALIDA))
            System.out.println("Resposta recebida em formato inválido.");
        else if (mensagem.equals(erros.METODO_INVALIDO))
            System.out.println("Método de comunicação inválido.");
        else if (mensagem.equals(erros.IDS_OBRIGATORIOS))
            System.out.println("É necessário informar os IDs das corridas.");
        else if (mensagem.equals(erros.LIMITE_DE_IDS))
            System.out.println("Quantidade de IDs acima do limite permitido.");
        else if (mensagem.equals(erros.BUSCA_INTERROMPIDA))
            System.out.println("A busca foi interrompida.");
        else if (mensagem.equals(erros.RESERVA_REMOTA_INDISPONIVEL))
            System.out.println("Não foi possível acessar o servidor responsável pela reserva.");
        else if (mensagem.equals(erros.PASSAGEIRO_INVALIDO))
            System.out.println("Passageiro inválido.");
        else if (mensagem.equals(erros.FALHA_NA_RESERVA))
            System.out.println("Não foi possível realizar a reserva.");
        else if (mensagem.equals(erros.PERCURSO_INVALIDO))
            System.out.println("Percurso informado inválido.");
        else if (mensagem.equals(erros.FALHA_AO_SALVAR))
            System.out.println("Falha ao salvar os dados.");
        else if (mensagem.equals(erros.FALHA_AO_CARREGAR))
            System.out.println("Falha ao carregar os dados.");
        else if (mensagem.equals(erros.FALHA_INTERNA))
            System.out.println("Ocorreu um erro interno no servidor.");
        else if (mensagem.equals(erros.ETAPA_INVALIDA))
            System.out.println("Uma das etapas da viagem é inválida.");
        else if (mensagem.equals(erros.ETAPA_DUPLICADA))
            System.out.println("A viagem contém etapas duplicadas.");
        else if (mensagem.equals(erros.RESERVA_PARCIAL))
            System.out.println("Não foi possível concluir todas as etapas da reserva.");
        else if (mensagem.equals(erros.RESERVA_NAO_PREPARADA))
            System.out.println("A reserva não foi preparada anteriormente.");
        else if (mensagem.equals(erros.FALHA_AO_CONFIRMAR))
            System.out.println("Não foi possível confirmar a reserva.");
        else if (mensagem.equals(erros.FALHA_AO_ABORTAR))
            System.out.println("Não foi possível abortar a reserva.");
        else if (mensagem.equals(erros.HISTORICO_NAO_SALVO))
            System.out.println("Reserva realizada com sucesso, mas houve falha ao salvar o histórico.");
        else if (mensagem.equals(erros.TRANSACAO_PENDENTE))
            System.out.println("A transação ainda está pendente de confirmação.");
    }
}