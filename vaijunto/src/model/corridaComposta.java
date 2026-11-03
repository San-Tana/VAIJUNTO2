
package vaijunto.src.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class corridaComposta {

    private final String ID;
    private final String origem;
    private final String destino;

    // Percurso completo do passageiro
    private final List<String> paradas;

    // ID global da corrida -> vetor de trechos percorridos
    private final Map<String, int[]> corridas;

    // Motoristas envolvidos
    private final List<String> motoristas;

    // Preço total
    private final int preco;


    public corridaComposta(String ID, List<String> paradas, Map<String, int[]> corridas, 
                            List<String> motoristas, int preco) {

        if (ID == null || ID.isBlank()) {
            throw new IllegalArgumentException("ID inválido.");
        }

        if (paradas == null || paradas.size() < 2) {
            throw new IllegalArgumentException("Percurso inválido.");
        }

        if (corridas == null || corridas.size() < 2) {
            throw new IllegalArgumentException(
                "Uma corrida composta precisa de pelo menos duas corridas."
            );
        }

        if (preco < 0) {
            throw new IllegalArgumentException("Preço inválido.");
        }

        this.ID = ID;
        this.paradas = new ArrayList<>(paradas);
        this.origem = paradas.get(0);
        this.destino = paradas.get(paradas.size() - 1);
        this.preco = preco;

        this.motoristas = motoristas == null
                ? new ArrayList<>()
                : new ArrayList<>(motoristas);

        this.corridas = new LinkedHashMap<>();

        // Valida e copia os vetores
        for (Map.Entry<String, int[]> entrada : corridas.entrySet()) {

            String idCorrida = entrada.getKey();
            int[] vetor = entrada.getValue();

            if (idCorrida == null || idCorrida.isBlank()
                    || vetor == null) {
                throw new IllegalArgumentException(
                    "Componente de corrida inválido."
                );
            }

            if (vetor.length != paradas.size() - 1) {
                throw new IllegalArgumentException(
                    "Vetor incompatível com o percurso."
                );
            }

            boolean possuiTrecho = false;
            boolean terminouSequencia = false;

            for (int valor : vetor) {

                if (valor != 0 && valor != 1) {
                    throw new IllegalArgumentException(
                        "O vetor deve conter apenas 0 e 1."
                    );
                }

                if (valor == 1) {

                    if (terminouSequencia) {
                        throw new IllegalArgumentException(
                            "Os trechos de uma corrida devem ser consecutivos."
                        );
                    }

                    possuiTrecho = true;

                } else if (possuiTrecho) {

                    terminouSequencia = true;
                }
            }

            if (!possuiTrecho) {
                throw new IllegalArgumentException(
                    "Uma corrida precisa cobrir pelo menos um trecho."
                );
            }

            this.corridas.put(idCorrida, vetor.clone());
        }

        // Verifica se todos os trechos do itinerário estão cobertos
        for (int i = 0; i < paradas.size() - 1; i++) {

            int cobertura = 0;

            for (int[] vetor : this.corridas.values()) {
                cobertura += vetor[i];
            }

            if (cobertura != 1) {
                throw new IllegalArgumentException(
                    "Trecho sem cobertura ou coberto por mais de uma corrida."
                );
            }
        }
    }


    // Getters

    public String getID() {
        return ID;
    }

    public String getOrigem() {
        return origem;
    }

    public String getDestino() {
        return destino;
    }

    public List<String> getParadas() {
        return new ArrayList<>(paradas);
    }

    // Retorna uma cópia do mapa e dos vetores
    public Map<String, int[]> getCorridas() {

        Map<String, int[]> copia = new LinkedHashMap<>();

        for (Map.Entry<String, int[]> entrada : corridas.entrySet()) {
            copia.put(entrada.getKey(), entrada.getValue().clone());
        }

        return copia;
    }

    public List<String> getMotoristas() {
        return new ArrayList<>(motoristas);
    }

    public int getPreco() {
        return preco;
    }
}
