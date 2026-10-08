package vaijunto.src.model;

public class corridaComposta extends corrida {
    private String corrida1;
    private String corrida2;

    public corridaComposta(String ID, corrida corrida1, corrida corrida2, String origem, String destino) {
        super(ID, corrida1.getmotorista() + "e" + corrida2.getmotorista(), origem, destino, corrida1.getHorario(), 
        (corrida1.getBancos() <= corrida2.getBancos() ? corrida1.getBancos() : corrida2.getBancos()), corrida1.getPreco() + corrida2.getPreco());
        this.corrida1 = corrida1.getID();
        this.corrida2 = corrida2.getID();
    }

    public String getCorrida1() { return corrida1; }
    public String getCorrida2() { return corrida2; }
    
    @Override 
    public String toString() {
        return super.toString() + ":" + corrida1 + ":" + corrida2;
    }
}