package com.sos.msseguridad.dto;

import java.util.List;

public record PoliticaClaveResponse(int longitudMinima, int longitudMaxima,
                                    String simbolosPermitidos, List<String> reglas) {
}
