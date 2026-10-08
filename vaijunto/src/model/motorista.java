package vaijunto.src.model;

public class motorista {
    private String CNH;
    private String tipo; //Ex.: Moto ou carro
    private String modelo;
    private String placa;

    public motorista(String CNH, String tipo, String modelo, String placa) {
        this.CNH = CNH;
        this.tipo = tipo;
        this.modelo = modelo;
        this.placa = placa;
    }

    public String getCNH() { return CNH; }
    public String getTipo() { return tipo; }
    public String getModelo() { return modelo; }
    public String getPlaca() { return placa; }

    public void setCNH(String nova) { this.CNH = nova; }
    public void editVeiculo(String tipo, String modelo, String placa) {
        this.tipo = tipo;
        this.modelo = modelo;
        this.placa = placa;
    }
    public void editVeiculo(String modelo, String placa) {
        this.modelo = modelo;
        this.placa = placa;
    }

    @Override 
    public String toString() {
        return CNH + "&" + tipo + "&" + modelo + "&" + placa;
    }
}