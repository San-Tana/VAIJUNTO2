package vaijunto.src.Server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import vaijunto.src.model.erros;

public class Servidor {

    private static Consorcio consorcio;

    public static void main(String[] args) {
        String empresa = args.length > 0 ? args[0] : "EMP_A";
        
        int portaTCP = args.length > 1
                ? Integer.parseInt(args[1]) : 5000;
        int portaREST = args.length > 2
                ? Integer.parseInt(args[2]) : 8081;

        List<URI> parceiros = new ArrayList<>();

        if (args.length > 3 && !args[3].isBlank()) {
            for (String endereco : args[3].split(",")) {
                parceiros.add(URI.create(endereco));
            }
        }

        try {
            consorcio = new Consorcio(
                    empresa, portaREST, parceiros);
        } catch (IOException e) {
            System.out.println("Erro iniciando REST: "
                    + e.getMessage());
            return;
        }

        // Cria um conjunto de threads que poderão atender clientes simultaneamente.
        ExecutorService executor = Executors.newFixedThreadPool(10);
        

        try (ServerSocket servidor = new ServerSocket(porta)) {

            System.out.println("Servidor iniciado." +
                "\nEmpresa: " + empresa +
                "\nTCP: " + portaTCP +
                "\nREST: " + portaREST);

            // O servidor fica continuamente aceitando clientes.
            while (true) {

                // Fica esperando até algum cliente se conectar.
                Socket cliente = servidor.accept();

                System.out.println("Novo cliente conectado!");

                // Entrega o atendimento desse cliente para uma thread do ExecutorService.
                executor.submit(() -> atenderCliente(cliente));
            }

        } catch (IOException e) {

            System.out.println("Erro no servidor: " + e.getMessage());

        } finally {

            // Encerra o ExecutorService quando o servidor terminar.
            executor.shutdown();
        }
    }


    // Método responsável por conversar com um cliente específico.
    private static void atenderCliente(Socket cliente) {
        try (Socket socket = cliente;
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {
            
            String mensagem = entrada.readLine();
            if (mensagem == null) return;

            String operacao[] = mensagem.split("\\|", 3);
            if (operacao.length < 2) saida.println("ERRO|" + erros.MENSAGEM_INVALIDA);

            String comando = operacao[0];
            String usuario = operacao[1];

            switch (comando) {
                case "LOGIN":
                    saida.println(servicosServidor.login(usuario));
                    break;

                case "REGISTRO":
                    saida.println(servicosServidor.registro(usuario));
                    break;

                case "CONSULTAR":
                    saida.println(servicosServidor.existeUsuario(usuario));
                    break;

                case "DESLOGAR":
                    saida.println(servicosServidor.deslogar(usuario));
                    break;

                case "MOTORISTA":
                    saida.println(servicosServidor.usuarioMotorista(usuario));
                    break;

                case "HISTORICO":
                    saida.println(servicosServidor.historico(usuario));
                    break;

                case "BUSCAR":
                    if (operacao.length < 3) {
                        saida.println("ERRO|" + erros.BUSCA_INVALIDA);
                    } else {
                        saida.println(consorcio.buscar(operacao[2]));
                    }
                    break;

                case "PUBLICAR":
                    if (operacao.length < 3) {
                        saida.println("ERRO|" + erros.MENSAGEM_INVALIDA);
                        break;
                    }
                    saida.printlnservicosServidor.publicarCorrida(usuario, operacao[2]);

                    if (resposta.startsWith("OK")) {
                        consorcio.publicou();
                    }
                    break;

                case "RESERVAR":
                    if (operacao.length < 3) {
                        saida.println("ERRO|" + erros.MENSAGEM_INVALIDA);
                        break;
                    }

                    String id = operacao[2];

                    if (id.contains(":")) {
                        String[] partes = id.split(":", 2);

                        if (!partes[0].equals(consorcio.empresa())) {
                            saida.println("ERRO|" + erros.RESERVA_REMOTA_INDISPONIVEL);
                            break;
                        }

                        id = partes[1];
                    }

                    saida.println(servicosServidor.reservarCorrida(usuario, id));
                    break;

                case "CANCELAR":
                    if (operacao.length < 3) {
                        saida.println("ERRO|" + erros.MENSAGEM_INVALIDA);
                        break;
                    }

                    String respostaCancelamento =
                            servicosServidor.cancelarCorrida(
                                    usuario, operacao[2]);

                    saida.println(respostaCancelamento);

                    if (respostaCancelamento.startsWith("OK")) {
                        consorcio.publicou();
                    }
                    break;

                default:
                    saida.println("ERRO|" + erros.MENSAGEM_INVALIDA);
            }
            
        } catch (Exception e) {
            System.out.println("Erro ao atender cliente: ");
            e.printStackTrace();
        }
    }




}