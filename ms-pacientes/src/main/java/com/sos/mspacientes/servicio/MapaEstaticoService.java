package com.sos.mspacientes.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Mapa estatico PNG hecho a medida: descarga los tiles de OpenStreetMap,
 * los compone con Java2D y dibuja los marcadores. No requiere API key ni
 * librerias externas. Si OSM no responde, devuelve null y el correo sale
 * sin el mapa (nunca rompe el envio principal).
 */
@Service
public class MapaEstaticoService {

    private static final Logger log = LoggerFactory.getLogger(MapaEstaticoService.class);
    private static final String URL_TILE = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "SOS-Clinic-Prototipo-Academico";
    private static final int TILE = 256;

    /** tipo "usuario" (azul) o "farmacia" (verde). */
    public record Marcador(double lat, double lon, String tipo) {}

    public byte[] generarMapa(double latCentro, double lonCentro, int radioMetros,
                              int ancho, int alto, List<Marcador> marcadores) {
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        try {
            int zoom = elegirZoom(radioMetros, ancho, alto, latCentro);
            double centroX = longitudAPixelX(lonCentro, zoom);
            double centroY = latitudAPixelY(latCentro, zoom);
            int origenX = (int) Math.round(centroX - ancho / 2.0);
            int origenY = (int) Math.round(centroY - alto / 2.0);

            List<TareaTile> tareas = new ArrayList<>();
            int tileXInicial = Math.floorDiv(origenX, TILE);
            int tileXFinal = Math.floorDiv(origenX + ancho, TILE);
            int tileYInicial = Math.floorDiv(origenY, TILE);
            int tileYFinal = Math.floorDiv(origenY + alto, TILE);
            for (int tx = tileXInicial; tx <= tileXFinal; tx++) {
                for (int ty = tileYInicial; ty <= tileYFinal; ty++) {
                    tareas.add(new TareaTile(zoom, tx, ty));
                }
            }

            List<Future<BufferedImage>> futuros = hilos.invokeAll(tareas, 20, TimeUnit.SECONDS);

            BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = lienzo.createGraphics();
            g.setColor(new Color(0xf0, 0xf0, 0xe8));
            g.fillRect(0, 0, ancho, alto);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            boolean algunTile = false;
            for (int i = 0; i < tareas.size(); i++) {
                TareaTile tarea = tareas.get(i);
                BufferedImage tile = futuros.get(i).get();
                if (tile != null) {
                    int x = tarea.tileX * TILE - origenX;
                    int y = tarea.tileY * TILE - origenY;
                    g.drawImage(tile, x, y, null);
                    algunTile = true;
                }
            }

            if (!algunTile) {
                g.dispose();
                return null;
            }

            dibujarMarcadores(g, marcadores, origenX, origenY, zoom, ancho, alto);
            g.dispose();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (Exception e) {
            log.warn("No se pudo generar el mapa estatico: {}", e.getMessage());
            return null;
        } finally {
            hilos.shutdownNow();
        }
    }

    private void dibujarMarcadores(Graphics2D g, List<Marcador> marcadores,
                                   int origenX, int origenY, int zoom, int ancho, int alto) {
        if (marcadores == null) return;
        for (Marcador m : marcadores) {
            int x = (int) Math.round(longitudAPixelX(m.lon(), zoom)) - origenX;
            int y = (int) Math.round(latitudAPixelY(m.lat(), zoom)) - origenY;
            if (x < -20 || y < -20 || x > ancho + 20 || y > alto + 20) continue;

            boolean esUsuario = "usuario".equals(m.tipo());
            int radio = esUsuario ? 10 : 8;

            // sombra
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(x - radio + 1, y - radio + 2, radio * 2, radio * 2);
            // relleno
            g.setColor(esUsuario ? new Color(0x25, 0x63, 0xeb) : new Color(0x16, 0xa3, 0x4a));
            g.fillOval(x - radio, y - radio, radio * 2, radio * 2);
            // borde blanco
            g.setColor(Color.WHITE);
            g.drawOval(x - radio, y - radio, radio * 2, radio * 2);

            if (esUsuario) {
                // puntito blanco central en el marcador del usuario
                g.fillOval(x - 3, y - 3, 6, 6);
            } else {
                // cruce blanco de farmacia
                g.fillRect(x - 4, y - 1, 8, 2);
                g.fillRect(x - 1, y - 4, 2, 8);
            }
        }
    }

    private int elegirZoom(int radioMetros, int ancho, int alto, double lat) {
        int mejor = 12;
        for (int z = 17; z >= 12; z--) {
            double metrosPorPixel = 156543.03392 * Math.cos(Math.toRadians(lat)) / Math.pow(2, z);
            double anchoMetros = ancho * metrosPorPixel;
            double altoMetros = alto * metrosPorPixel;
            if (anchoMetros >= radioMetros * 2 && altoMetros >= radioMetros * 1.2) {
                mejor = z;
            }
        }
        return mejor;
    }

    static double longitudAPixelX(double lon, int zoom) {
        double mundo = (double) TILE * (1 << zoom);
        return (lon + 180.0) / 360.0 * mundo;
    }

    static double latitudAPixelY(double lat, int zoom) {
        double mundo = (double) TILE * (1 << zoom);
        double rad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * mundo;
    }

    private record TareaTile(int zoom, int tileX, int tileY) implements Callable<BufferedImage> {
        @Override
        public BufferedImage call() {
            // Los indices de tiles se envuelven horizontalmente (mundo cerrado).
            int max = 1 << zoom;
            int tx = Math.floorMod(tileX, max);
            if (tileY < 0 || tileY >= max) return null;
            HttpURLConnection conexion = null;
            try {
                URL url = new URL(String.format(URL_TILE, zoom, tx, tileY));
                conexion = (HttpURLConnection) url.openConnection();
                conexion.setRequestProperty("User-Agent", USER_AGENT);
                conexion.setConnectTimeout(4000);
                conexion.setReadTimeout(6000);
                if (conexion.getResponseCode() != 200) return null;
                try (InputStream entrada = conexion.getInputStream()) {
                    return ImageIO.read(entrada);
                }
            } catch (Exception e) {
                return null;
            } finally {
                if (conexion != null) conexion.disconnect();
            }
        }
    }
}
