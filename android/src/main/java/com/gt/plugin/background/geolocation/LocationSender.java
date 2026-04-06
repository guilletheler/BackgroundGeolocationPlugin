package com.gt.plugin.background.geolocation;

import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LocationSender implements Runnable {

    private static final String TAG = "LocationSender";

    Location location;
    String backendUrl;
    String messageTemplate;
    String bearerToken;
    int responseCode = -1;

    public LocationSender(String backendUrl, String bearerToken, String messageTemplate,
            Location location) {
        this.backendUrl = backendUrl;
        this.bearerToken = bearerToken;
        this.location = location;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public void run() {

        HttpURLConnection conn = null;

        try {
            conn = getHttpURLConnection();
            sendLocationToBack(conn, location);
        } catch (Exception e) {
            Log.e(TAG, "Error sending location", e);
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }
    }

    private void sendLocationToBack(HttpURLConnection conn, Location location) throws IOException, JSONException {

        Log.d(TAG, "Sending location to back: lat:" + location.getLatitude() + ", long: "
                + location.getLongitude());

        String jsonPayload = LocationPayloadBuilder.createPayload(messageTemplate, location);

        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(input, 0, input.length);
            responseCode = conn.getResponseCode();
        }

        Log.d(TAG, "Server response: " + responseCode);

    }

    @NonNull
    private HttpURLConnection getHttpURLConnection() throws IOException {
        URL url = new URL(backendUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        conn.setDoOutput(true);
        return conn;
    }
}
