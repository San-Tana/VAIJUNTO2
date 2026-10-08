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
            String paradas = adicionarParadas(scan);
            if (paradas.startsWith("ERRO")) return paradas;
            return origem + ";" + destino + ";" + horario + ";" + assentos + ";" + preco + ";" + paradas;
        }
        else if (!parada.matches("(?i)^(nao|não|n)$"))
            return "ERRO|" + erros.ENTRADA_INVALIDA;

        if (Stream.of(origem, destino, assentos).anyMatch(campo -> campo == null || campo.trim().isEmpty()))
            return "ERRO|" + erros.CAMPO_VAZIO;

        return origem + ";" + destino + ";" + horario + ";" + assentos + ";" + preco;
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
        else if (!Stream.of(dia, mes, ano, horario).allMatch(campo -> campo.matches("[0-9]+")))
            return "ERRO|" + erros.ENTRADA_INVALIDA;

        return dia + "&" + mes + "&" + ano + "&" + horario;
    }

    public static String adicionarParadas(Scanner scan) {
        int sair = 0;
        String paradas = "";
        while (sair != 1) {
            System.out.print("\nInforme o nome da cidade: ");
            paradas += scan.nextLine().trim() + "&";

            System.out.print("\nDeseja adicionar outra parada? [S/N]: ");
            String resposta = scan.nextLine().trim();
            if (resposta.matches("(?i)^(nao|não|n)$")) 
                sair = 1;
            else if (resposta.matches("(?i)^(sim|s)$"))
                continue;
            else 
                return "ERRO|" + erros.ENTRADA_INVALIDA;
        }

        return paradas;
    }

    // exibir todas as corridas em que o usuário está registrado/exibir corridas que o usuário pode participar
    public static void exibirHistorico(List<String> historico) {
        for (String corrida : historico) System.out.println(corrida.replace("\\n", "\n"));
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
    }
}