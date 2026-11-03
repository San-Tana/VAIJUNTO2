package vaijunto.src.model;

import java.time.LocalDateTime;

public class data {
    private int dia;
    private int mes;
    private int ano;
    private String horario;

    public data(String dia, String mes, String ano, String horario) {
        this.dia = Integer.parseInt(dia);
        this.mes = Integer.parseInt(mes);
        this.ano = Integer.parseInt(ano);
        this.horario = horario;
    }

    public int getDia() { return dia; }
    public int getMes() { return mes; }
    public int getAno() { return ano; }
    public String getHora() { return horario; }

    public LocalDateTime paraLocalDateTime() {
        String[] partes = horario.split(":");

        if (partes.length != 2) {
            throw new IllegalArgumentException("Horário inválido");
        }

        return LocalDateTime.of(
            ano, mes, dia,
            Integer.parseInt(partes[0]),
            Integer.parseInt(partes[1]));
    }

    @Override 
    public String toString() {return dia + "&" + mes + "&" + ano + "&" + horario; }
}
