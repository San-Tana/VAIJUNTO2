package vaijunto.src.Server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Servidor {

    public static void main(String[] args) {
        // Porta de conexão do servidor
        int porta = 5000;

        // Cria um conjunto de threads que poderão atender clientes simultaneamente.
        ExecutorService executor = Executors.newFixedThreadPool(10);
        

        try (ServerSocket servidor = new ServerSocket(porta)) {

            System.out.println("Servidor iniciado.");
            System.out.println("Aguardando conexões na porta " + porta + "...");

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
            String operacao[] = mensagem.split("\\|");
            
            System.out.println("");
            if (operacao[0].equals("LOGIN")) saida.println(servicosServidor.login(operacao[1]));
            else if (operacao[0].equals("REGISTRO")) saida.println(servicosServidor.registro(operacao[1]));
            else if (operacao[0].equals("CONSULTAR")) saida.println(servicosServidor.existeUsuario(operacao[1]));
            else if (operacao[0].equals("DESLOGAR")) saida.println(servicosServidor.deslogar(operacao[1]));
            else if (operacao[0].equals("MOTORISTA")) saida.println(servicosServidor.usuarioMotorista(operacao[1]));
            else if (operacao[0].equals("RESERVAR")) saida.println(servicosServidor.reservarCorrida(operacao[1], operacao[2]));
            else if (operacao[0].equals("HISTORICO")) saida.println(servicosServidor.historico(operacao[1]));
            else if (operacao[0].equals("CANCELAR")) saida.println(servicosServidor.cancelarCorrida(operacao[1], operacao[2]));
            else if (operacao[0].equals("PUBLICAR")) saida.println(servicosServidor.publicarCorrida(operacao[1], operacao[2]));
            else if (operacao[0].equals("BUSCAR")) saida.println(servicosServidor.buscarCorridas(operacao[1], operacao[2]));
            
        } catch (Exception e) {
            System.out.println("Erro ao atender cliente: ");
            e.printStackTrace();
        }
    }




}