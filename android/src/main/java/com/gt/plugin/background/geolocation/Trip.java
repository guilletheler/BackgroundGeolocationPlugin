package com.gt.plugin.background.geolocation;

import android.location.Location;
import android.util.Log;

import com.getcapacitor.JSObject;

import java.util.ArrayList;
import java.util.List;

public class Trip {

    static final String TAG = "Trip";

    List<Location> puntos;
    Double distancia;
    Long timestampInicio;
    Long timestampFin;
    private Float lastBearing = null;

    private static final float MAX_ACCURACY_METERS = 25.0f;
    private static final float MIN_DIST_STRAIGHT_METERS = 10.0f;
    private static final float MIN_DIST_TURN_METERS = 5.0f;
    private static final float MIN_DIST_STOPPED_METERS = 5.0f;
    private static final float TURN_BEARING_DELTA_DEG = 15.0f;
    private static final float MAX_SPEED_MPS = 50.0f; // 180 km/h
    private static final double SIMPLIFY_EPSILON_METERS = 4.0;

    private final List<Location> puntosSospechosos = new ArrayList<>();

    public Trip() {
        this.puntos = new ArrayList<>();
        this.distancia = 0.0;
        this.timestampInicio = System.currentTimeMillis();
    }

    public static JSObject toJSObject(Trip trip) {
        return toJSObject(trip, false);
    }

    public static JSObject toJSObject(Trip trip, boolean withPath) {
        JSObject ret = null;
        if (trip != null) {
            ret = new JSObject();
            ret.put("distancia", trip.getDistancia());
            ret.put("timestampInicio", trip.getTimestampInicio());
            ret.put("timestampFin", trip.getTimestampFin());
            if (withPath) {
                List<JSObject> puntos = new ArrayList<>();
                for (Location punto : trip.getPuntos()) {
                    puntos.add(LocationPayloadBuilder.locationToJson(punto));
                }
                ret.put("puntos", puntos);
            }
        }
        return ret;
    }

    public List<Location> getPuntos() {
        return puntos;
    }

    public Double getDistancia() {
        return distancia;
    }

    public Long getTimestampInicio() {
        return timestampInicio;
    }

    public Long getTimestampFin() {
        return timestampFin;
    }

    public void setTimestampFin(Long timestampFin) {
        this.timestampFin = timestampFin;
    }

    public void addPunto(Location location) {
        if (location == null) {
            return;
        }

        if (!location.hasAccuracy() || location.getAccuracy() > MAX_ACCURACY_METERS) {
            Log.d(TAG, "Punto descartado por baja precisión: " + (location.hasAccuracy() ? location.getAccuracy() + "m" : "sin accuracy"));
            return;
        }

        Location ultimo = this.getUltimoPunto();
        if (ultimo == null) {
            puntos.add(location);
            if (location.hasBearing() && location.getSpeed() > 1.0f) {
                lastBearing = location.getBearing();
            }
            return;
        }

        float curDist = ultimo.distanceTo(location);

        // Determinación de rumbo / bearing para detectar esquinas/curvas
        float currentBearing = (location.hasBearing() && location.getSpeed() > 1.0f)
                ? location.getBearing()
                : ultimo.bearingTo(location);

        float deltaBearing = 0.0f;
        if (lastBearing != null) {
            deltaBearing = Math.abs(currentBearing - lastBearing);
            if (deltaBearing > 180.0f) {
                deltaBearing = 360.0f - deltaBearing;
            }
        }

        boolean isTurn = lastBearing != null && deltaBearing >= TURN_BEARING_DELTA_DEG;
        boolean isStopped = location.hasSpeed() && location.getSpeed() < 0.6f;

        float requiredMinDist = isTurn ? MIN_DIST_TURN_METERS : (isStopped ? MIN_DIST_STOPPED_METERS : MIN_DIST_STRAIGHT_METERS);

        if (!puntosSospechosos.isEmpty()) {
            Location lastCandidato = puntosSospechosos.get(puntosSospechosos.size() - 1);

            long timeDesdeUltimo = Math.abs(location.getTime() - ultimo.getTime());
            float distDesdeUltimo = ultimo.distanceTo(location);
            float speedDesdeUltimo = (timeDesdeUltimo > 0) ? (distDesdeUltimo / (timeDesdeUltimo / 1000.0f)) : Float.MAX_VALUE;

            long timeDesdeCandidato = Math.abs(location.getTime() - lastCandidato.getTime());
            float distDesdeCandidato = lastCandidato.distanceTo(location);
            float speedDesdeCandidato = (timeDesdeCandidato > 0) ? (distDesdeCandidato / (timeDesdeCandidato / 1000.0f)) : Float.MAX_VALUE;

            if (timeDesdeUltimo >= 500 && speedDesdeUltimo <= MAX_SPEED_MPS) {
                Log.d(TAG, "Sospechosos descartados: retorno a ruta confirmada");
                puntosSospechosos.clear();
                if (distDesdeUltimo >= requiredMinDist) {
                    this.distancia += distDesdeUltimo;
                    puntos.add(location);
                    this.lastBearing = currentBearing;
                }
                return;
            }

            if (timeDesdeCandidato >= 500 && speedDesdeCandidato <= MAX_SPEED_MPS && distDesdeCandidato >= requiredMinDist) {
                Log.d(TAG, "Candidatos confirmados por punto subsecuente consistente");
                Location prev = ultimo;
                for (Location cand : puntosSospechosos) {
                    float d = prev.distanceTo(cand);
                    this.distancia += d;
                    puntos.add(cand);
                    prev = cand;
                }
                float d = prev.distanceTo(location);
                this.distancia += d;
                puntos.add(location);
                this.lastBearing = currentBearing;
                puntosSospechosos.clear();
                return;
            }

            if (timeDesdeCandidato >= 500 && distDesdeCandidato < requiredMinDist) {
                Log.d(TAG, "Candidatos confirmados por detención en la nueva posición");
                Location prev = ultimo;
                for (Location cand : puntosSospechosos) {
                    float d = prev.distanceTo(cand);
                    this.distancia += d;
                    puntos.add(cand);
                    prev = cand;
                }
                this.lastBearing = currentBearing;
                puntosSospechosos.clear();
                return;
            }

            if (puntosSospechosos.size() >= 3) {
                puntosSospechosos.remove(0);
            }
            puntosSospechosos.add(location);
            return;
        }

        if (curDist < requiredMinDist) {
            Log.d(TAG, "Punto ignorado por distancia mínima (" + requiredMinDist + "m): " + curDist);
            return;
        }

        long timeDiffMillis = Math.abs(location.getTime() - ultimo.getTime());
        float speedMps = (timeDiffMillis > 0) ? (curDist / (timeDiffMillis / 1000.0f)) : Float.MAX_VALUE;
        boolean speedSensorInverosimil = location.hasSpeed() && location.getSpeed() > MAX_SPEED_MPS;

        if (timeDiffMillis < 500 || speedMps > MAX_SPEED_MPS || speedSensorInverosimil) {
            Log.d(TAG, "Punto guardado como sospechoso para validación posterior: " + (speedMps * 3.6f) + " km/h");
            puntosSospechosos.add(location);
            return;
        }

        this.distancia += curDist;
        puntos.add(location);
        this.lastBearing = currentBearing;
    }

