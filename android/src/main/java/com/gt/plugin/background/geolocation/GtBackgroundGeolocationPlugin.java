package com.gt.plugin.background.geolocation;

import static android.content.Context.POWER_SERVICE;

import android.app.ActivityManager;
import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;

@CapacitorPlugin(
        name = "GtBackgroundGeolocation",
        permissions = {
                @Permission(strings = {Manifest.permission.ACCESS_FINE_LOCATION},
                        alias = "fineLocation"),
                @Permission(strings = {Manifest.permission.ACCESS_COARSE_LOCATION},
                        alias = "coarseLocation"),
                @Permission(strings = {Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        alias = "backgroundLocation")
        })
public class GtBackgroundGeolocationPlugin extends Plugin {

    private static final String TAG = "GtBackgroundGeolocation";
    private final GtBackgroundGeolocationConfig config = new GtBackgroundGeolocationConfig();
    @Nullable
    private PowerManager.WakeLock activeWakeLock;

    @PluginMethod
    public void configure(PluginCall call) {
        config.setNotificationTitle(call.getString("title"));
        config.setNotificationText(call.getString("text"));
        config.setUrl(call.getString("url"));

        config.setIcon(call.getString("icon"));
        config.setMessageTemplate(call.getString("messageTemplate"));
        var interval = call.getLong("interval");
        if (interval == null) {
            interval = 10 * 1000L;
        }
        config.setInterval(interval);

        var maxInterval = call.getLong("maxInterval");
        if (maxInterval == null) {
            maxInterval = 15 * 60 * 1000L;
        }
        config.setMaxInterval(interval);

        config.setBearerToken(call.getString("bearerToken"));

        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            call.reject("A URL must be provided.");
            return;
        }
        if (config.getBearerToken() == null || config.getBearerToken().isEmpty()) {
            call.reject("A Bearer token must be provided.");
            return;
        }
        if (config.getNotificationTitle() == null || config.getNotificationTitle().isEmpty()) {
            call.reject("A title must be provided for the notification.");
            return;
        }
        if (config.getNotificationText() == null || config.getNotificationText().isEmpty()) {
            call.reject("Text must be provided for the notification.");
            return;
        }

        Log.d(TAG, "Configured with URL: " + config.getUrl());
        call.resolve();
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (config.getUrl() == null) {
            call.reject("Plugin must be configured before starting. Call 'configure' first.");
            return;
        }

        if (getPermissionState("fineLocation") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("fineLocation", call, "start");
        } else {
            startService(call);
        }
    }

    @PermissionCallback
    private void start(PluginCall call, String callbackId) {
        if (getPermissionState("fineLocation") == com.getcapacitor.PermissionState.GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getPermissionState(
                    "backgroundLocation") != com.getcapacitor.PermissionState.GRANTED) {
                requestPermissionForAlias("backgroundLocation", call, "start");
            } else {
                startService(call);
            }
        } else {
            call.reject("Fine location permission is required.");
        }
    }

    private void startService(PluginCall call) {

        if (isServiceRunning(LocationService.class)) {
            Log.d(TAG, "Location service is already running.");
            call.resolve();
            return;
        }


        acquireWakeLock();

        Intent serviceIntent = new Intent(getContext(), LocationService.class);
        serviceIntent.putExtra("url", config.getUrl());
        serviceIntent.putExtra("title", config.getNotificationTitle());
        serviceIntent.putExtra("text", config.getNotificationText());
        serviceIntent.putExtra("icon", config.getIcon());
        serviceIntent.putExtra("messageTemplate", config.getMessageTemplate());
        serviceIntent.putExtra("bearerToken", config.getBearerToken());
        serviceIntent.putExtra("interval", config.getInterval());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(serviceIntent);
        } else {
            getContext().startService(serviceIntent);
        }
        Log.d(TAG, "Location service started.");
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {

        releaseWakeLock();

        Intent serviceIntent = new Intent(getContext(), LocationService.class);
        getContext().stopService(serviceIntent);
        Log.d(TAG, "Location service stopped.");
        call.resolve();
    }

    @PluginMethod
    public void getCurrentPosition(PluginCall call) {
        if (getPermissionState("fineLocation") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("fineLocation", call, "getCurrentPositionCallback");
        } else {
            sendCurrentPosition(call);
        }
    }

    @PermissionCallback
    private void getCurrentPositionCallback(PluginCall call) {
        if (getPermissionState("fineLocation") == com.getcapacitor.PermissionState.GRANTED) {
            sendCurrentPosition(call);
        } else {
            call.reject("Location permission is required to get the current position.");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void sendCurrentPosition(PluginCall call) {

        // Crea una instancia de CancellationTokenSource
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        CancellationToken cancellationToken = cancellationTokenSource.getToken();

        FusedLocationProviderClient fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(getContext());
        fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)
                .addOnSuccessListener(
                        location -> {
                            if (location != null) {
                                JSObject ret = new JSObject();
                                ret.put("latitude", location.getLatitude());
                                ret.put("longitude", location.getLongitude());
                                ret.put("accuracy", location.getAccuracy());
                                ret.put("speed", location.getSpeed());
                                ret.put("altitude", location.getAltitude());
                                ret.put("time", location.getTime());
                                call.resolve(ret);
                            } else {
                                call.reject("Unable to get current location.");
                            }
                        })
                .addOnFailureListener(e -> call.reject("Failed to get location.", e));
    }

    private void acquireWakeLock() {
        if (activeWakeLock != null) {
            return;
        }
        PowerManager powerManager = (PowerManager) getContext().getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CapacitorAndroidForegroundService::Wakelock");
        wakeLock.acquire();
        activeWakeLock = wakeLock;
    }

    private void releaseWakeLock() {
        if (activeWakeLock == null) {
            return;
        }
        activeWakeLock.release();
        activeWakeLock = null;
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getContext().getSystemService(getContext().ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
