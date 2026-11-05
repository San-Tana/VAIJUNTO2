package vaijunto.src.Test;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TesteRecuperacaoFalha {

    private static final String HOST = "localhost";
    private static final int PORTA_A = 5000;
    private static final int PORTA_B = 5001;
    private static final String SEPARADOR_CP = System.getProperty("path.separator");

    private static class ClienteTeste {
        private final int porta;
        ClienteTeste(int porta) { this.porta = porta; }

        String enviarSimples(String comando) throws IOException {
            try (Socket socket = new Socket(HOST, porta);
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {
                saida.println(comando);
                return entrada.readLine();
            }
        }
    }

    // Método para iniciar o servidor como um processo real do Sistema Operacional
    private static Process iniciarServidor(String nome, int tcp, int rest, String parceiros) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "java", "-cp", "out" + SEPARADOR_CP + "lib/gson-2.15.0.jar",
            "vaijunto.src.Server.Servidor",
            nome, String.valueOf(tcp), String.valueOf(rest), parceiros
        );
        
        // Redireciona a saída para não travar o console do teste
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    // Método para limpar a pasta de dados e garantir um teste limpo
    private static void limparDados(File pasta) {
        if (pasta.exists() && pasta.isDirectory()) {
            for (File arquivo : pasta.listFiles()) {
                arquivo.delete();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("========== TESTE DE INJEÇÃO DE FALHA (CHAOS ENGINEERING) ==========");
        limparDados(new File("dados"));

        Process servidorA = null;
        Process servidorB = null;

        try {
            System.out.println("1. Iniciando instâncias independentes...");
            servidorA = iniciarServidor("EMP_A", PORTA_A, 8081, "http://localhost:8082");
            servidorB = iniciarServidor("EMP_B", PORTA_B, 8082, "http://localhost:8081");
            
            // Aguarda os servidores subirem
            Thread.sleep(3000);

            ClienteTeste empA = new ClienteTeste(PORTA_A);
            ClienteTeste empB = new ClienteTeste(PORTA_B);

            String id = UUID.randomUUID().toString().substring(0, 5);
            String origem = "CidadeA", inter = "CidadeB", destino = "CidadeC";
            String passageiro = "passageiro_" + id;

            System.out.println("2. Publicando Trechos com 1 VAGA...");
            empA.enviarSimples("REGISTRO|motA_" + id + ";senha;111&carro&X&AAA");
            empA.enviarSimples("PUBLICAR|motA_" + id + "|" + origem + ";" + inter + ";31&12&2027&10:00;1;10;" + origem + "&" + inter);

            empB.enviarSimples("REGISTRO|motB_" + id + ";senha;222&carro&Y&BBB");
            empB.enviarSimples("PUBLICAR|motB_" + id + "|" + inter + ";" + destino + ";31&12&2027&11:30;1;20;" + inter + "&" + destino);
            
            empA.enviarSimples("REGISTRO|" + passageiro + ";senha");

            System.out.println("Aguardando sincronização de rede (6s)...");
            Thread.sleep(6000);

            String respostaBusca = empA.enviarSimples("BUSCAR|" + passageiro + "|" + origem + ";" + destino);
            String idComposta = null;
            for (String opcao : respostaBusca.split("\\|")[2].split("~")) {
                if (opcao.startsWith("COMPOSTA;")) {
                    idComposta = opcao.split(";")[1];
                    break;
                }
            }

            System.out.println("3. Disparando Reserva e MATANDO Servidor B no meio do processo...");
            
            CountDownLatch latchDisparo = new CountDownLatch(1);
            AtomicReference<String> respostaReserva = new AtomicReference<>("");

            // Thread 1: Inicia a reserva
            Thread reservaThread = new Thread(() -> {
                try {
                    latchDisparo.await();
                    respostaReserva.set(empA.enviarSimples("RESERVAR_COMPOSTA|" + passageiro + "|" + idComposta));
                } catch (Exception e) { e.printStackTrace(); }
            });

            // Thread 2: O Carrasco (Mata o Servidor B)
            final Process procB = servidorB;
            Thread carrascoThread = new Thread(() -> {
                try {
                    latchDisparo.await();
                    // Espera exatos 150ms. Esse é o tempo estimado para o Coordenador (A) 
                    // processar a fase local e enviar o PREPARE REST para B.
                    Thread.sleep(150); 
                    procB.destroyForcibly();
                    System.out.println("   [X] Servidor B ASSASSINADO de forma abrupta!");
                } catch (Exception e) { e.printStackTrace(); }
            });

            reservaThread.start();
            carrascoThread.start();
            latchDisparo.countDown();

            reservaThread.join();
            carrascoThread.join();

            System.out.println("Resposta da transação após a queda: " + respostaReserva.get());

            System.out.println("\n4. Reiniciando Servidor B (Recuperação de Desastre)...");
            servidorB = iniciarServidor("EMP_B", PORTA_B, 8082, "http://localhost:8081");
            Thread.sleep(6000); // Aguarda boot e sync

            System.out.println("\n5. VALIDAÇÃO DE CONSISTÊNCIA DE ASSENTOS");
            String validacao = empA.enviarSimples("BUSCAR|" + passageiro + "|" + origem + ";" + destino);
            
            if (validacao.startsWith("OK|")) {
                System.out.println("[APROVADO] O assento não ficou travado! O sistema abortou corretamente a transação inacabada.");
            } else {
                System.err.println("[FALHOU CRÍTICO] A busca retornou erro ou não achou a rota: " + validacao);
                System.err.println("Isso significa que o Servidor B guardou o assento como 'PREPARADO' no JSON, mas como ele morreu antes do 'COMMIT' ou 'ABORT', o assento ficou travado para sempre na volta!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Garante que os processos filhos serão mortos ao final do teste
            if (servidorA != null) servidorA.destroyForcibly();
            if (servidorB != null) servidorB.destroyForcibly();
        }
    }
}