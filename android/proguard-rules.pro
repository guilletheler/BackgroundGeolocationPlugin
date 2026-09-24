# Preserve the LocationService class name and members as it's referenced by name
-keep class com.gt.plugin.background.geolocation.LocationService { *; }

# Preserve Capacitor Plugin classes
-keep public class * extends com.getcapacitor.Plugin
-keep public class * extends com.getcapacitor.annotation.CapacitorPlugin

-keep class com.gt.plugin.background.geolocation.** { *; }

