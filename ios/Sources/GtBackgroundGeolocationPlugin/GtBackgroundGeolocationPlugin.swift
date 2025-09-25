import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(GtBackgroundGeolocationPlugin)
public class GtBackgroundGeolocationPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "GtBackgroundGeolocationPlugin"
    public let jsName = "GtBackgroundGeolocation"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentPosition", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = GtBackgroundGeolocation()

    @objc func configure(_ call: CAPPluginCall) {
        // let url = call.getString("url")
        // TODO: Store configuration
        call.resolve()
    }

    @objc func start(_ call: CAPPluginCall) {
        // TODO: Request permissions and start location updates
        // Remember to add NSLocationWhenInUseUsageDescription,
        // NSLocationAlwaysAndWhenInUseUsageDescription, and
        // UIBackgroundModes (with 'location') to your Info.plist
        call.resolve()
    }

    @objc func stop(_ call: CAPPluginCall) {
        // TODO: Stop location updates
        call.resolve()
    }

    @objc func getCurrentPosition(_ call: CAPPluginCall) {
        // TODO: Implement single location fetch
        call.unimplemented("getCurrentPosition is not implemented on iOS.")
    }
}
