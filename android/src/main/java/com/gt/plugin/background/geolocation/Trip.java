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

        Location ultimo = this.getUltimoPunto();

        if (ultimo != null) {
            float curDist = ultimo.distanceTo(location);
            if (curDist > 30f) {
                this.distancia += curDist;
                puntos.add(location);
            } else {
                Log.d(TAG, "Punto ignorado: " + curDist);
            }
        } else {
            puntos.add(location);
        }
    }

    public Location getUltimoPunto() {
        if (puntos.isEmpty()) {
            return null;
        }

        return puntos.get(puntos.size() - 1);
    }
}