    public Location getUltimoPunto() {
        if (puntos.isEmpty()) {
            return null;
        }
        return puntos.get(puntos.size() - 1);
    }

    public void simplificarRuta() {
        if (this.puntos != null && this.puntos.size() > 2) {
            this.puntos = simplify(this.puntos, SIMPLIFY_EPSILON_METERS);
        }
    }

    public static List<Location> simplify(List<Location> points, double epsilonMeters) {
        if (points == null || points.size() <= 2) {
            return points != null ? new ArrayList<>(points) : new ArrayList<>();
        }

        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;

        douglasPeuckerRecursive(points, 0, points.size() - 1, epsilonMeters, keep);

        List<Location> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static void douglasPeuckerRecursive(List<Location> points, int startIndex, int endIndex, double epsilonMeters, boolean[] keep) {
        if (endIndex <= startIndex + 1) {
            return;
        }

        Location start = points.get(startIndex);
        Location end = points.get(endIndex);

        double maxDist = 0.0;
        int maxIndex = startIndex;

        for (int i = startIndex + 1; i < endIndex; i++) {
            double dist = perpendicularDistanceMeters(points.get(i), start, end);
            if (dist > maxDist) {
                maxDist = dist;
                maxIndex = i;
            }
        }

        if (maxDist > epsilonMeters) {
            keep[maxIndex] = true;
            douglasPeuckerRecursive(points, startIndex, maxIndex, epsilonMeters, keep);
            douglasPeuckerRecursive(points, maxIndex, endIndex, epsilonMeters, keep);
        }
    }

    private static double perpendicularDistanceMeters(Location p, Location lineStart, Location lineEnd) {
        double lat1 = Math.toRadians(lineStart.getLatitude());
        double lon1 = Math.toRadians(lineStart.getLongitude());
        double lat2 = Math.toRadians(lineEnd.getLatitude());
        double lon2 = Math.toRadians(lineEnd.getLongitude());
        double latP = Math.toRadians(p.getLatitude());
        double lonP = Math.toRadians(p.getLongitude());

        double meanLat = (lat1 + lat2) / 2.0;
        double cosLat = Math.cos(meanLat);
        double R = 6371000.0;

        double x2 = (lon2 - lon1) * cosLat * R;
        double y2 = (lat2 - lat1) * R;

        double xp = (lonP - lon1) * cosLat * R;
        double yp = (latP - lat1) * R;

        double lineLenSq = x2 * x2 + y2 * y2;
        if (lineLenSq == 0) {
            return Math.hypot(xp, yp);
        }

        double t = (xp * x2 + yp * y2) / lineLenSq;
        t = Math.max(0.0, Math.min(1.0, t));

        double projX = t * x2;
        double projY = t * y2;

        return Math.hypot(xp - projX, yp - projY);
    }
}
