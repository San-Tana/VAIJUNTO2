
package vaijunto.src.model;

public final class identificadorGlobal {

    private identificadorGlobal() {}

    public static String gerar(String empresa, String idLocal) {
        if (empresa == null || empresa.isBlank()
                || idLocal == null || idLocal.isBlank()
                || empresa.contains(":") || idLocal.contains(":")) {
            throw new IllegalArgumentException("Identificador inválido.");
        }

        return empresa + ":" + idLocal;
    }

    public static String obterEmpresa(String idGlobal) {
        validar(idGlobal);
        return idGlobal.substring(0, idGlobal.indexOf(':'));
    }

    public static String obterIdLocal(String idGlobal) {
        validar(idGlobal);
        return idGlobal.substring(idGlobal.indexOf(':') + 1);
    }

    private static void validar(String idGlobal) {
        if (idGlobal == null) {
            throw new IllegalArgumentException("Identificador global inválido.");
        }

        int separador = idGlobal.indexOf(':');

        if (separador <= 0 || separador == idGlobal.length() - 1 || 
            idGlobal.indexOf(':', separador + 1) != -1) {
            throw new IllegalArgumentException("Identificador global inválido.");
        }
    }
}
