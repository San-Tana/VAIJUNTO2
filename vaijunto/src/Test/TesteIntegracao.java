
package vaijunto.src.Test;

import java.io.*;
import java.net.*;

public class TesteIntegracao {

    private static final String HOST = "localhost";
    private static final int PORTA_A = 5000;
    private static final int PORTA_B = 5001;

    // Dados exclusivos para o teste
    private static final String MOTORISTA = "motorista_teste";
    private static final String SENHA_MOTORISTA = "123456";

    private static final String PASSAGEIRO = "passageiro_teste";
    private static final String SENHA_PASSAGEIRO = "123456";

    private static final String ID_CORRIDA = "EMP_A:CAR001";

    private static final String ORIGEM = "Salvador";
    private static final String DESTINO = "Feira";

    private static int testesAprovados = 0;
    private static int testesFalhos = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("================================");
        System.out.println(" TESTE DE INTEGRAÇÃO VAIJUNTO");
        System.out.println("================================");

        // 1. Registra motorista na EMP_A
        testar(
            "Cadastro do motorista na EMP_A",
            enviar(PORTA_A,
                "REGISTRO|" + MOTORISTA + ";" + SENHA_MOTORISTA
                + ";12345678900&carro&Onix&ABC1234")
                .startsWith("OK|")
        );

        // 2. Registra passageiro na EMP_B
        testar(
            "Cadastro do passageiro na EMP_B",
            enviar(PORTA_B,
                "REGISTRO|" + PASSAGEIRO + ";" + SENHA_PASSAGEIRO)
                .startsWith("OK|")
        );

        // 3. Publica corrida na EMP_A
        String dadosCorrida =
            ORIGEM + ";" +
            DESTINO + ";" +
            "31&12&2027&12:00" + ";" +
            "3" + ";" +
            "50" + ";" +
            ORIGEM + "&" + DESTINO;

        testar(
            "Publicação da corrida na EMP_A",
            enviar(PORTA_A,
                "PUBLICAR|" + MOTORISTA + "|" + dadosCorrida)
                .startsWith("OK|")
        );

        // 4. Aguarda sincronização do índice entre os servidores
        System.out.println("\nAguardando sincronização...");
        Thread.sleep(6000);

        // 5. Reserva a corrida da EMP_A através da EMP_B
        String dadosReserva =
            ID_CORRIDA + ";" + ORIGEM + ";" + DESTINO;

        String respostaReserva = enviar(
            PORTA_B,
            "RESERVAR|" + PASSAGEIRO + "|" + dadosReserva
        );

        System.out.println("Resposta da reserva: " + respostaReserva);

        testar(
            "Reserva remota pela EMP_B",
            respostaReserva != null
                && respostaReserva.startsWith("OK|")
        );

        // 6. Tenta reservar novamente a mesma corrida
        String segundaReserva = enviar(
            PORTA_B,
            "RESERVAR|" + PASSAGEIRO + "|" + dadosReserva
        );

        System.out.println("Segunda tentativa: " + segundaReserva);

        testar(
            "Bloqueio de reserva duplicada",
            segundaReserva != null
                && segundaReserva.startsWith("ERRO|")
        );

        // Resultado final
        System.out.println("\n================================");
        System.out.println(" RESULTADO DOS TESTES");
        System.out.println("================================");
        System.out.println("Aprovados: " + testesAprovados);
        System.out.println("Falhos: " + testesFalhos);
    }

    private static String enviar(int porta, String mensagem) {

        try (
            Socket socket = new Socket(HOST, porta);
            PrintWriter saida = new PrintWriter(
                socket.getOutputStream(), true
            );
            BufferedReader entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            )
        ) {
            saida.println(mensagem);
            return entrada.readLine();

        } catch (IOException e) {
            System.out.println(
                "Falha na comunicação com a porta "
                + porta + ": " + e.getMessage()
            );
            return null;
        }
    }

    private static void testar(String nome, boolean sucesso) {

        if (sucesso) {
            System.out.println("[APROVADO] " + nome);
            testesAprovados++;
        } else {
            System.out.println("[FALHOU] " + nome);
            testesFalhos++;
        }
    }
}
