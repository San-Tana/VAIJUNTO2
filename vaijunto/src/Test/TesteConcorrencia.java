package vaijunto.src.Test;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.*;

public class TesteConcorrencia {

    private static final String HOST = "localhost";
    private static final int PORTA_A = 5000;

    private static class ClienteTeste {

        private final int porta;

        ClienteTeste(int porta) {
            this.porta = porta;
        }

        String enviar(String comando) throws IOException {

            try (
                Socket socket = new Socket(HOST, porta);

                BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
                );

                PrintWriter saida = new PrintWriter(
                    socket.getOutputStream(), true
                )
            ) {
                System.out.println(
                    "\n[" + Thread.currentThread().getName()
                    + "] > " + comando
                );

                saida.println(comando);

                String resposta = entrada.readLine();

                System.out.println(
                    "[" + Thread.currentThread().getName()
                    + "] < " + resposta
                );

                if (resposta == null) {
                    throw new IOException(
                        "Servidor encerrou a conexão sem responder."
                    );
                }

                return resposta;
            }
        }
    }

    private static void verificar(String resposta, String operacao) {
        if (resposta == null || !resposta.startsWith("OK|")) {
            throw new RuntimeException(
                "Falha em " + operacao + ": " + resposta
            );
        }
    }

    private static String obterIdCorrida(String respostaBusca) {

        String[] partes = respostaBusca.split("\\|", 3);

        if (partes.length < 3) {
            throw new RuntimeException(
                "Resposta da busca sem opções: " + respostaBusca
            );
        }

        String[] opcoes = partes[2].split("~");

        for (String opcao : opcoes) {

            String[] campos = opcao.split(";", -1);

            if (campos.length >= 8
                    && campos[0].equals("DIRETA")) {
                return campos[1];
            }
        }

        throw new RuntimeException(
            "Corrida direta não encontrada na busca."
        );
    }

    public static void main(String[] args) {

        String identificador = UUID.randomUUID()
            .toString().substring(0, 6);

        String motorista = "motorista" + identificador;
        String passageiroA = "passageiroA" + identificador;
        String passageiroB = "passageiroB" + identificador;

        ClienteTeste empresaA = new ClienteTeste(PORTA_A);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch prontidao = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);

        try {
            System.out.println("========== CADASTROS ==========");

            String dadosMotorista =
                motorista + ";senha123;12345678901&carro&Sedan&ABC1234";

            verificar(
                empresaA.enviar("REGISTRO|" + dadosMotorista),
                "cadastro do motorista"
            );

            verificar(
                empresaA.enviar("REGISTRO|" + passageiroA + ";senha123"),
                "cadastro do passageiro A"
            );

            verificar(
                empresaA.enviar("REGISTRO|" + passageiroB + ";senha123"),
                "cadastro do passageiro B"
            );

            String origem = "CidadeConcorrenciaA";
            String destino = "CidadeConcorrenciaB";

            System.out.println("\n========== PUBLICAÇÃO ==========");

            String dadosCorrida =
                origem + ";" + destino
                + ";3&12&2026&08:00;1;30;"
                + origem + "&" + destino;

            verificar(
                empresaA.enviar(
                    "PUBLICAR|" + motorista + "|" + dadosCorrida
                ),
                "publicação da corrida"
            );

            System.out.println("\n========== IDENTIFICAÇÃO DA CORRIDA ==========");

            String respostaBusca = empresaA.enviar(
                "BUSCAR|" + passageiroA + "|" + origem + ";" + destino
            );

            verificar(respostaBusca, "busca da corrida");

            String idCorrida = obterIdCorrida(respostaBusca);

            System.out.println("Corrida identificada: " + idCorrida);

            System.out.println("\n========== DISPUTA PELA ÚLTIMA VAGA ==========");

            Callable<String> reservaA = () -> {

                prontidao.countDown();
                largada.await();

                return new ClienteTeste(PORTA_A).enviar(
                    "RESERVAR|" + passageiroA + "|"
                    + idCorrida + ";" + origem + ";" + destino
                );
            };

            Callable<String> reservaB = () -> {

                prontidao.countDown();
                largada.await();

                return new ClienteTeste(PORTA_A).enviar(
                    "RESERVAR|" + passageiroB + "|"
                    + idCorrida + ";" + origem + ";" + destino
                );
            };

            Future<String> resultadoA = executor.submit(reservaA);
            Future<String> resultadoB = executor.submit(reservaB);

            // Aguarda as duas threads chegarem à linha de largada.
            if (!prontidao.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                    "As threads não ficaram prontas a tempo."
                );
            }

            // Libera as duas requisições praticamente juntas.
            largada.countDown();

            String respostaA = resultadoA.get(15, TimeUnit.SECONDS);
            String respostaB = resultadoB.get(15, TimeUnit.SECONDS);

            boolean sucessoA = respostaA.startsWith("OK|");
            boolean sucessoB = respostaB.startsWith("OK|");

            boolean cheiaA = respostaA.contains("CORRIDA CHEIA");
            boolean cheiaB = respostaB.contains("CORRIDA CHEIA");

            System.out.println("\n========== RESULTADO ==========");

            System.out.println("Passageiro A: " + respostaA);
            System.out.println("Passageiro B: " + respostaB);

            if (sucessoA && cheiaB) {
                System.out.println(
                    "\nTESTE APROVADO: passageiro A reservou a vaga."
                );
            }
            else if (sucessoB && cheiaA) {
                System.out.println(
                    "\nTESTE APROVADO: passageiro B reservou a vaga."
                );
            }
            else {
                throw new RuntimeException(
                    "Resultado inesperado: era esperada exatamente "
                    + "uma reserva confirmada e uma resposta de corrida cheia."
                );
            }

            System.out.println("\nCorrida: " + idCorrida);
            System.out.println("Motorista: " + motorista);

        } catch (Exception e) {

            System.out.println("\n========== TESTE FALHOU ==========");
            e.printStackTrace();

        } finally {

            largada.countDown();
            executor.shutdownNow();
        }
    }
}
