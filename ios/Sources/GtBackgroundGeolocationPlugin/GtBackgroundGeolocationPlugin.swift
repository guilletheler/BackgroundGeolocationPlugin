import Foundation
import CoreLocation
import UserNotifications
import Capacitor

@objc(GtBackgroundGeolocationPlugin)
public class GtBackgroundGeolocationPlugin: CAPPlugin, CAPBridgedPlugin, CLLocationManagerDelegate {
    public let identifier = "GtBackgroundGeolocationPlugin"
    public let jsName = "GtBackgroundGeolocation"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentPosition", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "initTrip", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getTripDistance", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "endTrip", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isIgnoringBatteryOptimizations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestIgnoreBatteryOptimizations", returnType: CAPPluginReturnPromise)
    ]

    private let service = GtBackgroundGeolocationService.shared
    private let permissionLocationManager = CLLocationManager()
    private var pendingPermissionCall: CAPPluginCall?

    override public func load() {
        super.load()
        permissionLocationManager.delegate = self
    }

    private var currentAuthorizationStatus: CLAuthorizationStatus {
        if #available(iOS 14.0, *) {
            return permissionLocationManager.authorizationStatus
        } else {
            return CLLocationManager.authorizationStatus()
        }
    }

    private var hasFineLocationPermission: Bool {
        let status = currentAuthorizationStatus
        return status == .authorizedWhenInUse || status == .authorizedAlways
    }

    private var hasBackgroundLocationPermission: Bool {
        return currentAuthorizationStatus == .authorizedAlways
    }

    // MARK: - Plugin Methods

    @objc func getStatus(_ call: CAPPluginCall) {
        let status: String
        if !service.isConfigured {
            status = "UNCONFIGURED"
        } else if service.isRunning {
            status = "STARTED"
        } else {
            status = "STOPPED"
        }
        call.resolve(["status": status])
    }

    @objc func configure(_ call: CAPPluginCall) {
        let title = call.getString("title")
        let text = call.getString("text")
        let url = call.getString("url")
        let bearerToken = call.getString("bearerToken")
        let messageTemplate = call.getString("messageTemplate")
        let minDist = call.getInt("minDist", 50)

        let sensorInterval: Int64
        if let intervalNumber = call.options["sensorInterval"] as? NSNumber {
            sensorInterval = intervalNumber.int64Value
        } else {
            sensorInterval = 10000
        }

        let heartbeatInterval: Int64
        if let heartbeatNumber = call.options["heartbeatInterval"] as? NSNumber {
            heartbeatInterval = heartbeatNumber.int64Value
        } else {
            heartbeatInterval = 15 * 60 * 1000
        }

        var errors: [String] = []
        if url == nil || url?.isEmpty == true {
            errors.append("A URL must be provided.")
        }
        if bearerToken == nil || bearerToken?.isEmpty == true {
            errors.append("A Bearer token must be provided.")
        }
        if title == nil || title?.isEmpty == true {
            errors.append("A title must be provided for the notification.")
        }
        if text == nil || text?.isEmpty == true {
            errors.append("Text must be provided for the notification.")
        }
        if messageTemplate == nil || messageTemplate?.isEmpty == true {
            errors.append("A messageTemplate must be provided.")
        }

        if !errors.isEmpty {
            call.reject(errors.joined(separator: ", "))
            return
        }

        service.configure(
            url: url,
            bearerToken: bearerToken,
            title: title,
            text: text,
            messageTemplate: messageTemplate,
            minDist: minDist,
            sensorInterval: sensorInterval,
            heartbeatInterval: heartbeatInterval
        )

        call.resolve()
    }

    @objc func start(_ call: CAPPluginCall) {
        if !service.isConfigured {
            call.reject("Plugin must be configured before starting. Call 'configure' first.")
            return
        }

        if !hasBackgroundLocationPermission {
            call.reject("Background location permission is required (Allow all the time).", "NOT_AUTHORIZED")
            return
        }

        service.start()
        call.resolve()
    }

    @objc func stop(_ call: CAPPluginCall) {
        service.stop()
        call.resolve()
    }

    @objc func getCurrentPosition(_ call: CAPPluginCall) {
        if !hasFineLocationPermission {
            call.reject("Location permission is required to get the current position.", "NOT_AUTHORIZED")
            return
        }

        service.requestCurrentLocation { result in
            switch result {
            case .success(let location):
                let ret = LocationPayloadBuilder.locationToDictionary(location)
                call.resolve(ret)
            case .failure(let error):
                call.reject("Failed to get location: \(error.localizedDescription)")
            }
        }
    }

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let notificationsGranted = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional

            call.resolve([
                "fineLocation": self.hasFineLocationPermission,
                "backgroundLocation": self.hasBackgroundLocationPermission,
                "notifications": notificationsGranted
            ])
        }
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        var reqFine = call.getBool("fineLocation")
        var reqBackground = call.getBool("backgroundLocation")

        if reqFine == nil && reqBackground == nil {
            reqFine = true
            reqBackground = true
        }
        let requestFine = reqFine ?? false
        let requestBackground = reqBackground ?? false

        if !requestFine && !requestBackground {
            call.reject("Location permission is required to start the service.", "NOT_AUTHORIZED")
            return
        }

        // Request notification permissions
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }

        let currentStatus = self.currentAuthorizationStatus

        if requestBackground {
            if currentStatus == .authorizedAlways {
                call.resolve()
                return
            }
            self.pendingPermissionCall = call
            DispatchQueue.main.async {
                self.permissionLocationManager.requestAlwaysAuthorization()
            }
        } else {
            if currentStatus == .authorizedWhenInUse || currentStatus == .authorizedAlways {
                call.resolve()
                return
            }
            self.pendingPermissionCall = call
            DispatchQueue.main.async {
                self.permissionLocationManager.requestWhenInUseAuthorization()
            }
        }
    }

    @objc func initTrip(_ call: CAPPluginCall) {
        service.initTrip()
        call.resolve()
    }

    @objc func getTripDistance(_ call: CAPPluginCall) {
        if let trip = service.getCurrentTrip() {
            call.resolve(trip.toDictionary(withPath: false))
        } else {
            call.resolve([:])
        }
    }

    @objc func endTrip(_ call: CAPPluginCall) {
        if let trip = service.endTrip() {
            call.resolve(trip.toDictionary(withPath: true))
        } else {
            call.resolve([:])
        }
    }

    @objc func isIgnoringBatteryOptimizations(_ call: CAPPluginCall) {
        call.resolve(["isIgnoring": true])
    }

    @objc func requestIgnoreBatteryOptimizations(_ call: CAPPluginCall) {
        call.resolve()
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = currentAuthorizationStatus
        guard status != .notDetermined else { return }

        if let pending = pendingPermissionCall {
            pendingPermissionCall = nil
            pending.resolve()
        }
    }

    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        guard status != .notDetermined else { return }

        if let pending = pendingPermissionCall {
            pendingPermissionCall = nil
            pending.resolve()
        }
    }
}
