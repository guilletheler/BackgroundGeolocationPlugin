import Foundation
import CoreLocation

public class Trip {
    private static let maxAccuracyMeters: Double = 20.0
    private static let minDistStraightMeters: Double = 10.0
    private static let minDistTurnMeters: Double = 5.0
    private static let minDistStoppedMeters: Double = 6.0
    private static let turnBearingDeltaDeg: Double = 15.0

    private static let absoluteMaxSpeedMps: Double = 45.0
    private static let baseUrbanSpeedMps: Double = 18.0
    private static let highSpeedThresholdMps: Double = 22.2
    private static let maxAccelMps2: Double = 3.5
    private static let maxEffectiveAccelDtSec: Double = 7.0
    private static let highSpeedMaxDeviationRatio: Double = 0.35
    private static let highSpeedMaxTurnRateDegSec: Double = 28.0
    private static let simplifyEpsilonMeters: Double = 4.0

    public var puntos: [CLLocation]
    public var distancia: Double
    public var timestampInicio: Int64
    public var timestampFin: Int64?

    private var lastBearing: Double? = nil
    private var lastValidatedSpeedMps: Double = 0.0
    private var lastStationaryDate: Date? = nil
    private var puntosSospechosos: [CLLocation] = []

    public init() {
        self.puntos = []
        self.distancia = 0.0
        self.timestampInicio = Int64(Date().timeIntervalSince1970 * 1000)
        self.timestampFin = nil
    }

    public func addPunto(_ location: CLLocation) {
        if location.horizontalAccuracy < 0 || location.horizontalAccuracy > Trip.maxAccuracyMeters {
            return
        }

        guard let ultimo = puntos.last else {
            puntos.append(location)
            lastStationaryDate = location.timestamp
            if location.speed >= 0 {
                lastValidatedSpeedMps = min(location.speed, Trip.absoluteMaxSpeedMps)
            }
            if location.course >= 0 && location.speed > 1.0 {
                lastBearing = location.course
            }
            return
        }

        let curDist = location.distance(from: ultimo)
        let refUltimoDate: Date = {
            if let statDate = lastStationaryDate, statDate > ultimo.timestamp {
                return statDate
            }
            return ultimo.timestamp
        }()

        let currentBearing = (location.course >= 0 && location.speed > 1.0)
            ? location.course
            : Self.bearing(from: ultimo, to: location)

        let deltaBearing = Self.angleDiff(currentBearing, lastBearing)
        let isTurn = lastBearing != nil && deltaBearing >= Trip.turnBearingDeltaDeg
        let isStopped = location.speed >= 0 && location.speed < 0.6
        let requiredMinDist = isTurn ? Trip.minDistTurnMeters : (isStopped ? Trip.minDistStoppedMeters : Trip.minDistStraightMeters)

        if !puntosSospechosos.isEmpty {
            let dtUltimoSec = max(0.5, abs(location.timestamp.timeIntervalSince(refUltimoDate)))
            let distDesdeUltimo = location.distance(from: ultimo)
            let speedDesdeUltimo = distDesdeUltimo / dtUltimoSec

            if dtUltimoSec >= 0.5 && isTransitionPlausible(from: ultimo, to: location, distMeters: distDesdeUltimo, dtSec: dtUltimoSec, impliedSpeedMps: speedDesdeUltimo, prevSpeedMps: lastValidatedSpeedMps, prevBearing: lastBearing) {
                puntosSospechosos.removeAll()
                if distDesdeUltimo >= requiredMinDist {
                    acceptLocation(location, segmentSpeedMps: speedDesdeUltimo, currentBearing: currentBearing)
                } else {
                    lastStationaryDate = location.timestamp
                    lastValidatedSpeedMps = 0.0
                }
                return
            }

            let lastCandidato = puntosSospechosos.last!
            let dtCandSec = max(0.5, abs(location.timestamp.timeIntervalSince(lastCandidato.timestamp)))
            let distDesdeCand = location.distance(from: lastCandidato)
            let speedDesdeCand = distDesdeCand / dtCandSec

            var candBearing: Double? = nil
            var prevCandSpeed = lastValidatedSpeedMps
            if puntosSospechosos.count >= 2 {
                let prevCand = puntosSospechosos[puntosSospechosos.count - 2]
                candBearing = Self.bearing(from: prevCand, to: lastCandidato)
                let dtPrev = max(0.5, abs(lastCandidato.timestamp.timeIntervalSince(prevCand.timestamp)))
                prevCandSpeed = lastCandidato.distance(from: prevCand) / dtPrev
            } else if lastCandidato.course >= 0 && lastCandidato.speed > 1.5 {
                candBearing = lastCandidato.course
                prevCandSpeed = lastCandidato.speed
            }

            if dtCandSec >= 0.5 && distDesdeCand >= Trip.minDistStraightMeters && isTransitionPlausible(from: lastCandidato, to: location, distMeters: distDesdeCand, dtSec: dtCandSec, impliedSpeedMps: speedDesdeCand, prevSpeedMps: prevCandSpeed, prevBearing: candBearing) {
                puntosSospechosos.append(location)
                if puntosSospechosos.count >= 3 && isCandidateChainCoherent(puntosSospechosos) {
                    puntos.append(contentsOf: puntosSospechosos)
                    puntosSospechosos.removeAll()
                    pruneSpikesAndRecalculateDistance()
                    updateStateFromTail()
                }
                return
            }

            if distDesdeCand >= Trip.minDistStraightMeters {
                puntosSospechosos.removeAll()
                puntosSospechosos.append(location)
            }
            return
        }

        if curDist < requiredMinDist {
            if curDist <= Trip.minDistStoppedMeters || isStopped {
                lastStationaryDate = location.timestamp
                lastValidatedSpeedMps = location.speed >= 0 ? min(location.speed, 1.5) : 0.0
            }
            return
        }

        let dtSec = abs(location.timestamp.timeIntervalSince(refUltimoDate))
        let speedMps = dtSec > 0 ? (curDist / dtSec) : Double.greatestFiniteMagnitude

        if dtSec < 0.5 || !isTransitionPlausible(from: ultimo, to: location, distMeters: curDist, dtSec: dtSec, impliedSpeedMps: speedMps, prevSpeedMps: lastValidatedSpeedMps, prevBearing: lastBearing) {
            puntosSospechosos.append(location)
            return
        }

        acceptLocation(location, segmentSpeedMps: speedMps, currentBearing: currentBearing)
    }

