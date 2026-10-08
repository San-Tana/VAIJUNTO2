package vaijunto.src.model;

import java.util.List;
import java.util.ArrayList;

public class corrida {
    private boolean finalizada; // Canselada ou concluída. O motorista não pode alterar uma corrida finalizada.
    private final String ID; // ID da corrida para facilitar busca
    private final String motorista; // nome do motorista da corrida
    private String origem; // cidade de onde o motorista irá partir
    private String destino; // cidade destino, onde o motorista irá parar
    private data horario; // Data da viajem
    private int bancos; // número de assentos no veículo
    private int preco; // preço médio por trecho da corrida
    private List<String> paradas; // Guarda a ordem e o nome das cidades
    private List<String> caronas; // Guarda o ID e usuário do passageiro
    


    // Corrida com uma ou mais paradas
    public corrida(String ID, String motorista, String origem, String destino, data horario, int bancos, int preco, List<String> paradas) {
        this.finalizada = false;
        this.ID = ID;
        this.motorista = motorista;
        this.origem = origem;
        this.destino = destino;
        this.horario = horario;
        this.bancos = bancos;
        this.preco = preco;
        this.paradas = paradas;
        this.caronas = new ArrayList<String>();
    }

    // Corrida sem nenhuma parada
    public corrida(String ID, String motorista, String origem, String destino, data horario, int bancos, int preco) {
        this.finalizada = false;
        this.ID = ID;
        this.motorista = motorista;
        this.origem = origem;
        this.destino = destino;
        this.horario = horario;
        this.bancos = bancos;
        this.preco = preco;
        this.paradas = new ArrayList<String>();
        this.caronas = new ArrayList<String>();
    }

    public boolean getFinalizada() { return finalizada; }
    public String getID() { return ID; }
    public String getmotorista() { return motorista; }
    public String getOrigem() { return origem; }
    public String getDestino() {return destino; }
    public data getHorario() { return horario; }
    public int getBancos() { return bancos; }
    public int getPreco() { return preco; }
    public List<String> getParadas() { return paradas; }

    public void setPreco(int novo) { this.preco = novo; }
    public void finalizarCorrida() { this.finalizada = true; }

    public void addParada(int posicao, String parada) { paradas.add(posicao, parada); }
    public void addParada(String parada) { paradas.add(parada); }
    public void removeParada(String parada) { paradas.remove(parada); }

    // Ao invés de guardar todas as informações do cliente, guarda seu nome para busca
    public void addCarona(String nome) { 
        caronas.add(nome); 
        bancos--; // Diminui o número de assentos disponíveis
    }
    public void removeCarona(String nome) { 
        caronas.remove(nome); 
        bancos++; // Aumenta o número de assentos disponíveis
    }
    
    public List<String> getCarona() { return caronas; } // Busca o passageiro

    // Compara a data e horário da corrida com outra, para saber se a corrida é válida para uma corrida combinada
    public boolean compararHora(data horario) {
        if (this.horario.getAno() > horario.getAno()) return false;

        else if (this.horario.getAno() == horario.getAno()) {
            if (this.horario.getMes() > horario.getMes()) return false;
            else if (this.horario.getMes() == horario.getMes()) {

                if (this.horario.getDia() > horario.getDia()) return false;
                else if (this.horario.getDia() == horario.getDia()) {
                    String hora1[] = this.horario.getHora().split("\\:");
                    String hora2[] = horario.getHora().split("\\:");
                    if (Integer.parseInt(hora1[0]) > Integer.parseInt(hora2[0])) return false;
                    else if (Integer.parseInt(hora1[0]) == Integer.parseInt(hora2[0])) {
                        
                        if (Integer.parseInt(hora1[1]) >= Integer.parseInt(hora2[1])) return false;
                    }
                }
            }
        }
        return true;
    }
    
    @Override 
    public String toString() {
        String corrida = ID + ";" + motorista + ";" + origem + ";" + destino + ";" + bancos + ";" + preco;
        if (!paradas.isEmpty()) corrida += ";PARADAS;" + String.join("&", paradas);
        if (!caronas.isEmpty()) corrida += ";CARONAS;" + String.join("&", caronas);
        return corrida;
    }
}
