package vaijunto.src.model;

public class data {
    // Resolvi manter todos como String para eviter erros de entrada do usuário.
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

    @Override 
    public String toString() {return dia + "&" + mes + "&" + ano + "&" + horario; }
}