    private func acceptLocation(_ location: CLLocation, segmentSpeedMps: Double, currentBearing: Double) {
        puntos.append(location)
        lastStationaryDate = location.timestamp
        let dopplerSpeed = location.speed >= 0 ? location.speed : segmentSpeedMps
        lastValidatedSpeedMps = min(Trip.absoluteMaxSpeedMps, 0.6 * segmentSpeedMps + 0.4 * dopplerSpeed)
        lastBearing = currentBearing
        pruneSpikesAndRecalculateDistance()
    }

    private func updateStateFromTail() {
        guard puntos.count >= 2 else { return }
        let prev = puntos[puntos.count - 2]
        let last = puntos[puntos.count - 1]
        lastStationaryDate = last.timestamp
        let dtSec = max(0.5, abs(last.timestamp.timeIntervalSince(prev.timestamp)))
        let segSpeed = last.distance(from: prev) / dtSec
        lastValidatedSpeedMps = min(Trip.absoluteMaxSpeedMps, segSpeed)
        lastBearing = (last.course >= 0 && last.speed > 1.0) ? last.course : Self.bearing(from: prev, to: last)
    }

    private func isTransitionPlausible(
        from: CLLocation,
        to: CLLocation,
        distMeters: Double,
        dtSec: Double,
        impliedSpeedMps: Double,
        prevSpeedMps: Double,
        prevBearing: Double?
    ) -> Bool {
        if dtSec <= 0 || impliedSpeedMps > Trip.absoluteMaxSpeedMps {
            return false
        }
        if to.speed >= 0 && to.speed > Trip.absoluteMaxSpeedMps {
            return false
        }

        let effectiveAccelDt = min(dtSec, Trip.maxEffectiveAccelDtSec)
        let baselineSpeed = max(prevSpeedMps, Trip.baseUrbanSpeedMps)
        let adaptiveMaxSpeed = min(Trip.absoluteMaxSpeedMps, baselineSpeed + Trip.maxAccelMps2 * effectiveAccelDt)

        if impliedSpeedMps > adaptiveMaxSpeed {
            return false
        }

        if impliedSpeedMps > Trip.highSpeedThresholdMps {
            let maxHighSpeedAllowed = max(
                Trip.highSpeedThresholdMps + Trip.maxAccelMps2 * min(dtSec, 4.0),
                prevSpeedMps * (1.0 + Trip.highSpeedMaxDeviationRatio)
            )
            if impliedSpeedMps > maxHighSpeedAllowed {
                return false
            }

            if let prevBearing = prevBearing {
                let segBearing = Self.bearing(from: from, to: to)
                let turnDeg = Self.angleDiff(segBearing, prevBearing)
                let maxAllowedTurn = min(75.0, Trip.highSpeedMaxTurnRateDegSec * max(1.0, min(dtSec, 3.0)))
                if turnDeg > maxAllowedTurn {
                    return false
                }
            }
        }

        if to.speed >= 0 && impliedSpeedMps > 15.0 {
            let dopplerSpeed = to.speed
            if dopplerSpeed < impliedSpeedMps * 0.45 && (impliedSpeedMps - dopplerSpeed) > 8.0 {
                return false
            }
        }

        if distMeters > 200.0, let prevBearing = prevBearing {
            let segBearing = Self.bearing(from: from, to: to)
            let turnDeg = Self.angleDiff(segBearing, prevBearing)
            if turnDeg > 100.0 && impliedSpeedMps > 12.0 {
                return false
            }
        }

        return true
    }

