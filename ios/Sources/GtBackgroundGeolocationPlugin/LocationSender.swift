import Foundation
import CoreLocation
import UIKit

public class LocationSender {
    public static func send(
        url backendUrl: String,
        bearerToken: String,
        messageTemplate: String,
        location: CLLocation,
        completion: ((Bool, Int?) -> Void)? = nil
    ) {
        guard let url = URL(string: backendUrl) else {
            completion?(false, nil)
            return
        }

        var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "LocationSenderBackgroundTask") {
            if backgroundTaskID != .invalid {
                UIApplication.shared.endBackgroundTask(backgroundTaskID)
                backgroundTaskID = .invalid
            }
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")

        let payloadString = LocationPayloadBuilder.createPayload(template: messageTemplate, location: location)
        request.httpBody = payloadString.data(using: .utf8)

        let task = URLSession.shared.dataTask(with: request) { _, response, error in
            defer {
                if backgroundTaskID != .invalid {
                    UIApplication.shared.endBackgroundTask(backgroundTaskID)
                    backgroundTaskID = .invalid
                }
            }

            if let error = error {
                print("[LocationSender] Error sending location: \(error.localizedDescription)")
                completion?(false, nil)
                return
            }

            let httpResponse = response as? HTTPURLResponse
            let statusCode = httpResponse?.statusCode
            let success = (statusCode ?? 0) >= 200 && (statusCode ?? 0) < 300
            completion?(success, statusCode)
        }

        task.resume()
    }
}
