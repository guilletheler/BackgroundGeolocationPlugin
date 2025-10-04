package com.gt.plugin.background.geolocation;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.getcapacitor.Logger;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class LocationService extends Service {
    private static final String TAG = "LocationService";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private String postUrl;
    private String bearerToken;
    private String notificationTitle;
    private String notificationText;
    private String messageTemplate;
    private int minDist = 50;
    private long interval = 10000;
    private long maxInterval;
    private int iconResId = android.R.drawable.ic_menu_mylocation;

    private Location lastLocation;

    private boolean isTripActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        sendLocation(location);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        setConfigFromIntent(intent);

        findIconResId(intent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, createNotification());
        }
        startLocationUpdates();

        return START_STICKY;
    }

    private void setConfigFromIntent(Intent intent) {
        postUrl = intent.getStringExtra("url");
        notificationTitle = intent.getStringExtra("title");
        notificationText = intent.getStringExtra("text");
        messageTemplate = intent.getStringExtra("messageTemplate");

        bearerToken = intent.getStringExtra("bearerToken");
        interval = intent.getLongExtra("interval", 10000);
        maxInterval = intent.getLongExtra("maxInterval", 15 * 60 * 1000);
        minDist = intent.getIntExtra("minDist", 50);
    }

    private void findIconResId(Intent intent) {
        String notificationIcon = intent.getStringExtra("icon");
        if (notificationIcon != null && !notificationIcon.isEmpty()) {
            @SuppressLint("DiscouragedApi")
            int resourceId = getApplicationContext().getResources().getIdentifier(notificationIcon,
                    "drawable", getApplicationContext().getPackageName());
            if (resourceId != 0) {
                iconResId = resourceId;
            } else {
                Logger.warn("Could not find notification icon: " + notificationIcon);
                iconResId = android.R.drawable.ic_menu_mylocation;
            }
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest =
                new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, this.interval)
                        .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted.", e);
            stopSelf();
        }
    }

    private void sendLocation(Location location) {
        if (postUrl == null || postUrl.isEmpty()) {
            return;
        }

        if (bearerToken == null || bearerToken.isEmpty()) {
            Log.e(TAG, "Wrong config, bearer token is null");
            return;
        }

        if (location == null) {
            Log.e(TAG, "Location is null");
            return;
        }

        if (lastLocation != null) {
            if (location.getTime() - lastLocation.getTime() < maxInterval) {
                return;
            }
            float dist = distance(lastLocation, location);

            if (dist < 50) {
                return;
            }
        }

        lastLocation = location;

        new Thread(() -> {
            try {
                HttpURLConnection conn = getHttpURLConnection();

                String jsonPayload;

                // Format the time as an ISO 8601 string
                SimpleDateFormat isoFormat =
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                String isoTime = isoFormat.format(location.getTime());

                if (messageTemplate != null && !messageTemplate.isEmpty()) {
                    jsonPayload = messageTemplate
                            .replace("{latitude}", String.valueOf(location.getLatitude()))
                            .replace("{longitude}", String.valueOf(location.getLongitude()))
                            .replace("{accuracy}", String.valueOf(location.getAccuracy()))
                            .replace("{speed}", String.valueOf(location.getSpeed()))
                            .replace("{altitude}", String.valueOf(location.getAltitude()))
                            .replace("{time}", isoTime);
                } else {
                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("latitude", location.getLatitude());
                    jsonParam.put("longitude", location.getLongitude());
                    jsonParam.put("accuracy", location.getAccuracy());
                    jsonParam.put("speed", location.getSpeed());
                    jsonParam.put("altitude", location.getAltitude());
                    jsonParam.put("time", isoTime);
                    jsonPayload = jsonParam.toString();
                }

                Log.d(TAG, "Sending location to back: lat:" + location.getLatitude() + ", long: "
                        + location.getLongitude());

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();

                Log.d(TAG, "Server response: " + code);

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending location", e);
            }
        }).start();
    }

    @NonNull
    private HttpURLConnection getHttpURLConnection() throws IOException {
        URL url = new URL(postUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        conn.setDoOutput(true);
        return conn;
    }

    private Notification createNotification() {
        String channelId = "location_service_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    channelId,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setSmallIcon(iconResId)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private float distance(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            // Depending on your logic, you might want to return a specific value
            // or handle this case differently. Returning a large value if one is null.
            return Float.MAX_VALUE;
        }
        return loc1.distanceTo(loc2);
    }


}