    private func isCandidateChainCoherent(_ chain: [CLLocation]) -> Bool {
        guard chain.count >= 3 else { return false }
        let d01 = chain[1].distance(from: chain[0])
        let d12 = chain[2].distance(from: chain[1])
        if d01 < Trip.minDistStraightMeters || d12 < Trip.minDistStraightMeters {
            return false
        }
        let b01 = Self.bearing(from: chain[0], to: chain[1])
        let b12 = Self.bearing(from: chain[1], to: chain[2])
        return Self.angleDiff(b01, b12) <= 45.0
    }

    public func pruneSpikesAndRecalculateDistance() {
        guard puntos.count >= 3 else {
            distancia = Self.calculateTotalDistance(puntos)
            return
        }

        var changed = true
        while changed && puntos.count >= 3 {
            changed = false
            let n = puntos.count
            for i in 0..<(n - 2) {
                let maxK = min(8, n - 1 - i)
                var pruned = false
                if maxK >= 2 {
                    for k in stride(from: maxK, through: 2, by: -1) {
                        let directDist = puntos[i + k].distance(from: puntos[i])
                        var pathDist = 0.0
                        var maxSeg = 0.0
                        for j in i..<(i + k) {
                            let seg = puntos[j + 1].distance(from: puntos[j])
                            pathDist += seg
                            if seg > maxSeg { maxSeg = seg }
                        }
                        if pathDist <= 20.0 { continue }

                        let isJitterLoop = directDist < 45.0 && pathDist > max(35.0, 2.2 * directDist)
                        let isTeleportExcursion = maxSeg > 220.0 && pathDist > 2.8 * max(directDist, 25.0)
                        if isJitterLoop || isTeleportExcursion {
                            puntos.removeSubrange((i + 1)..<(i + k))
                            changed = true
                            pruned = true
                            break
                        }
                    }
                }
                if pruned { break }
            }

            if changed { continue }

            for i in 1..<(puntos.count - 1) {
                let a = puntos[i - 1]
                let b = puntos[i]
                let c = puntos[i + 1]
                let dAB = b.distance(from: a)
                let dBC = c.distance(from: b)
                let dAC = c.distance(from: a)
                if dAB > 5.0 && dBC > 5.0 {
                    let b1 = Self.bearing(from: a, to: b)
                    let b2 = Self.bearing(from: b, to: c)
                    let turn = Self.angleDiff(b1, b2)
                    if turn > 135.0 && (dAB + dBC) > 1.7 * max(dAC, 5.0) {
                        puntos.remove(at: i)
                        changed = true
                        break
                    }
                }
            }
        }

        distancia = Self.calculateTotalDistance(puntos)
    }

    public func simplificarRuta() {
        pruneSpikesAndRecalculateDistance()
    }

    private static func calculateTotalDistance(_ pts: [CLLocation]) -> Double {
        guard pts.count >= 2 else { return 0.0 }
        var total = 0.0
        for i in 1..<pts.count {
            total += pts[i].distance(from: pts[i - 1])
        }
        return total
    }

    private static func bearing(from p1: CLLocation, to p2: CLLocation) -> Double {
        let lat1 = p1.coordinate.latitude * .pi / 180.0
        let lon1 = p1.coordinate.longitude * .pi / 180.0
        let lat2 = p2.coordinate.latitude * .pi / 180.0
        let lon2 = p2.coordinate.longitude * .pi / 180.0
        let dLon = lon2 - lon1
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        let deg = atan2(y, x) * 180.0 / .pi
        return (deg + 360.0).truncatingRemainder(dividingBy: 360.0)
    }

    private static func angleDiff(_ b1: Double, _ b2: Double?) -> Double {
        guard let b2 = b2 else { return 0.0 }
        let diff = abs(b1 - b2).truncatingRemainder(dividingBy: 360.0)
        return diff > 180.0 ? 360.0 - diff : diff
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
