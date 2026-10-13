package com.sos.msanalitica.repositorio;

import com.sos.msanalitica.dto.CapacidadDto;
import com.sos.msanalitica.dto.ConteoDto;
import com.sos.msanalitica.dto.HoraDto;
import com.sos.msanalitica.dto.ResumenDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class DashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ResumenDto obtenerResumen(LocalDate desde, LocalDate hasta, String localidad, String turno) {
        MapSqlParameterSource parametros = parametros(desde, hasta, localidad, turno);
        String where = whereLlegadas(localidad, turno);
        String sql = "SELECT COUNT(*) AS total, COALESCE(AVG(tiempo_espera_min), 0) AS espera_promedio "
                + "FROM llegadas " + where;
        return jdbc.queryForObject(sql, parametros, (rs, row) ->
                new ResumenDto(rs.getLong("total"), rs.getDouble("espera_promedio")));
    }

    public List<HoraDto> obtenerPorHora(LocalDate desde, LocalDate hasta, String localidad, String turno) {
        MapSqlParameterSource parametros = parametros(desde, hasta, localidad, turno);
        String sql = "SELECT HOUR(fecha_hora) AS hora, COUNT(*) AS total FROM llegadas "
                + whereLlegadas(localidad, turno)
                + " GROUP BY HOUR(fecha_hora) ORDER BY hora";
        return jdbc.query(sql, parametros, (rs, row) ->
                new HoraDto(rs.getInt("hora"), rs.getLong("total")));
    }

    public List<ConteoDto> obtenerPorPrioridad(LocalDate desde, LocalDate hasta,
                                               String localidad, String turno) {
        return obtenerDistribucion("prioridad", desde, hasta, localidad, turno);
    }

    public List<ConteoDto> obtenerPorLocalidad(LocalDate desde, LocalDate hasta,
                                                String localidad, String turno) {
        return obtenerDistribucion("localidad", desde, hasta, localidad, turno);
    }

    public List<CapacidadDto> obtenerCapacidad(LocalDate desde, LocalDate hasta, String turno) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        String filtroTurno = "";
        if (turno != null && !turno.isBlank()) {
            filtroTurno = " AND c.turno = :turno";
            parametros.addValue("turno", turno.trim());
        }

        String sql = "SELECT c.turno, "
                + "ROUND(AVG(COALESCE(d.demanda, 0)), 2) AS llegadas_promedio, "
                + "ROUND(AVG(c.personal_disponible), 2) AS personal_promedio, "
                + "ROUND(AVG(c.puestos_triage), 2) AS puestos_promedio, "
                + "ROUND(AVG(c.camas_disponibles), 2) AS camas_promedio "
                + "FROM capacidad_turno c "
                + "LEFT JOIN (SELECT fecha, turno, COUNT(*) AS demanda FROM llegadas "
                + "WHERE fecha BETWEEN :desde AND :hasta GROUP BY fecha, turno) d "
                + "ON d.fecha = c.fecha AND d.turno = c.turno "
                + "WHERE c.fecha BETWEEN :desde AND :hasta" + filtroTurno + " "
                + "GROUP BY c.turno "
                + "ORDER BY CASE c.turno WHEN 'Mañana' THEN 1 WHEN 'Tarde' THEN 2 "
                + "WHEN 'Noche' THEN 3 ELSE 4 END, c.turno";

        return jdbc.query(sql, parametros, (rs, row) -> new CapacidadDto(
                rs.getString("turno"),
                rs.getDouble("llegadas_promedio"),
                rs.getDouble("personal_promedio"),
                rs.getDouble("puestos_promedio"),
                rs.getDouble("camas_promedio")));
    }

    private List<ConteoDto> obtenerDistribucion(String columna, LocalDate desde, LocalDate hasta,
                                                String localidad, String turno) {
        MapSqlParameterSource parametros = parametros(desde, hasta, localidad, turno);
        String sql = "SELECT COALESCE(" + columna + ", 'Sin clasificar') AS etiqueta, COUNT(*) AS total "
                + "FROM llegadas " + whereLlegadas(localidad, turno)
                + " GROUP BY " + columna + " ORDER BY " + columna;
        return jdbc.query(sql, parametros, (rs, row) ->
                new ConteoDto(rs.getString("etiqueta"), rs.getLong("total")));
    }

    private MapSqlParameterSource parametros(LocalDate desde, LocalDate hasta,
                                              String localidad, String turno) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("desde", desde)
                .addValue("hasta", hasta);
        if (localidad != null && !localidad.isBlank()) {
            parametros.addValue("localidad", localidad.trim());
        }
        if (turno != null && !turno.isBlank()) {
            parametros.addValue("turno", turno.trim());
        }
        return parametros;
    }

    private String whereLlegadas(String localidad, String turno) {
        StringBuilder where = new StringBuilder("WHERE fecha BETWEEN :desde AND :hasta");
        if (localidad != null && !localidad.isBlank()) {
            where.append(" AND localidad = :localidad");
        }
        if (turno != null && !turno.isBlank()) {
            where.append(" AND turno = :turno");
        }
        return where.toString();
    }
}
