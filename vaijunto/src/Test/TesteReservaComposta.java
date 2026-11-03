
package vaijunto.src.Test;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

public class TesteReservaComposta {

    private static final String HOST = "localhost";
    private static final int PORTA_A = 5000;
    private static final int PORTA_B = 5001;

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
                System.out.println("\n> " + comando);

                saida.println(comando);

                String resposta = entrada.readLine();
                System.out.println("< " + resposta);

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

    public static void main(String[] args) {

        String identificador = UUID.randomUUID()
            .toString().substring(0, 6);

        String motoristaA = "motoristaA" + identificador;
        String motoristaB = "motoristaB" + identificador;
        String passageiro = "passageiro" + identificador;

        ClienteTeste empresaA = new ClienteTeste(PORTA_A);
        ClienteTeste empresaB = new ClienteTeste(PORTA_B);
        try {
            System.out.println("========== CADASTROS ==========");

            String dadosMotoristaA =
            motoristaA + ";senha123;12345678901&carro&Sedan&ABC1234";

            verificar(
                empresaA.enviar("REGISTRO|" + dadosMotoristaA),
                "cadastro do motorista A"
            );

            // Motorista da EMP_B
            String dadosMotoristaB =
            motoristaB + ";senha123;12345678902&carro&SUV&DEF5678";

            verificar(
                empresaB.enviar("REGISTRO|" + dadosMotoristaB),
                "cadastro do motorista B"
            );

            // Passageiro na EMP_A
            String dadosPassageiro = passageiro + ";senha123";

            verificar(
                empresaA.enviar("REGISTRO|" + dadosPassageiro),
                "cadastro do passageiro"
            );

            String origem = "CidadeTesteA";
            String intermediaria = "CidadeTesteB";
            String destino = "CidadeTesteC";

            System.out.println("\n========== PUBLICAÇÕES ==========");

            String corridaA = origem + ";" + intermediaria
                + ";3&12&2026&08:00;3;30;"
                + origem + "&" + intermediaria;

            verificar(
                empresaA.enviar("PUBLICAR|" + motoristaA + "|" + corridaA),
                "publicação na EMP_A"
            );

            String corridaB = intermediaria + ";" + destino
                + ";3&12&2026&09:00;3;40;"
                + intermediaria + "&" + destino;

            verificar(
                empresaB.enviar("PUBLICAR|" + motoristaB + "|" + corridaB),
                "publicação na EMP_B"
            );

            Thread.sleep(7000);

            System.out.println("\n========== BUSCA ==========");

            String respostaBusca = empresaA.enviar("BUSCAR|" + passageiro + "|" + origem + ";" + destino);

            verificar(respostaBusca, "busca");

            String[] partesResposta =
                respostaBusca.split("\\|", 3);

            if (partesResposta.length < 3) {
                throw new RuntimeException(
                    "Resposta da busca sem opções."
                );
            }

            String[] opcoes = partesResposta[2].split("~");

            String idComposta = null;

            for (String opcao : opcoes) {
                String[] campos = opcao.split(";", -1);

                if (campos.length >= 6
                        && campos[0].equals("COMPOSTA")) {
                    idComposta = campos[1];
                    break;
                }
            }

            if (idComposta == null) {
                throw new RuntimeException(
                    "Nenhuma viagem composta foi encontrada."
                );
            }

            System.out.println(
                "\nViagem composta encontrada: " + idComposta
            );

            System.out.println("\n========== RESERVA ==========");

            String respostaReserva = empresaA.enviar(
                "RESERVAR_COMPOSTA|"
                + passageiro + "|"
                + idComposta
            );

            verificar(respostaReserva, "reserva composta");

            System.out.println("\n========== TESTE CONCLUÍDO ==========");
            System.out.println("Reserva composta realizada com sucesso!");

            System.out.println("\nPassageiro: " + passageiro);
            System.out.println("Viagem: " + idComposta);

        } catch (Exception e) {
            System.out.println("\n========== TESTE FALHOU ==========");
            e.printStackTrace();
        }
    }
}
