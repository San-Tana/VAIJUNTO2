
package vaijunto.src.Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import vaijunto.src.model.cliente;
import vaijunto.src.model.corrida;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class PersistenciaJson {

    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final Path PASTA = Path.of("dados");

    private PersistenciaJson() {}

    // Estrutura que representa tudo o que será salvo no JSON.
    public static class Snapshot {
        public Map<String, cliente> registros;
        public Map<String, corrida> corridas;
        public Map<String, Consorcio.ReservaComposta> reservasCompostas;
        public int IDcounter;

        public Snapshot() {
            registros = new HashMap<>();
            corridas = new HashMap<>();
            reservasCompostas = new HashMap<>();
            IDcounter = 1;
        }
    }

    // Cada empresa terá seu próprio arquivo.
    private static Path caminhoArquivo(String empresa) {
        if (empresa == null || empresa.isBlank()
                || empresa.contains("/")
                || empresa.contains("\\")
                || empresa.contains(":")) {
            throw new IllegalArgumentException("Nome de empresa inválido");
        }

        return PASTA.resolve(empresa + ".json");
    }

    public static synchronized void salvar(
            String empresa,
            Map<String, cliente> registros,
            Map<String, corrida> corridas,
            Map<String, Consorcio.ReservaComposta> reservasCompostas,
            int IDcounter) throws IOException {

        Files.createDirectories(PASTA);

        Snapshot snapshot = new Snapshot();
        snapshot.registros = new HashMap<>(registros);
        snapshot.corridas = new HashMap<>(corridas);
        snapshot.reservasCompostas = new HashMap<>(reservasCompostas);
        snapshot.IDcounter = IDcounter;

        String json = gson.toJson(snapshot);

        Path destino = caminhoArquivo(empresa);
        Path temporario = PASTA.resolve(empresa + ".tmp");

        // Primeiro escreve em um arquivo temporário para evitar perder o JSON anterior e ficar com um arquivo incompleto.
        Files.writeString(
                temporario,
                json,
                StandardCharsets.UTF_8
        );

        // Depois substitui o arquivo principal.
        // Isso reduz o risco de deixar um JSON incompleto
        // caso o processo seja interrompido durante a gravação.
        try {
            Files.move(
                    temporario,
                    destino,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            // Alguns sistemas de arquivos não suportam ATOMIC_MOVE.
            Files.move(
                    temporario,
                    destino,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    public static synchronized Snapshot carregar(String empresa)
            throws IOException {

        Path arquivo = caminhoArquivo(empresa);

        // Primeira inicialização: ainda não existe arquivo.
        if (!Files.exists(arquivo)) {
            return null;
        }

        String json = Files.readString(
                arquivo,
                StandardCharsets.UTF_8
        );

        Snapshot snapshot = gson.fromJson(json, Snapshot.class);

        if (snapshot == null) {
            throw new IOException("Arquivo JSON vazio ou inválido");
        }

        // Proteção contra campos ausentes no JSON.
        if (snapshot.registros == null) {
            snapshot.registros = new HashMap<>();
        }

        if (snapshot.corridas == null) {
            snapshot.corridas = new HashMap<>();
        }

        if (snapshot.reservasCompostas == null) {
            snapshot.reservasCompostas = new HashMap<>();
        }

        if (snapshot.IDcounter < 1) {
            snapshot.IDcounter = 1;
        }

        // As sessões não devem continuar abertas após reiniciar
        // o servidor.
        for (cliente c : snapshot.registros.values()) {
            if (c != null) {
                c.deslogar();
            }
        }

        return snapshot;
    }
}
