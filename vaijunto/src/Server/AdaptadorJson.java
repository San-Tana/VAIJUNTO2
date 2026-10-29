package vaijunto.src.Server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class AdaptadorJson {

    private static final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    private AdaptadorJson() {}

    public static String gerar(Object objeto) {
        return gson.toJson(objeto);
    }

    public static <T> T ler(String json, Class<T> classe) {
        return gson.fromJson(json, classe);
    }
}