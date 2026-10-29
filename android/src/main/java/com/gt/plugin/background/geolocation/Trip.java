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
    private float lastValidatedSpeedMps = 0.0f;
    private Long lastStationaryTimeMs = null;

    private static final float MAX_ACCURACY_METERS = 20.0f;
    private static final float MIN_DIST_STRAIGHT_METERS = 10.0f;
    private static final float MIN_DIST_TURN_METERS = 5.0f;
    private static final float MIN_DIST_STOPPED_METERS = 6.0f;
    private static final float TURN_BEARING_DELTA_DEG = 15.0f;

    // Límites cinemáticos adaptativos
    private static final float ABSOLUTE_MAX_SPEED_MPS = 45.0f; // 162 km/h techo absoluto
    private static final float BASE_URBAN_SPEED_MPS = 18.0f;   // ~65 km/h margen inicial urbano
    private static final float HIGH_SPEED_THRESHOLD_MPS = 22.2f; // 80 km/h
    private static final float MAX_ACCEL_MPS2 = 3.5f;          // 0-100 km/h en ~8s
    private static final float MAX_EFFECTIVE_ACCEL_DT_SEC = 7.0f;
    private static final float HIGH_SPEED_MAX_DEVIATION_RATIO = 0.35f; // +-35% sobre 80 km/h
    private static final float HIGH_SPEED_MAX_TURN_RATE_DEG_SEC = 28.0f;
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
            lastStationaryTimeMs = location.getTime();
            if (location.hasSpeed()) {
                lastValidatedSpeedMps = Math.min(location.getSpeed(), ABSOLUTE_MAX_SPEED_MPS);
            }
            if (location.hasBearing() && location.getSpeed() > 1.0f) {
                lastBearing = location.getBearing();
            }
            return;
        }

        float curDist = ultimo.distanceTo(location);
        long referenceUltimoTimeMs = (lastStationaryTimeMs != null && lastStationaryTimeMs > ultimo.getTime())
                ? lastStationaryTimeMs
                : ultimo.getTime();

        // Determinación de rumbo / bearing para detectar esquinas/curvas
        float currentBearing = (location.hasBearing() && location.getSpeed() > 1.0f)
                ? location.getBearing()
                : ultimo.bearingTo(location);

        float deltaBearing = angleDiff(currentBearing, lastBearing);

        boolean isTurn = lastBearing != null && deltaBearing >= TURN_BEARING_DELTA_DEG;
        boolean isStopped = location.hasSpeed() && location.getSpeed() < 0.6f;

        float requiredMinDist = isTurn ? MIN_DIST_TURN_METERS : (isStopped ? MIN_DIST_STOPPED_METERS : MIN_DIST_STRAIGHT_METERS);

        // Si ya tenemos puntos sospechosos en observación, evaluamos consistencia
        if (!puntosSospechosos.isEmpty()) {
            long timeDesdeUltimo = Math.abs(location.getTime() - referenceUltimoTimeMs);
            float distDesdeUltimo = ultimo.distanceTo(location);
            float dtUltimoSec = Math.max(0.5f, timeDesdeUltimo / 1000.0f);
            float speedDesdeUltimo = distDesdeUltimo / dtUltimoSec;

            // 1. ¿El nuevo punto vuelve a ser consistente con el último punto válido antes del salto?
            if (timeDesdeUltimo >= 500 && isTransitionPlausible(ultimo, location, distDesdeUltimo, dtUltimoSec, speedDesdeUltimo, lastValidatedSpeedMps, lastBearing)) {
                Log.d(TAG, "Sospechosos descartados: retorno a ruta confirmada");
                puntosSospechosos.clear();
                if (distDesdeUltimo >= requiredMinDist) {
                    acceptLocation(location, speedDesdeUltimo, currentBearing);
                } else {
                    lastStationaryTimeMs = location.getTime();
                    lastValidatedSpeedMps = 0.0f;
                }
                return;
            }

            // 2. Evaluar si el nuevo punto continúa de forma coherente la serie de sospechosos
            Location lastCandidato = puntosSospechosos.get(puntosSospechosos.size() - 1);
            long timeDesdeCand = Math.abs(location.getTime() - lastCandidato.getTime());
            float distDesdeCand = lastCandidato.distanceTo(location);
            float dtCandSec = Math.max(0.5f, timeDesdeCand / 1000.0f);
            float speedDesdeCand = distDesdeCand / dtCandSec;

            Float candBearing = null;
            float prevCandSpeed = lastValidatedSpeedMps;
            if (puntosSospechosos.size() >= 2) {
                Location prevCand = puntosSospechosos.get(puntosSospechosos.size() - 2);
                candBearing = prevCand.bearingTo(lastCandidato);
                long dtPrev = Math.max(500L, Math.abs(lastCandidato.getTime() - prevCand.getTime()));
                prevCandSpeed = prevCand.distanceTo(lastCandidato) / (dtPrev / 1000.0f);
            } else if (lastCandidato.hasBearing() && lastCandidato.getSpeed() > 1.5f) {
                candBearing = lastCandidato.getBearing();
                prevCandSpeed = lastCandidato.getSpeed();
            }

            boolean consistentWithLastCandidate = timeDesdeCand >= 500
                    && distDesdeCand >= MIN_DIST_STRAIGHT_METERS
                    && isTransitionPlausible(lastCandidato, location, distDesdeCand, dtCandSec, speedDesdeCand, prevCandSpeed, candBearing);

            if (consistentWithLastCandidate) {
                puntosSospechosos.add(location);
                // Exigimos al menos 3 puntos consecutivos coherentes en movimiento para confirmar un salto de rama
                if (puntosSospechosos.size() >= 3 && isCandidateChainCoherent(puntosSospechosos)) {
                    Log.d(TAG, "Cadena de 3 candidatos en movimiento coherente confirmada");
                    for (Location cand : puntosSospechosos) {
                        puntos.add(cand);
                    }
                    puntosSospechosos.clear();
                    pruneSpikesAndRecalculateDistance();
                    updateStateFromTail();
                }
                return;
            }

            // Si no es consistente con el candidato previo (ej. detenido en celda falsa o nuevo salto errático),
            // reemplazamos la cola sospechosa sin confirmar jamás por detención
            if (distDesdeCand >= MIN_DIST_STRAIGHT_METERS) {
                puntosSospechosos.clear();
                puntosSospechosos.add(location);
            }
            return;
        }

        if (curDist < requiredMinDist) {
            // El vehículo permanece detenido o casi inmóvil cerca del último punto válido:
            // actualizamos el reloj de referencia estacionario para que el tiempo detenido en un semáforo
            // no infle el delta-t (permitiendo saltos falsos) ni distorsione la velocidad al arrancar.
            if (curDist <= MIN_DIST_STOPPED_METERS || isStopped) {
                lastStationaryTimeMs = location.getTime();
                lastValidatedSpeedMps = location.hasSpeed() ? Math.min(location.getSpeed(), 1.5f) : 0.0f;
            }
            Log.d(TAG, "Punto ignorado por distancia mínima (" + requiredMinDist + "m): " + curDist);
            return;
        }

        long timeDiffMillis = Math.abs(location.getTime() - referenceUltimoTimeMs);
        float dtSec = (timeDiffMillis > 0) ? (timeDiffMillis / 1000.0f) : 0.0f;
        float speedMps = (dtSec > 0.0f) ? (curDist / dtSec) : Float.MAX_VALUE;

        if (timeDiffMillis < 500 || !isTransitionPlausible(ultimo, location, curDist, dtSec, speedMps, lastValidatedSpeedMps, lastBearing)) {
            Log.d(TAG, "Punto guardado como sospechoso para validación posterior: " + (speedMps * 3.6f) + " km/h (dist=" + curDist + "m, dt=" + dtSec + "s)");
            puntosSospechosos.add(location);
            return;
        }

        acceptLocation(location, speedMps, currentBearing);
    }

    private void acceptLocation(Location location, float segmentSpeedMps, float currentBearing) {
        puntos.add(location);
        this.lastStationaryTimeMs = location.getTime();
        float dopplerSpeed = location.hasSpeed() ? location.getSpeed() : segmentSpeedMps;
        this.lastValidatedSpeedMps = Math.min(ABSOLUTE_MAX_SPEED_MPS, 0.6f * segmentSpeedMps + 0.4f * dopplerSpeed);
        this.lastBearing = currentBearing;
        pruneSpikesAndRecalculateDistance();
    }

    private void updateStateFromTail() {
        int n = puntos.size();
        if (n >= 2) {
            Location prev = puntos.get(n - 2);
            Location last = puntos.get(n - 1);
            this.lastStationaryTimeMs = last.getTime();
            long dtMs = Math.max(500L, Math.abs(last.getTime() - prev.getTime()));
            float segSpeed = prev.distanceTo(last) / (dtMs / 1000.0f);
            this.lastValidatedSpeedMps = Math.min(ABSOLUTE_MAX_SPEED_MPS, segSpeed);
            this.lastBearing = (last.hasBearing() && last.getSpeed() > 1.0f) ? last.getBearing() : prev.bearingTo(last);
        }
    }

    /**
     * Evalúa si la transición entre 'from' y 'to' es físicamente plausible usando:
     * 1. Techo absoluto de velocidad (162 km/h).
     * 2. Límite adaptativo según velocidad anterior + aceleración física máxima.
     * 3. Si supera 80 km/h: exige desvío acotado respecto a velocidad previa y cono de rumbo estrecho.
     * 4. Coherencia con velocidad Doppler del chip GPS (location.getSpeed()).
     * 5. Penalización de saltos grandes durante apagones largos de señal (> 12s).
     */
    private boolean isTransitionPlausible(
            Location from,
            Location to,
            float distMeters,
            float dtSec,
            float impliedSpeedMps,
            float prevSpeedMps,
            Float prevBearing
    ) {
        if (dtSec <= 0.0f || impliedSpeedMps > ABSOLUTE_MAX_SPEED_MPS) {
            return false;
        }

        if (to.hasSpeed() && to.getSpeed() > ABSOLUTE_MAX_SPEED_MPS) {
            return false;
        }

        // 1. Límite de velocidad adaptativo por aceleración máxima física
        float effectiveAccelDt = Math.min(dtSec, MAX_EFFECTIVE_ACCEL_DT_SEC);
        float baselineSpeed = Math.max(prevSpeedMps, BASE_URBAN_SPEED_MPS);
        float adaptiveMaxSpeed = Math.min(ABSOLUTE_MAX_SPEED_MPS, baselineSpeed + MAX_ACCEL_MPS2 * effectiveAccelDt);

        if (impliedSpeedMps > adaptiveMaxSpeed) {
            return false;
        }

        // 2. Régimen de alta velocidad (> 80 km/h = 22.2 m/s):
        // Exige consistencia con la velocidad previa (desvío máximo) y giro suave de autopista
        if (impliedSpeedMps > HIGH_SPEED_THRESHOLD_MPS) {
            float maxHighSpeedAllowed = Math.max(
                    HIGH_SPEED_THRESHOLD_MPS + MAX_ACCEL_MPS2 * Math.min(dtSec, 4.0f),
                    prevSpeedMps * (1.0f + HIGH_SPEED_MAX_DEVIATION_RATIO)
            );
            if (impliedSpeedMps > maxHighSpeedAllowed) {
                return false;
            }

            if (prevBearing != null) {
                float segBearing = from.bearingTo(to);
                float turnDeg = angleDiff(segBearing, prevBearing);
                float maxAllowedTurn = Math.min(75.0f, HIGH_SPEED_MAX_TURN_RATE_DEG_SEC * Math.max(1.0f, Math.min(dtSec, 3.0f)));
                if (turnDeg > maxAllowedTurn) {
                    return false;
                }
            }
        }

        // 3. Validación cruzada con velocidad Doppler reportada por el GPS
        if (to.hasSpeed() && impliedSpeedMps > 15.0f) {
            float dopplerSpeed = to.getSpeed();
            // Si el desplazamiento implica > 54 km/h pero el sensor Doppler reporta menos de la mitad, es un salto de posición
            if (dopplerSpeed < impliedSpeedMps * 0.45f && (impliedSpeedMps - dopplerSpeed) > 8.0f) {
                return false;
            }
        }

        // 4. Apagón de señal prolongado + salto grande (> 200 m) con giro brusco respecto a la dirección previa
        if (distMeters > 200.0f && prevBearing != null) {
            float segBearing = from.bearingTo(to);
            float turnDeg = angleDiff(segBearing, prevBearing);
            if (turnDeg > 100.0f && impliedSpeedMps > 12.0f) {
                return false;
            }
        }

        return true;
    }

    private boolean isCandidateChainCoherent(List<Location> chain) {
        if (chain.size() < 3) {
            return false;
        }
        Location p0 = chain.get(0);
        Location p1 = chain.get(1);
        Location p2 = chain.get(2);

        float d01 = p0.distanceTo(p1);
        float d12 = p1.distanceTo(p2);
        if (d01 < MIN_DIST_STRAIGHT_METERS || d12 < MIN_DIST_STRAIGHT_METERS) {
            return false;
        }

        float b01 = p0.bearingTo(p1);
        float b12 = p1.bearingTo(p2);
        // Los candidatos deben avanzar en una dirección consistente (sin zig-zag)
        return angleDiff(b01, b12) <= 45.0f;
    }

    private static float angleDiff(float b1, Float b2) {
        if (b2 == null) {
            return 0.0f;
        }
        float diff = Math.abs(b1 - b2) % 360.0f;
        return diff > 180.0f ? 360.0f - diff : diff;
    }

    /**
     * Podado retroactivo de picos (spikes), excursiones de teletransportación y bucles de ruido GPS,
     * recalculando `this.distancia` sobre la polilínea depurada.
     */
    public void pruneSpikesAndRecalculateDistance() {
        if (this.puntos == null || this.puntos.size() < 3) {
            this.distancia = calculateTotalDistance(this.puntos);
            return;
        }

        boolean changed = true;
        while (changed && this.puntos.size() >= 3) {
            changed = false;

            // 1. Podado por ventana deslizante (k de 2 a 8) para excursiones de salto GPS y bucles de esquina
            int n = this.puntos.size();
            for (int i = 0; i < n - 2; i++) {
                int maxK = Math.min(8, n - 1 - i);
                boolean pruned = false;
                for (int k = maxK; k >= 2; k--) {
                    Location start = this.puntos.get(i);
                    Location end = this.puntos.get(i + k);
                    float directDist = start.distanceTo(end);

                    float pathDist = 0.0f;
                    float maxSeg = 0.0f;
                    for (int j = i; j < i + k; j++) {
                        float seg = this.puntos.get(j).distanceTo(this.puntos.get(j + 1));
                        pathDist += seg;
                        if (seg > maxSeg) {
                            maxSeg = seg;
                        }
                    }

                    if (pathDist <= 20.0f) {
                        continue;
                    }

                    boolean isJitterLoop = directDist < 45.0f && pathDist > Math.max(35.0f, 2.2f * directDist);
                    boolean isTeleportExcursion = maxSeg > 220.0f && pathDist > 2.8f * Math.max(directDist, 25.0f);

                    if (isJitterLoop || isTeleportExcursion) {
                        this.puntos.subList(i + 1, i + k).clear();
                        changed = true;
                        pruned = true;
                        break;
                    }
                }
                if (pruned) {
                    break;
                }
            }

            if (changed) {
                continue;
            }

            // 2. Podado de picos agudos individuales (A -> B -> C con inversión de rumbo > 135°)
            for (int i = 1; i < this.puntos.size() - 1; i++) {
                Location a = this.puntos.get(i - 1);
                Location b = this.puntos.get(i);
                Location c = this.puntos.get(i + 1);

                float dAB = a.distanceTo(b);
                float dBC = b.distanceTo(c);
                float dAC = a.distanceTo(c);

                if (dAB > 5.0f && dBC > 5.0f) {
                    float b1 = a.bearingTo(b);
                    float b2 = b.bearingTo(c);
                    float turn = angleDiff(b1, b2);
                    if (turn > 135.0f && (dAB + dBC) > 1.7f * Math.max(dAC, 5.0f)) {
                        this.puntos.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
        }

        this.distancia = calculateTotalDistance(this.puntos);
    }

    private static double calculateTotalDistance(List<Location> pts) {
        if (pts == null || pts.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            total += pts.get(i - 1).distanceTo(pts.get(i));
        }
        return total;
    }

    public Location getUltimoPunto() {
        if (puntos.isEmpty()) {
            return null;
        }
        return puntos.get(puntos.size() - 1);
    }

    public void simplificarRuta() {
        pruneSpikesAndRecalculateDistance();
        if (this.puntos != null && this.puntos.size() > 2) {
            this.puntos = simplify(this.puntos, SIMPLIFY_EPSILON_METERS);
            this.distancia = calculateTotalDistance(this.puntos);
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
