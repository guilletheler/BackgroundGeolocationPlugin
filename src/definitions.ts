export interface GtBackgroundGeolocationPlugin {
  /**
   * Configure the background geolocation plugin.
   * This method must be called before starting the service.
   *
   * @param options - The configuration options.
   * @since 0.0.1
   */
  configure(options: GtBackgroundGeolocationConfig): Promise<void>;

  /**
   * Start the background geolocation service.
   *
   * @since 0.0.1
   */
  start(): Promise<void>;

  /**
   * Stop the background geolocation service.
   *
   * @since 0.0.1
   */
  stop(): Promise<void>;
  /**
   * Get the current device location.
  *
  * @since 0.0.1
  */
  getCurrentPosition(): Promise<Location>;
  /**
   * Check the app required permissions, can return 'background' and/or 'location'
   *
   * @since 0.0.1
   */
  checkPermissions(): Promise<GrantedPermissions>;
  /**
   * Check the app required permissions.
   *
   * @since 0.0.1
  */
  requestPermissions(options: GrantedPermissions): Promise<void>;
  /**
   * Start a trip.
   *
   * @since 0.0.1
  */
  initTrip(): Promise<void>;
  /**
   * Returns current trip distance in meters.
  *
  * @since 0.0.1
  */
  getTripDistance(): Promise<Trip>;
  /**
   * Start a trip.
   *
   * @since 0.0.1
  */
  endTrip(): Promise<Trip>;
  /**
   * 
   */
  getStatus(): Promise<ServiceStatus>;
}

export interface ServiceStatus {
  status: 'UNCONFIGURED' | 'STARTED' | 'STOPPED'
}

/**
 * Represent a trip
 *
 * @since 0.0.1
*/
export interface Trip {
  /**
   * 
   * Represent start timestamp
   *
   * @since 0.0.1
   */
  timestampInicio: number;
  /**
   * 
   * Represent end timestamp
   *
   * @since 0.0.1
   */
  timestampFin: number;
  /**
   * 
   * Represent path of the trip
   *
   * @since 0.0.1
   */
  puntos: Location[];
  /**
   * 
   * Represent total distance in meter of the trip
   *
   * @since 0.0.1
   */
  distancia: number;
}

/**
 * Represents a geographical location.
 */
export interface Location {
  /** The latitude of the location. */
  latitude: number;
  /** The longitude of the location. */
  longitude: number;
  /** The accuracy of the location in meters. */
  accuracy: number;
  /** The speed of the location in meters per second. */
  speed: number;
  /** The altitude of the location in meters. */
  altitude: number;
  /** The time of the location in milliseconds since the epoch. */
  time: number;
}

export interface GrantedPermissions {
  fineLocation: boolean;
  backgroundLocation: boolean;
}

/**
 * Represents the configuration for the background geolocation plugin.
 */
export interface GtBackgroundGeolocationConfig {
  /**
   * The title of the notification for the foreground service.
   */
  title: string;
  /**
   * The text of the notification for the foreground service.
   */
  text: string;
  /**
   * The URL of the endpoint to which the location data will be posted.
   */
  url: string;
  /**
   * The bearer token for authorization.
   */
  bearerToken: string;
  /**
   * The small icon for the notification.
   * This should be the name of a drawable resource in your Android project.
   * e.g. 'ic_stat_name' which resolves to `res/drawable/ic_stat_name.xml`
   */
  icon?: string;
  /**
   * A template for the JSON payload to be sent.
   * Use placeholders like `{latitude}`, `{longitude}`, `{accuracy}`, `{speed}`, `{altitude}`, `{time}`.
   */
  messageTemplate?: string;
  /**
   * Interval between send position
   */
  interval?: number;
  /**
   * Maximum interval between send position
   */
  maxInterval?: number;
  /**
   * Minimal distance in meters between send position
   */
  minDist?: number;
}
