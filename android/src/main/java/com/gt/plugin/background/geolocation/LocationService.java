package com.gt.plugin.background.geolocation;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.getcapacitor.Logger;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    // Instancia del Binder que se devuelve a los clientes (como nuestro Plugin)
    private final IBinder binder = new LocationServiceBinder();
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String postUrl;
    private String bearerToken;
    private String notificationTitle;
    private String notificationText;
    private String messageTemplate;
    private int minDist = 50;
    private long sensorInterval = 10000;
    private long heartbeatInterval;
    private int iconResId = android.R.drawable.ic_menu_mylocation;
    private Location lastLocation;
    private Trip currentTrip;
    private boolean fineTracking = false;
    private ExecutorService locationExecutor;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Servicio enlazado (onBind)");
        return binder;
    }

    // Si el Service no está enlazado, se detendrá.
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Servicio desenlazado (onUnbind).");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationExecutor = Executors.newSingleThreadExecutor();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        processLocation(location);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            setConfigFromIntent(intent);
            findIconResId(intent);
        }

        // Siempre debemos llamar a startForeground para evitar ForegroundServiceDidNotStartInTimeException
        // Especialmente en reinicios del sistema (donde el intent puede ser null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, createNotification());
        }

        if (intent != null) {
            startLocationUpdates();
        }

        return START_STICKY;
    }

    private void setConfigFromIntent(Intent intent) {

        if (intent == null) {
            Log.w(TAG, "Intent nulo configurando LocationService");
            return;
        }

        postUrl = intent.getStringExtra("url");
        notificationTitle = intent.getStringExtra("title");
        notificationText = intent.getStringExtra("text");
        messageTemplate = intent.getStringExtra("messageTemplate");

        bearerToken = intent.getStringExtra("bearerToken");
        sensorInterval = intent.getLongExtra("sensorInterval", 10000);
        heartbeatInterval = intent.getLongExtra("heartbeatInterval", 15 * 60 * 1000);
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
        boolean highAccuracy = this.fineTracking || (this.currentTrip != null);
        var priority = highAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        long useInterval;
        long minUpdateInterval;
        if (this.currentTrip != null) {
            useInterval = 1000;
            minUpdateInterval = 1000;
        } else if (this.fineTracking) {
            useInterval = Math.min(this.sensorInterval, 5000);
            minUpdateInterval = Math.min(useInterval, 3000);
        } else {
            useInterval = this.heartbeatInterval;
            minUpdateInterval = 5000;
        }

        Log.d(TAG, "Starting location updates. HighAccuracy: " + highAccuracy + " FineTracking: " + this.fineTracking + " Trip: " + (this.currentTrip != null) + " Interval: " + useInterval + " Priority: " + priority);

        LocationRequest locationRequest = new LocationRequest.Builder(priority, useInterval)
                .setMinUpdateIntervalMillis(minUpdateInterval)
                .setMinUpdateDistanceMeters(0)
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

    public void restartLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            startLocationUpdates();
        }
    }

    public void setFineTracking(boolean enabled) {
        if (this.fineTracking != enabled) {
            this.fineTracking = enabled;
            Log.d(TAG, "Fine tracking set to: " + enabled);
            restartLocationUpdates();
        }
    }

    public boolean isFineTracking() {
        return this.fineTracking;
    }

    private void processLocation(Location location) {
        processTrip(location);

        processSendLocation(location);
    }

    private void processSendLocation(Location location) {
        boolean highAccuracy = this.fineTracking || (this.currentTrip != null);
        if (highAccuracy && (!location.hasAccuracy() || location.getAccuracy() > 30.0f)) {
            Log.d(TAG, "Ubicación descartada para envío por baja precisión: " + (location.hasAccuracy() ? location.getAccuracy() + "m" : "sin accuracy"));
            return;
        }
        if (lastLocation != null) {
            long timeDiff = location.getTime() - lastLocation.getTime();

            if (highAccuracy) {
                // Durante un viaje o tracking fino, enviamos si superamos la distancia mínima O el intervalo latido (heartbeat)
                float dist = distance(lastLocation, location);
                if (dist < this.minDist && timeDiff < this.heartbeatInterval) {
                    return;
                }
            } else {
                // Sin viaje ni tracking fino, enviamos exactamente cada heartbeatInterval (ej. 15 min)
                if (timeDiff < this.heartbeatInterval) {
                    return;
                }
            }
        }

        lastLocation = location;

        if (locationExecutor != null && !locationExecutor.isShutdown()) {
            locationExecutor.execute(new LocationSender(postUrl, bearerToken, messageTemplate, location));
        }
    }

    private void processTrip(Location location) {
        if (this.currentTrip != null) {
            this.currentTrip.addPunto(location);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "location_service_channel",
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        String channelId = "location_service_channel";

        String title = (notificationTitle != null) ? notificationTitle : "Seguimiento de ubicación";
        String text = (notificationText != null) ? notificationText : "La aplicación está funcionando en segundo plano";

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(iconResId)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (locationExecutor != null) {
            locationExecutor.shutdown();
        }
    }

    private float distance(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            // Depending on your logic, you might want to return a specific value
            // or handle this case differently. Returning a large value if one is null.
            return Float.MAX_VALUE;
        }
        return loc1.distanceTo(loc2);
    }

    public Trip getCurrentTrip() {
        return this.currentTrip;
    }

    public Double getCurrentTripDistance() {
        if (this.currentTrip == null) {
            return 0.0;
        }
        return this.currentTrip.getDistancia();
    }

    public void initTrip() {
        if (this.currentTrip != null) {
            Log.w(TAG, "Trip anterior activo detectado, reiniciando para nuevo viaje");
            this.currentTrip = null;
        }
        Log.d(TAG, "Iniciando trip");
        this.currentTrip = new Trip();
        restartLocationUpdates();
    }

    public Trip endTrip() {
        Trip ret = null;
        if (currentTrip != null) {
            Log.d(TAG, "Finalizando trip distancia: " + this.currentTrip.getDistancia());
            ret = this.currentTrip;
            this.currentTrip = null;
            ret.setTimestampFin(System.currentTimeMillis());
            ret.simplificarRuta();
            restartLocationUpdates();
        }
        return ret;
    }

    // Clase anidada para exponer métodos del Servicio
    public class LocationServiceBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }
}
