package com.gt.plugin.background.geolocation;

import static android.content.Context.POWER_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private LocationService myServiceInstance;
    private boolean isBound = false;
    // Objeto que maneja la conexión con el Service
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Obtenemos el Binder y luego la instancia del Service
            LocationService.LocationServiceBinder binder =
                    (LocationService.LocationServiceBinder) service;
            myServiceInstance = binder.getService();
            isBound = true;
            Log.d(TAG, "Plugin: Conectado al servicio vía IBinder.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            myServiceInstance = null;
            Log.d(TAG, "Plugin: Desconectado del servicio.");
        }
    };

    @Nullable
    private PowerManager.WakeLock activeWakeLock;

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject ret = new JSObject();
        if (!isConfigured()) {
            ret.put("status", "UNCONFIGURED");
        } else if (isServiceRunning(LocationService.class)) {
            ret.put("status", "STARTED");
        } else {
            ret.put("status", "STOPPED");
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void configure(PluginCall call) {
        config.setNotificationTitle(call.getString("title"));
        config.setNotificationText(call.getString("text"));
        config.setUrl(call.getString("url"));

        config.setIcon(call.getString("icon"));
        config.setMessageTemplate(call.getString("messageTemplate"));
        Long interval = call.getLong("interval", 10 * 1000L);
        config.setInterval(Objects.requireNonNull(interval));

        Long maxInterval = call.getLong("maxInterval", 15 * 60 * 1000L);
        config.setMaxInterval(Objects.requireNonNull(maxInterval));

        Integer minDist = call.getInt("minDist", 50);
        config.setMinDist(Objects.requireNonNull(minDist));

        config.setBearerToken(call.getString("bearerToken"));

        String configError = validateConfig(config);
        if (!configError.isEmpty()) {
            call.reject(configError);
            return;
        }

        Log.d(TAG, "Configured with URL: " + config.getUrl());
        call.resolve();
    }

    private String validateConfig(GtBackgroundGeolocationConfig config) {

        List<String> errors = new ArrayList<>();

        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            errors.add("A URL must be provided.");
        }
        if (config.getBearerToken() == null || config.getBearerToken().isEmpty()) {
            errors.add("A Bearer token must be provided.");
        }
        if (config.getNotificationTitle() == null || config.getNotificationTitle().isEmpty()) {
            errors.add("A title must be provided for the notification.");
        }
        if (config.getNotificationText() == null || config.getNotificationText().isEmpty()) {
            errors.add("Text must be provided for the notification.");
        }

        StringBuilder sb = new StringBuilder();
        for (String error : errors) {
            sb.append(error).append(", ");
        }
        return sb.toString();
    }

    @PluginMethod
    public void start(PluginCall call) {
        Log.d(TAG, "Request Start the service.");
        if (!isConfigured()) {
            call.reject("Plugin must be configured before starting. Call 'configure' first.");
            return;
        }
        startService(call);
    }

    private boolean isConfigured() {
        return config.getUrl() != null
                && !config.getUrl().isEmpty()
                && config.getBearerToken() != null
                && !config.getBearerToken().isEmpty()
                && config.getMessageTemplate() != null
                && !config.getMessageTemplate().isEmpty();
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        Boolean[] toRequest = resolvePermissionsToRequest(call);
        if (!toRequest[0] && !toRequest[1]) {
            call.reject("Location permission is required to start the service.", "NOT_AUTHORIZED");
            return;
        }

        if (getPermissionState("fineLocation") != com.getcapacitor.PermissionState.GRANTED) {
            String callBack = toRequest[1] ? "backgroundLocationCallback" : "fineLocationCallback";
            requestPermissionForAlias("fineLocation", call, callBack);
            return;
        }

        if (toRequest[1]) {
            backgroundLocationCallback(call);
            return;
        }

        call.resolve();


    }

    /**
     * Devuelve un array de boolean
     *
     * @param call PluginCall
     * @return Un array de boolean en donde el primer valor es fineLocation y el segundo
     * backgroundLocation
     */
    private Boolean[] resolvePermissionsToRequest(PluginCall call) {
        Boolean[] toRequest = new Boolean[]{
                call.getBoolean("fineLocation"),
                call.getBoolean("backgroundLocation")};

        if (toRequest[0] == null && toRequest[1] == null) {
            toRequest[0] = true;
            toRequest[1] = true;
        }

        if (toRequest[1] == null) {
            toRequest[1] = false;
        }
        if (toRequest[0] == null) {
            toRequest[0] = false;
        }
        return toRequest;
    }

    @PermissionCallback
    private void fineLocationCallback(PluginCall call) {
        call.resolve();
    }

    @PermissionCallback
    private void backgroundLocationCallback(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && getPermissionState(
                "backgroundLocation") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("backgroundLocation", call, "openSettingsCallback");
            return;
        }

        call.resolve();
    }

    @PermissionCallback
    private void openSettingsCallback(PluginCall call) {
        call.resolve();
    }

    private void startService(PluginCall call) {

        Log.d(TAG, "Starting the service.");

        if (!hasBackgroundPermissions()) {
            Log.e(TAG, "Background location permission is required to start the service.");
            call.reject("Background location permission is required to start the service.",
                    "NOT_AUTHORIZED");
            return;
        }

        if (isServiceRunning(LocationService.class)) {
            Log.d(TAG, "Location service is already running.");
            call.resolve();
            return;
        }

        acquireWakeLock();

        Intent serviceIntent = getServiceIntent();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(serviceIntent);
        } else {
            getContext().startService(serviceIntent);
        }
        Log.d(TAG, "Location service started.");

        this.connectService();

        call.resolve();
    }

    @NonNull
    private Intent getServiceIntent() {
        Intent serviceIntent = new Intent(getContext(), LocationService.class);
        serviceIntent.putExtra("url", config.getUrl());
        serviceIntent.putExtra("title", config.getNotificationTitle());
        serviceIntent.putExtra("text", config.getNotificationText());
        serviceIntent.putExtra("icon", config.getIcon());
        serviceIntent.putExtra("messageTemplate", config.getMessageTemplate());
        serviceIntent.putExtra("bearerToken", config.getBearerToken());
        serviceIntent.putExtra("interval", config.getInterval());
        serviceIntent.putExtra("minDist", config.getMinDist());
        serviceIntent.putExtra("maxInterval", config.getMaxInterval());
        return serviceIntent;
    }

    @PluginMethod
    public void stop(PluginCall call) {

        releaseWakeLock();
        this.disconnectService();

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
            returnCurrentPosition(call);
        }
    }

    @PermissionCallback
    private void getCurrentPositionCallback(PluginCall call) {
        if (getPermissionState("fineLocati on") == com.getcapacitor.PermissionState.GRANTED) {
            returnCurrentPosition(call);
        } else {
            call.reject("Location permission is required to get the current position.",
                    "NOT_AUTHORIZED");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void returnCurrentPosition(PluginCall call) {

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
                                JSObject ret = LocationPayloadBuilder.locationToJson(location);
                                call.resolve(ret);
                            } else {
                                call.reject("Unable to get current location.");
                            }
                        })
                .addOnFailureListener(e -> call.reject("Failed to get location.", e));
    }

    @SuppressLint("WakelockTimeout")
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
        try {
            ActivityManager manager =
                    (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager
                    .getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking service status", e);
        }
        return false;
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("fineLocation", hasLocationPermissions());
        ret.put("backgroundLocation", hasBackgroundPermissions());
        call.resolve(ret);
    }

    private boolean hasLocationPermissions() {
        return getPermissionState("fineLocation") == com.getcapacitor.PermissionState.GRANTED ||
                getPermissionState(
                        "coarseLocation") == com.getcapacitor.PermissionState.GRANTED;
    }

    private boolean hasBackgroundPermissions() {

        boolean ret = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ret = getPermissionState("fineLocation") == com.getcapacitor.PermissionState.GRANTED
                    && getPermissionState(
                    "backgroundLocation") == com.getcapacitor.PermissionState.GRANTED;

        } else if (hasLocationPermissions()) {
            ret = getPermissionState("fineLocation") == com.getcapacitor.PermissionState.GRANTED;
        }

        return ret;
    }

    private void connectService() {
        if (this.isBound) {
            return;
        }
        Intent intent = new Intent(getContext(), LocationService.class);
        getContext().bindService(intent, connection, 0);
    }

    private void disconnectService() {
        if (isBound) {
            getContext().unbindService(connection);
            isBound = false;
        }
    }

    @Override
    protected void handleOnStop() {
        this.disconnectService();
        super.handleOnStop();
    }

    @PluginMethod
    public void initTrip(PluginCall call) {
        if (this.myServiceInstance != null) {
            this.myServiceInstance.initTrip();
        }
        call.resolve();
    }

    @PluginMethod
    public void getTripDistance(PluginCall call) {
        if (testLocationServiceConnection(call)) {
            return;
        }
        JSObject ret = Trip.toJSObject(this.myServiceInstance.getCurrentTrip());

        call.resolve(ret);
    }

    @PluginMethod
    public void endTrip(PluginCall call) {
        if (testLocationServiceConnection(call)) {
            return;
        }
        Trip trip = this.myServiceInstance.endTrip();
        call.resolve(Trip.toJSObject(trip, true));
    }

    private boolean testLocationServiceConnection(PluginCall call) {
        if (this.myServiceInstance == null) {
            this.connectService();
            try {
                Thread.sleep(200);
                if (this.myServiceInstance == null) {
                    Log.e(TAG, "No se puede conectar al servicio");
                    call.reject("No se puede conectar al servicio");
                    return true;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error esperando se conecte el servicio");
                call.reject("Error esperando se conecte el servicio");
                return true;
            }
        }
        return false;
    }
}
