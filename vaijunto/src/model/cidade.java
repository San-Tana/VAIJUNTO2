package vaijunto.src.model;

import java.util.Map;
import java.util.HashMap;

public class cidade {
    private String nome;
    private Map<String, cidade> proximas;
    private Map<String, String> corridas;

    public cidade (String nome) {
        this.nome = nome;
        this.proximas = new HashMap<String, cidade>();
    }

    public getNome() { return nome; }
    
    public addVizinha(cidade vizinha) { proximas.put(vizinha.nome, vizinha); }
    public removeVizinha(String vizinha) { proximas.remove(vizinha); }

    // Cidade guarda o ID das corridas disponíveis nela e se é origem, destino ou parada da corrida
    public addCorrida(String nova, String trecho) { corridas.put(nova, trecho); }
    public removeCorrida(String cancelada) { corridas.remove(cancelada); }


}