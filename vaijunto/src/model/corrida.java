package vaijunto.src.model;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;

public class corrida {
    private boolean finalizada; // Canselada ou concluída.
    private final String ID; // ID da corrida para facilitar busca
    private final String motorista; // nome do motorista da corrida
    private String origem; // cidade de onde o motorista irá partir
    private String destino; // cidade destino, onde o motorista irá parar
    private data horario; // Data da viajem
    private int bancos; // número de assentos no veículo
    private int preco; // preço médio por trecho da corrida
    private List<String> paradas; // Guarda a ordem e o nome das cidades
    private List<Integer> vagasPorTrecho; // Guarda a quantidade de vagas por trecho
    private Map<String, int[]> caronas; // Guarda o ID e usuário do passageiro
    
    // Reservas que foram preparadas, mas ainda não confirmadas.
    private Map<String, ReservaPendente> reservasPendentes = new HashMap<>();

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
        this.vagasPorTrecho = new ArrayList<>(Collections.nCopies(paradas.size() - 1, bancos));

        this.caronas = new HashMap<>();

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
    public List<Integer> getTrechos() { return vagasPorTrecho; }
    public Map<String, int[]> getCarona() { return caronas; } // Busca o passageiro

    
    public void finalizarCorrida() { this.finalizada = true; }
    
    // Verifica se tem vagas o suficiente para reservar uma corrida
    public boolean temVagas(String origem, String destino) {

        // Gera umo vetor que representa os trechos desejados.
        int[] reserva = vetorReserva(origem, destino);

        // Elementos de reserva são sempre 0 ou 1. Se a quantidade de reservas for menor que 1, não tem vagas disponíveis
        for (int i = 0; i < vagasPorTrecho.size(); i++) {
            if (vagasPorTrecho.get(i) < reserva[i])
                return false;
        }

        return true;
    }

    synchronized public boolean addCarona(String idGlobal, String origem, String destino) {

        // Impede que o mesmo passageiro reserve duas vezes ou reserve uma corrida finalizada
        if (caronas.containsKey(idGlobal) || finalizada) return false;

        int[] reserva;

        try {
            reserva = vetorReserva(origem, destino);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Confere todos os trechos antes de alterar qualquer vaga.
        for (int i = 0; i < reserva.length; i++) {
            if (reserva[i] == 1 && vagasPorTrecho.get(i) <= 0) {
                return false;
            }
        }

        // Efetiva a reserva.
        caronas.put(idGlobal, reserva);

        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(i, vagasPorTrecho.get(i) - reserva[i]);
        }

        return true;
    }

    synchronized public boolean removeCarona(String idGlobal) {
        int[] reserva = caronas.remove(idGlobal);

        if (reserva == null) return false;

        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(i, vagasPorTrecho.get(i) + reserva[i]);
        }

