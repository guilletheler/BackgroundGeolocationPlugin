package com.gt.plugin.background.geolocation;

import android.location.Location;

import androidx.annotation.NonNull;

import com.getcapacitor.JSObject;

import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class LocationPayloadBuilder {

    @NonNull
    public static String createPayload(String messageTemplate, Location location) throws JSONException {
        String jsonPayload;

        // Format the time as an ISO 8601 string
        SimpleDateFormat isoFormat =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
        String isoTime = isoFormat.format(location.getTime());

        jsonPayload = messageTemplate
                .replace("{latitude}", String.valueOf(location.getLatitude()))
                .replace("{longitude}", String.valueOf(location.getLongitude()))
                .replace("{accuracy}", String.valueOf(location.getAccuracy()))
                .replace("{speed}", String.valueOf(location.getSpeed()))
                .replace("{altitude}", String.valueOf(location.getAltitude()))
                .replace("{time}", isoTime);

        return jsonPayload;
    }

    @NonNull
    public static JSObject locationToJson(Location location) {
        JSObject ret = new JSObject();
        ret.put("latitude", location.getLatitude());
        ret.put("longitude", location.getLongitude());
        ret.put("accuracy", location.getAccuracy());
        ret.put("speed", location.getSpeed());
        ret.put("altitude", location.getAltitude());
        ret.put("time", location.getTime());
        return ret;
    }
}
