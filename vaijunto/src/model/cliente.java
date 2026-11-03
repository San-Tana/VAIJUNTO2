package vaijunto.src.model;

import java.util.List;
import java.util.ArrayList;

public class cliente {
    private boolean logado;
    private final String empresa; // Identifica a empresa onde o usuário foi registrado
    private String usuario;
    private String senha;
    public motorista veiculo;
    private List<String> corridas;

    // Cliente passageiro
    public cliente (String usuario, String senha, String empresa) {
        this.logado = true;
        this.usuario = usuario;
        this.senha = senha;
        this.empresa = empresa; 

        this.corridas = new ArrayList<>();
    }

    // Cliente passageiro E motorista
    public cliente (String usuario, String senha, String empresa, motorista veiculo) {
        this.logado = true;
        this.usuario = usuario;
        this.senha = senha;
        this.veiculo = veiculo;
        this.empresa = empresa;

        this.corridas = new ArrayList<String>();
    }

    public String getUsuario() { return usuario; }
    public String getSenha() { return senha; }
    public motorista getVeiculo() { return veiculo; }
    public boolean getLogado() { return logado; }
    
    public String getEmpresa() {  return empresa; }
    public String getIdGlobal() {  return identificadorGlobal.gerar(empresa, usuario); }

    public void setUsuario(String novo) { this.usuario = novo; }
    public void setSenha(String nova) { this.senha = nova; }

    public void logar() { this.logado = true; }
    public void deslogar() { this.logado = false; }

    public void addCorrida(String ID) { this.corridas.add(ID); }
    public List<String> getCorridas() { return corridas; }
    public void removerCorrida(String ID) { corridas.remove(ID); }

    @Override 
    public String toString() { 
        String cliente = logado + ";" + usuario + ";" + senha + ";" + veiculo;
        if (!corridas.isEmpty()) { cliente += ";" + String.join("&", corridas); }
        return cliente; 
    }
}
