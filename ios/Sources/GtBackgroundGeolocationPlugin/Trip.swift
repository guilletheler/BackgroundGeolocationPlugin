import Foundation
import CoreLocation

public class Trip {
    private static let minPointDistance: Double = 30.0

    public var puntos: [CLLocation]
    public var distancia: Double
    public var timestampInicio: Int64
    public var timestampFin: Int64?

    public init() {
        self.puntos = []
        self.distancia = 0.0
        self.timestampInicio = Int64(Date().timeIntervalSince1970 * 1000)
        self.timestampFin = nil
    }

    public func addPunto(_ location: CLLocation) {
        if let ultimo = puntos.last {
            let curDist = location.distance(from: ultimo)
            if curDist > Trip.minPointDistance {
                distancia += curDist
                puntos.append(location)
            }
        } else {
            puntos.append(location)
        }
    }

    public func toDictionary(withPath: Bool = false) -> [String: Any] {
        var dict: [String: Any] = [
            "distancia": distancia,
            "timestampInicio": timestampInicio
        ]
        if let timestampFin = timestampFin {
            dict["timestampFin"] = timestampFin
        }
        if withPath {
            dict["puntos"] = puntos.map { LocationPayloadBuilder.locationToDictionary($0) }
        }
        return dict
    }
}
