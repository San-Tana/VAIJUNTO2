package vaijunto.src.Test;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteConcorrencia {

    private static String host = "localhost";
    private static int portaA = 5000;
    private static int portaB = 5001;
    private static int portaC = 5002;
    
    private static final int NUM_PASSAGEIROS = 15;

    private static class ClienteTeste {
        private final int porta;
        ClienteTeste(int porta) { this.porta = porta; }

        String enviarSimples(String comando) throws IOException {
            try (Socket socket = new Socket(host, porta);
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {
                saida.println(comando);
                return entrada.readLine();
            }
        }
    }

    public static void main(String[] args) {
        // Captura portas e host pela linha de comando
        if (args.length >= 3) {
            portaA = Integer.parseInt(args[0]);
            portaB = Integer.parseInt(args[1]);
            portaC = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            host = args[3];
        }

        System.out.println("Iniciando teste em " + host + " nas portas: " + portaA + ", " + portaB + ", " + portaC);

        String id = UUID.randomUUID().toString().substring(0, 5);
        String motorista = "mot_" + id;
        String origem = "DiretaA";
        String destino = "DiretaB";

        ClienteTeste[] empresas = {
            new ClienteTeste(portaA), 
            new ClienteTeste(portaB), 
            new ClienteTeste(portaC)
        };

        try {
            System.out.println("========== 1. PREPARAÇÃO DO CENÁRIO (3 SERVIDORES) ==========");

            // Cadastra motorista e publica a corrida na EMP_A com APENAS 1 VAGA
            empresas[0].enviarSimples("REGISTRO|" + motorista + ";senha;11111111111&carro&Sedan&ABC1234");
            String corridaA = origem + ";" + destino + ";31&12&2027&12:00;1;50;" + origem + "&" + destino;
            empresas[0].enviarSimples("PUBLICAR|" + motorista + "|" + corridaA);

            // Cadastra passageiros distribuídos nos 3 servidores
            System.out.println("Cadastrando " + NUM_PASSAGEIROS + " passageiros (A, B e C)...");
            String[] passageiros = new String[NUM_PASSAGEIROS];
            for (int i = 0; i < NUM_PASSAGEIROS; i++) {
                passageiros[i] = "pass_" + id + "_" + i;
                empresas[i % 3].enviarSimples("REGISTRO|" + passageiros[i] + ";senha");
            }

            System.out.println("\nAguardando sincronização entre os 3 servidores (7 segundos)...");
            Thread.sleep(7000);

            System.out.println("\n========== 2. BUSCA DO ID GLOBAL ==========");
            String respostaBusca = empresas[0].enviarSimples("BUSCAR|" + passageiros[0] + "|" + origem + ";" + destino);
            
            if (respostaBusca == null || !respostaBusca.startsWith("OK|")) {
                throw new RuntimeException("Falha na busca. A corrida não propagou pela rede.");
            }

            String idCorridaGlobal = respostaBusca.split("\\|")[2].split("~")[0].split(";")[1];
            System.out.println("ID Global da corrida: " + idCorridaGlobal);

            System.out.println("\n========== 3. DISPARO CONCORRENTE TRIPLO ==========");
            
            CountDownLatch latchPreparacao = new CountDownLatch(NUM_PASSAGEIROS);
            CountDownLatch latchDisparo = new CountDownLatch(1);
            CountDownLatch latchTermino = new CountDownLatch(NUM_PASSAGEIROS);

            AtomicInteger sucesso = new AtomicInteger(0);
            AtomicInteger falha = new AtomicInteger(0);

            for (int i = 0; i < NUM_PASSAGEIROS; i++) {
                final int index = i;
                final int portaAlvo = (i % 3 == 0) ? portaA : ((i % 3 == 1) ? portaB : portaC);

                new Thread(() -> {
                    try (Socket socket = new Socket(host, portaAlvo);
                         BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                         PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

                        String comando = "RESERVAR|" + passageiros[index] + "|" + idCorridaGlobal + ";" + origem + ";" + destino;
                        latchPreparacao.countDown();
                        latchDisparo.await();
                        
                        saida.println(comando);
                        String resposta = entrada.readLine();

                        if (resposta != null && resposta.startsWith("OK|")) {
                            sucesso.incrementAndGet();
                        } else {
                            falha.incrementAndGet();
                        }
                    } catch (Exception e) {
                        falha.incrementAndGet();
                    } finally {
                        latchTermino.countDown();
                    }
                }).start();
            }

            latchPreparacao.await();
            System.out.println("Todos conectados nas 3 portas. Fogo!");
            latchDisparo.countDown();
            latchTermino.await();

            System.out.println("\n========== 4. VALIDAÇÃO ==========");
            System.out.println("Reservas confirmadas: " + sucesso.get() + " (Esperado: 1)");
            System.out.println("Reservas rejeitadas: " + falha.get() + " (Esperado: " + (NUM_PASSAGEIROS - 1) + ")");
            
            if (sucesso.get() == 1) System.out.println("[APROVADO] Mutex distribuído perfeito!");
            else System.err.println("[FALHOU] Ocorreu inconsistência.");

        } catch (Exception e) { e.printStackTrace(); }
    }
}
