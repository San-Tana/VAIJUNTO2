package vaijunto.src.Test;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteConcorrenciaComposta {

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
        if (args.length >= 3) {
            portaA = Integer.parseInt(args[0]);
            portaB = Integer.parseInt(args[1]);
            portaC = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            host = args[3];
        }

        System.out.println("Iniciando teste 2PC em " + host + " (Portas: " + portaA + ", " + portaB + ", " + portaC + ")");

        String id = UUID.randomUUID().toString().substring(0, 5);
        String origem = "CidadeA", intermediaria = "CidadeB", destino = "CidadeC";

        ClienteTeste empA = new ClienteTeste(portaA);
        ClienteTeste empB = new ClienteTeste(portaB);
        ClienteTeste empC = new ClienteTeste(portaC);

        try {
            System.out.println("========== 1. PREPARAÇÃO (COORDENAÇÃO DISTRIBUÍDA) ==========");
            
            // Trecho 1 (EMP_A)
            empA.enviarSimples("REGISTRO|motA_" + id + ";senha;111&carro&C1&AAA");
            empA.enviarSimples("PUBLICAR|motA_" + id + "|" + origem + ";" + intermediaria + ";31&12&2027&10:00;1;10;" + origem + "&" + intermediaria);

            // Trecho 2 (EMP_B) - Partindo +1h depois
            empB.enviarSimples("REGISTRO|motB_" + id + ";senha;222&carro&C2&BBB");
            empB.enviarSimples("PUBLICAR|motB_" + id + "|" + intermediaria + ";" + destino + ";31&12&2027&11:30;1;20;" + intermediaria + "&" + destino);

            System.out.println("Trechos publicados na EMP_A e EMP_B com 1 VAGA. EMP_C atuará como coordenadora remota pura.");

            String[] passageiros = new String[NUM_PASSAGEIROS];
            for (int i = 0; i < NUM_PASSAGEIROS; i++) {
                passageiros[i] = "passC_" + id + "_" + i;
                ClienteTeste empAlvo = (i % 3 == 0) ? empA : ((i % 3 == 1) ? empB : empC);
                empAlvo.enviarSimples("REGISTRO|" + passageiros[i] + ";senha");
            }

            System.out.println("\nAguardando sincronização inicial da rede (7 segundos)...");
            Thread.sleep(7000);

            System.out.println("\n========== 2. BUSCAS INDIVIDUAIS (COM TENTATIVAS) ==========");
            String[] idsCompostas = new String[NUM_PASSAGEIROS];
            for (int i = 0; i < NUM_PASSAGEIROS; i++) {
                ClienteTeste empAlvo = (i % 3 == 0) ? empA : ((i % 3 == 1) ? empB : empC);
                String resposta = null;
                
                // Loop de Retry para garantir que a rede propagou o índice
                for (int tentativa = 1; tentativa <= 5; tentativa++) {
                    resposta = empAlvo.enviarSimples("BUSCAR|" + passageiros[i] + "|" + origem + ";" + destino);
                    if (resposta != null && resposta.startsWith("OK|")) {
                        break;
                    }
                    System.out.println("    [Aviso] Rota ainda não propagada para o passageiro " + i + ". Tentativa " + tentativa + "/5. Retentando em 2s...");
                    Thread.sleep(2000);
                }
                
                if (resposta == null || !resposta.startsWith("OK|")) {
                    throw new RuntimeException("Falha na busca após retentativas. Passageiro " + i + ". Resposta final: " + resposta);
                }

                for (String opcao : resposta.split("\\|")[2].split("~")) {
                    if (opcao.startsWith("COMPOSTA;")) {
                        idsCompostas[i] = opcao.split(";")[1];
                        break;
                    }
                }
                
                if (idsCompostas[i] == null) {
                    throw new RuntimeException("ID Composto não retornado na busca para Passageiro " + i);
                }
            }
            System.out.println("Todas as 15 buscas concluídas com sucesso!");

            System.out.println("\n========== 3. DISPARO CONCORRENTE SIMULTÂNEO ==========");
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

                        latchPreparacao.countDown();
                        latchDisparo.await(); 
                        
                        saida.println("RESERVAR_COMPOSTA|" + passageiros[index] + "|" + idsCompostas[index]);
                        String resposta = entrada.readLine();

                        if (resposta != null && resposta.startsWith("OK|")) sucesso.incrementAndGet();
                        else falha.incrementAndGet();
                    } catch (Exception e) { 
                        falha.incrementAndGet();
                    } finally { 
                        latchTermino.countDown(); 
                    }
                }).start();
            }

            latchPreparacao.await();
            System.out.println("Disparando 2PC para itinerários distribuídos!");
            latchDisparo.countDown();
            latchTermino.await();

            System.out.println("\n========== 4. VALIDAÇÃO ==========");
            System.out.println("Reservas confirmadas: " + sucesso.get());
            System.out.println("Reservas abortadas/rejeitadas: " + falha.get());

            if (sucesso.get() == 1) {
                System.out.println("\n[APROVADO] O controle 2PC funcionou! O Consórcio garantiu a transação.");
            } else {
                System.err.println("\n[FALHOU] Ocorreu inconsistência ou deadlock.");
            }

        } catch (Exception e) { e.printStackTrace(); }
    }
}