        return true;
    }

    // Em caso do salvamento falhar e restauração da reserva ser necessária para evitar discrepancias
    synchronized public boolean restaurarCarona(
            String idGlobal, int[] reserva) {

        if (idGlobal == null || reserva == null) {
            return false;
        }

        if (caronas.containsKey(idGlobal)
                || reserva.length != vagasPorTrecho.size()) {
            return false;
        }

        // Valida o vetor antes de alterar qualquer dado.
        for (int valor : reserva) {
            if (valor != 0 && valor != 1) {
                return false;
            }
        }

        // Restaura a reserva e ocupa novamente as vagas.
        for (int i = 0; i < reserva.length; i++) {
            if (reserva[i] == 1 && vagasPorTrecho.get(i) <= 0) {
                return false;
            }
        }

        caronas.put(idGlobal, reserva.clone());

        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(
                    i, vagasPorTrecho.get(i) - reserva[i]
            );
        }

        return true;
    }

    // Cria um vetor binário que representa quais rotas o cliente está ocupando
    public int[] vetorReserva(String origem, String destino) {
        int inicio = paradas.indexOf(origem);
        int fim = paradas.indexOf(destino);

        if (inicio < 0 || fim <= inicio) {
            throw new IllegalArgumentException("Trecho inválido");
        }

        int[] vetor = new int[paradas.size() - 1];

        for (int i = inicio; i < fim; i++) {
            vetor[i] = 1;
        }

        return vetor;
    }

    // Compara a data e horário da corrida com outra, para saber se a corrida é válida para uma corrida combinada
    public boolean compararHora(data horario) {
        LocalDateTime minhaPartida = this.horario.paraLocalDateTime();
        LocalDateTime outraPartida = horario.paraLocalDateTime();

        return !minhaPartida.isBefore(outraPartida.plusHours(1));
    }
    
    @Override 
    public String toString() {
        String corrida = ID + ";" + motorista + ";" + origem + ";" + destino + ";" + 
                        horario.toString() + ";" + bancos + ";" + preco;

        if (!paradas.isEmpty()) corrida += ";PARADAS;" + String.join("&", paradas);
        if (!caronas.isEmpty()) corrida += ";CARONAS;" + String.join("&", caronas.keySet());

        return corrida;
    }

    /*
    Esse trecho trata da coordenação de reservas em multiplos servidores, usando métodos para
    Preparar uma reserva, confirmar ela ou cancelar, evitando reservas parciais
    */

    public synchronized String obterPassageiroPendente(String idTransacao) {
        garantirReservasPendentes();

        ReservaPendente pendente = reservasPendentes.get(idTransacao);

        if (pendente == null || !pendente.estado.equals("PREPARADA")) {
            return null;
        }

        return pendente.idPassageiro;
    }

    public synchronized int[] obterVetorPendente(String idTransacao) {
        garantirReservasPendentes();

        ReservaPendente pendente = reservasPendentes.get(idTransacao);

        if (pendente == null || !pendente.estado.equals("PREPARADA")) {
            return null;
        }

        return pendente.vetor.clone();
    }

    public synchronized String obterEstadoTransacao(String idTransacao) {
        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        return transacao == null ? null : transacao.estado;
    }

    public synchronized boolean correspondeTransacao(String idTransacao, String idPassageiro, String origem, String destino) {

        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        return transacao != null
                && transacao.idPassageiro.equals(idPassageiro)
                && transacao.origem.equals(origem)
                && transacao.destino.equals(destino);
    }

    synchronized public String obterTransacaoConfirmada(String idPassageiro) {
        garantirReservasPendentes();

        String idEncontrado = null;

        for (Map.Entry<String, ReservaPendente> entrada
                : reservasPendentes.entrySet()) {

            ReservaPendente transacao = entrada.getValue();

            if (transacao.estado.equals("CONFIRMADA")
                    && transacao.idPassageiro.equals(idPassageiro)) {

                // Mais de uma transação confirmada para o mesmo passageiro
                // indica uma inconsistência.
                if (idEncontrado != null) {
                    return "";
                }

                idEncontrado = entrada.getKey();
            }
        }

        return idEncontrado;
    }

    private void garantirReservasPendentes() {
        if (reservasPendentes == null) {
            reservasPendentes = new HashMap<>();
        }
    }

    // Prepara uma reserva que precisa da coordenação de vários servidores
    synchronized public boolean prepararCarona(String idTransacao, String idPassageiro, String origem, String destino) {

        garantirReservasPendentes();

        if (finalizada
                || idTransacao == null
                || idPassageiro == null
                || origem == null
                || destino == null) {
            return false;
        }

        ReservaPendente existente = reservasPendentes.get(idTransacao);

        // Uma repetição do mesmo PREPARE não bloqueia vagas novamente.
        if (existente != null) {
            return existente.estado.equals("PREPARADA")
                    && existente.idPassageiro.equals(idPassageiro)
                    && existente.origem.equals(origem)
                    && existente.destino.equals(destino);
        }

        if (caronas.containsKey(idPassageiro)) {
            return false;
        }

        // O passageiro não pode ter outra transação pendente nessa corrida.
        for (ReservaPendente pendente : reservasPendentes.values()) {
            if (pendente.estado.equals("PREPARADA")
                    && pendente.idPassageiro.equals(idPassageiro)) {
                return false;
            }
        }

        int[] reserva;

        try {
            reserva = vetorReserva(origem, destino);
        } catch (IllegalArgumentException e) {
            return false;
        }

        for (int i = 0; i < reserva.length; i++) {
            if (reserva[i] == 1 && vagasPorTrecho.get(i) <= 0) {
                return false;
            }
        }

        // Bloqueia as vagas.
        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(
                    i, vagasPorTrecho.get(i) - reserva[i]
            );
        }

        reservasPendentes.put(
                idTransacao,
                new ReservaPendente(
                        idPassageiro, origem, destino, reserva
                )
        );

        return true;
    }

    // Confirma a reserva após todos os servidores estarem prontos
    synchronized public boolean confirmarCarona(String idTransacao) {
        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        if (transacao == null) {
            return false;
        }

        // COMMIT repetido: já está confirmado.
        if (transacao.estado.equals("CONFIRMADA")) {
            return true;
        }

        if (!transacao.estado.equals("PREPARADA")) {
            return false;
        }

        if (caronas.containsKey(transacao.idPassageiro)) {
            return false;
        }

        // As vagas já foram descontadas no PREPARE.
        caronas.put(
                transacao.idPassageiro,
                transacao.vetor.clone()
        );

        transacao.estado = "CONFIRMADA";

        return true;
    }

    // Cancela a reserva caso algo dê errado no preparo
    synchronized public boolean abortarCarona(String idTransacao) {
        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        if (transacao == null) {
            return false;
        }

        // ABORT repetido: já está abortada.
        if (transacao.estado.equals("ABORTADA")) {
            return true;
        }

        // Uma reserva confirmada não pode ser desfeita por ABORT.
        if (!transacao.estado.equals("PREPARADA")) {
            return false;
        }

        for (int i = 0; i < transacao.vetor.length; i++) {
            vagasPorTrecho.set(
                    i,
                    vagasPorTrecho.get(i) + transacao.vetor[i]
            );
        }

        transacao.estado = "ABORTADA";

        return true;
    }

    synchronized public boolean reverterConfirmacao(String idTransacao, String idPassageiro) {

        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        if (transacao == null
                || !transacao.estado.equals("CONFIRMADA")
                || !transacao.idPassageiro.equals(idPassageiro)) {
            return false;
        }

        int[] reserva = caronas.remove(idPassageiro);

        if (reserva == null) {
            return false;
        }

        transacao.estado = "PREPARADA";

        return true;
    }

    // Restaura uma reserva pendente que foi abortada em memória.
    synchronized public boolean restaurarPendencia(String idTransacao, String idPassageiro, int[] reserva) {

        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        if (transacao == null || idPassageiro == null || reserva == null
                || !transacao.estado.equals("ABORTADA")
                || !transacao.idPassageiro.equals(idPassageiro)
                || reserva.length != vagasPorTrecho.size()) {
            return false;
        }

        for (int i = 0; i < reserva.length; i++) {
            if ((reserva[i] != 0 && reserva[i] != 1)
                    || (reserva[i] == 1 && vagasPorTrecho.get(i) <= 0)) {
                return false;
            }
        }

        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(
                    i, vagasPorTrecho.get(i) - reserva[i]
            );
        }

        transacao.estado = "PREPARADA";

        return true;
    }

    synchronized public boolean restaurarCancelamento(
        String idTransacao,
        String idPassageiro) {

        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        if (transacao == null
                || !transacao.estado.equals("CANCELADA")
                || !transacao.idPassageiro.equals(idPassageiro)
                || caronas.containsKey(idPassageiro)) {
            return false;
        }

        int[] reserva = transacao.vetor;

        for (int i = 0; i < reserva.length; i++) {
            if (reserva[i] == 1 && vagasPorTrecho.get(i) <= 0) {
                return false;
            }
        }

        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(
                    i, vagasPorTrecho.get(i) - reserva[i]
            );
        }

        caronas.put(idPassageiro, reserva.clone());
        transacao.estado = "CONFIRMADA";

        return true;
    }

    synchronized public boolean cancelarReservaConfirmada(String idTransacao, String idPassageiro) {
        garantirReservasPendentes();

        ReservaPendente transacao = reservasPendentes.get(idTransacao);

        if (transacao == null
                || !transacao.idPassageiro.equals(idPassageiro)) {
            return false;
        }

        // Cancelamento repetido: a operação já foi realizada.
        if (transacao.estado.equals("CANCELADA")) {
            return true;
        }

        if (!transacao.estado.equals("CONFIRMADA")) {
            return false;
        }

        int[] reserva = caronas.remove(idPassageiro);

        if (reserva == null) {
            return false;
        }

        for (int i = 0; i < reserva.length; i++) {
            vagasPorTrecho.set(
                    i, vagasPorTrecho.get(i) + reserva[i]
            );
        }

        transacao.estado = "CANCELADA";

        return true;
    }

    // Informações necessárias para confirmar ou desfazer uma reserva.
    private static class ReservaPendente {
        String idPassageiro;
        String origem;
        String destino;
        int[] vetor;
        String estado;

        ReservaPendente(String idPassageiro, String origem, String destino, int[] vetor) {

            this.idPassageiro = idPassageiro;
            this.origem = origem;
            this.destino = destino;
            this.vetor = vetor.clone();
            this.estado = "PREPARADA";
        }
    }
}
