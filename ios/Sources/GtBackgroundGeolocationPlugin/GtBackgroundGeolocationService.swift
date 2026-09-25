import Foundation
import CoreLocation
import UIKit

public class GtBackgroundGeolocationService: NSObject, CLLocationManagerDelegate {
    public static let shared = GtBackgroundGeolocationService()

    private let locationManager = CLLocationManager()

    public var postUrl: String?
    public var bearerToken: String?
    public var notificationTitle: String?
    public var notificationText: String?
    public var messageTemplate: String?
    public var minDist: Int = 50
    public var sensorInterval: Int64 = 10000
    public var heartbeatInterval: Int64 = 15 * 60 * 1000

    public private(set) var isRunning: Bool = false
    private var lastLocation: CLLocation?
    private var currentTrip: Trip?

    private var singleLocationCallbacks: [(Result<CLLocation, Error>) -> Void] = []

    override private init() {
        super.init()
        locationManager.delegate = self
    }

    public var isConfigured: Bool {
        guard let url = postUrl, !url.isEmpty,
              let token = bearerToken, !token.isEmpty,
              let template = messageTemplate, !template.isEmpty else {
            return false
        }
        return true
    }

    public func configure(
        url: String?,
        bearerToken: String?,
        title: String?,
        text: String?,
        messageTemplate: String?,
        minDist: Int?,
        sensorInterval: Int64?,
        heartbeatInterval: Int64?
    ) {
        self.postUrl = url
        self.bearerToken = bearerToken
        self.notificationTitle = title
        self.notificationText = text
        self.messageTemplate = messageTemplate

        if let minDist = minDist {
            self.minDist = minDist
        }
        if let sensorInterval = sensorInterval {
            self.sensorInterval = sensorInterval
        }
        if let heartbeatInterval = heartbeatInterval {
            self.heartbeatInterval = heartbeatInterval
        }
    }

    public func start() {
        guard !isRunning else { return }

        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = false
        if #available(iOS 11.0, *) {
            locationManager.showsBackgroundLocationIndicator = true
        }

        applyTrackingAccuracy()
        locationManager.startUpdatingLocation()
        isRunning = true
    }

    public func stop() {
        guard isRunning else { return }
        locationManager.stopUpdatingLocation()
        isRunning = false
    }

    public func initTrip() {
        if currentTrip != nil {
            print("[GtBackgroundGeolocationService] Resetting previous active trip")
        }
        currentTrip = Trip()
        if isRunning {
            applyTrackingAccuracy()
        }
    }

    public func getCurrentTrip() -> Trip? {
        return currentTrip
    }

    public func endTrip() -> Trip? {
        guard let trip = currentTrip else { return nil }
        trip.timestampFin = Int64(Date().timeIntervalSince1970 * 1000)
        currentTrip = nil

        if isRunning {
            applyTrackingAccuracy()
        }
        return trip
    }

    public func requestCurrentLocation(completion: @escaping (Result<CLLocation, Error>) -> Void) {
        if let loc = locationManager.location, abs(loc.timestamp.timeIntervalSinceNow) < 5.0 {
            completion(.success(loc))
            return
        }

        singleLocationCallbacks.append(completion)
        if isRunning {
            // Already updating, next location will resolve callbacks
        } else {
            locationManager.requestLocation()
        }
    }

    private func applyTrackingAccuracy() {
        if currentTrip != nil {
            locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
            locationManager.distanceFilter = kCLDistanceFilterNone
        } else {
            locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            locationManager.distanceFilter = CLLocationDistance(minDist)
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        // Filter invalid and excessively stale locations
        if location.horizontalAccuracy < 0 || location.timestamp.timeIntervalSinceNow < -15.0 {
            return
        }

        // Deliver single location requests if pending
        if !singleLocationCallbacks.isEmpty {
            let callbacks = singleLocationCallbacks
            singleLocationCallbacks.removeAll()
            callbacks.forEach { $0(.success(location)) }
        }

        guard isRunning else { return }

        processLocation(location)
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("[GtBackgroundGeolocationService] Location error: \(error.localizedDescription)")
        if !singleLocationCallbacks.isEmpty {
            let callbacks = singleLocationCallbacks
            singleLocationCallbacks.removeAll()
            callbacks.forEach { $0(.failure(error)) }
        }
    }

    private func processLocation(_ location: CLLocation) {
        processTrip(location)
        processSendLocation(location)
    }

    private func processTrip(_ location: CLLocation) {
        currentTrip?.addPunto(location)
    }

    private func processSendLocation(_ location: CLLocation) {
        if let last = lastLocation {
            let timeDiffMs = Int64(location.timestamp.timeIntervalSince(last.timestamp) * 1000)

            if currentTrip != nil {
                let dist = location.distance(from: last)
                if dist < Double(minDist) && timeDiffMs < heartbeatInterval {
                    return
                }
            } else {
                if timeDiffMs < heartbeatInterval {
                    return
                }
            }
        }

        lastLocation = location

        guard let url = postUrl, let token = bearerToken, let template = messageTemplate else {
            return
        }

        LocationSender.send(url: url, bearerToken: token, messageTemplate: template, location: location)
    }
}
