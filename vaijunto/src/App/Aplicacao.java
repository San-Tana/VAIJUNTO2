package vaijunto.src.App;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.Arrays;
import java.util.List;

import vaijunto.src.model.erros;
import vaijunto.src.model.okays;

public class Aplicacao {
    private static String Usuario;
    private static boolean logado = false;

    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        int sair = 0;
        String opcao, resposta;

        while (sair != 1) {
            if (logado == false) {
                System.out.print("""
                ------------VAIJUNTO------------
                1. Fazer login
                2. Cadastrar novo usuário
                3. Sair
                Escolher opção(Digite o número da opção): """);
                opcao = scan.nextLine().trim();
            
                if (opcao.equals("1")) {
                    String login = servicosClient.logarCliente(scan);
                    requisicao ("LOGIN|" + login);
                }
                else if (opcao.equals("2")) {
                    String usuario = servicosClient.registroNome(scan);
                    resposta = requisicao("CONSULTAR|" + usuario); // Primeiro revisa o nome de usuário para evitar duplicação
                    if (resposta.equals("OK")) {
                        String registro = servicosClient.registrarCliente(scan, usuario);
                        if (registro.startsWith("ERRO")) 
                            servicosClient.mensagensErros(registro.substring(5));
                        else requisicao("REGISTRO|" + registro);
                    }
                }
                else if (opcao.equals("3")) { sair = 1; }
            }

            else {
                System.out.print("""
                ------------VAIJUNTO------------
                1. Procurar corrida
                2. Nova corrida
                3. Histórico
                4. Sair
                Escolher opção(Digite o número da opção): """);
                opcao = scan.nextLine().trim();

                if (opcao.equals("1")) {
                    String viajem = servicosClient.buscarCorrida(scan);
                    if (viajem.startsWith("ERRO")) {
                        servicosClient.mensagensErros(viajem.substring(5));
                        continue;
                    }
                    resposta = requisicao("BUSCAR|" + Usuario + "|" + viajem);
                    if (resposta.equals("OK")) {
                        System.out.print("\nDigite o ID da corrida que deseja se juntar: ");
                        opcao = scan.nextLine().trim();
                        resposta = requisicao("RESERVAR|" + Usuario + "|" + opcao);
                    }
                }
                else if (opcao.equals("2")) {
                    resposta = requisicao("MOTORISTA|" + Usuario);
                    if (resposta.startsWith("ERRO")) {
                        servicosClient.mensagensErros(resposta.substring(5));
                        continue;
                    }
                    String novaViajem = servicosClient.novaViajem(scan);
                    if (novaViajem.startsWith("ERRO")) {
                        servicosClient.mensagensErros(novaViajem.substring(5));
                        continue;
                    }
                    resposta = requisicao("PUBLICAR|" + Usuario + "|" + novaViajem);
                }
                else if (opcao.equals("3")) {
                    resposta = requisicao("HISTORICO|" + Usuario);
                    if (resposta.equals("OK")) {
                        System.out.print("\nDigite o ID da corrida se deseja cancelar ela: ");
                        opcao = scan.nextLine().trim();
                        if (!opcao.isEmpty()) resposta = requisicao("CANCELAR|" + Usuario + "|" + opcao);
                    }
                }
                else if (opcao.equals("4")) sair = 1;
            }
        }
        
        if (logado) requisicao("DESLOGAR|" + Usuario);
        scan.close();
    }

    private static String requisicao(String mensagem) {
        String endereco = "localhost";
        int porta = 5000;

        try (Socket socket = new Socket(endereco, porta)) {

            System.out.println("Conectado ao servidor!");

            // Saída: envia dados para o servidor
            PrintWriter saida = new PrintWriter(socket.getOutputStream(), true);

            // Entrada: recebe dados do servidor
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envia mensagem
            saida.println(mensagem);

            // Recebe resposta
            String linha = entrada.readLine();
            if (linha == null) {
                System.out.println("Servidor fechou a conexão sem enviar resposta.");
                return "ERRO";
            }
            String resposta[] = linha.split("\\|");
            if (resposta[0].equals("OK")) {
                if (resposta[1].equals(okays.USUARIO_LOGADO) || resposta[1].equals(okays.USUARIO_REGISTRADO)) {
                    Usuario = resposta[2];
                    logado = true;
                }
                else if (resposta[1].equals(okays.HISTORICO_ENCONTRADO) || resposta[1].equals(okays.CORRIDA_ENCONTRADA)) {
                    List<String> corridas = Arrays.asList(resposta[2].split(";"));
                    servicosClient.exibirHistorico(corridas);
                }
                servicosClient.mensagensOkays(resposta[1]);

                return "OK";
            }
            else if (resposta[0].equals("ERRO")) {
                servicosClient.mensagensErros(resposta[1]);
            }

            return "ERRO";

        } catch (IOException e) {
            System.out.println("Erro no cliente: " + e.getMessage());
            return "ERRO";
        }
    }
}