package vaijunto.src.model;

import java.util.Map;
import java.util.HashMap;

public class cidade {
    private String nome;
    private Map<String, cidade> proximas; // Cidades conectadas por trechos de corridas.
    private Map<String, ReferenciaCorrida> corridas; // ID da corrida -> informações sobre a corrida nesta cidade.
    
    // Guarda a empresa proprietária e a posição desta cidade na rota.
    public record ReferenciaCorrida(String empresa, int posicao) {}

    public cidade (String nome) {
        this.nome = nome;
        this.proximas = new HashMap<>();
        this.corridas = new HashMap<>();
    }

    public String getNome() { return nome; }

    public Map<String, cidade> getProximas() { return proximas; }
    
    public Map<String, ReferenciaCorrida> getCorridas() { return corridas; }

    public void addVizinha(cidade vizinha) {
        proximas.put(vizinha.getNome(), vizinha);
    }

    public void removeVizinha(String nomeVizinha) {
        proximas.remove(nomeVizinha);
    }

    public void addCorrida(String id, String empresa, int posicao) {
        corridas.put(id, new ReferenciaCorrida(empresa, posicao));
    }

    public void removeCorrida(String id) {
        corridas.remove(id);
    }
}
