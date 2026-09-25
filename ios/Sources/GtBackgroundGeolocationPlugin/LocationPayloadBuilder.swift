import Foundation
import CoreLocation

public class LocationPayloadBuilder {
    private static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        return formatter
    }()

    public static func createPayload(template: String, location: CLLocation) -> String {
        let isoTime = isoFormatter.string(from: location.timestamp)
        let speed = max(0.0, location.speed)

        return template
            .replacingOccurrences(of: "{latitude}", with: String(location.coordinate.latitude))
            .replacingOccurrences(of: "{longitude}", with: String(location.coordinate.longitude))
            .replacingOccurrences(of: "{accuracy}", with: String(location.horizontalAccuracy))
            .replacingOccurrences(of: "{speed}", with: String(speed))
            .replacingOccurrences(of: "{altitude}", with: String(location.altitude))
            .replacingOccurrences(of: "{time}", with: isoTime)
    }

    public static func locationToDictionary(_ location: CLLocation) -> [String: Any] {
        return [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "accuracy": location.horizontalAccuracy,
            "speed": max(0.0, location.speed),
            "altitude": location.altitude,
            "time": Int64(location.timestamp.timeIntervalSince1970 * 1000)
        ]
    }
}
