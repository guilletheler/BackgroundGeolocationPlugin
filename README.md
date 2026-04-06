# gt-background-geolacation

Send current location to backend periodically

## Install

```bash
npm install gt-background-geolacation
npx cap sync
```

## API

<docgen-index>

* [`configure(...)`](#configure)
* [`start()`](#start)
* [`stop()`](#stop)
* [`getCurrentPosition()`](#getcurrentposition)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions(...)`](#requestpermissions)
* [`initTrip()`](#inittrip)
* [`getTripDistance()`](#gettripdistance)
* [`endTrip()`](#endtrip)
* [`getStatus()`](#getstatus)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### configure(...)

```typescript
configure(options: GtBackgroundGeolocationConfig) => Promise<void>
```

Configure the background geolocation plugin.
This method must be called before starting the service.

| Param         | Type                                                                                    | Description                  |
| ------------- | --------------------------------------------------------------------------------------- | ---------------------------- |
| **`options`** | <code><a href="#gtbackgroundgeolocationconfig">GtBackgroundGeolocationConfig</a></code> | - The configuration options. |

**Since:** 0.0.1

--------------------


### start()

```typescript
start() => Promise<void>
```

Start the background geolocation service.

**Since:** 0.0.1

--------------------


### stop()

```typescript
stop() => Promise<void>
```

Stop the background geolocation service.

**Since:** 0.0.1

--------------------


### getCurrentPosition()

```typescript
getCurrentPosition() => Promise<Location>
```

Get the current device location.

**Returns:** <code>Promise&lt;<a href="#location">Location</a>&gt;</code>

**Since:** 0.0.1

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<GrantedPermissions>
```

Check the app required permissions, can return 'background' and/or 'location'

**Returns:** <code>Promise&lt;<a href="#grantedpermissions">GrantedPermissions</a>&gt;</code>

**Since:** 0.0.1

--------------------


### requestPermissions(...)

```typescript
requestPermissions(options: GrantedPermissions) => Promise<void>
```

Check the app required permissions.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#grantedpermissions">GrantedPermissions</a></code> |

**Since:** 0.0.1

--------------------


### initTrip()

```typescript
initTrip() => Promise<void>
```

Start a trip.

**Since:** 0.0.1

--------------------


### getTripDistance()

```typescript
getTripDistance() => Promise<Trip>
```

Returns current trip distance in meters.

**Returns:** <code>Promise&lt;<a href="#trip">Trip</a>&gt;</code>

**Since:** 0.0.1

--------------------


### endTrip()

```typescript
endTrip() => Promise<Trip>
```

Start a trip.

**Returns:** <code>Promise&lt;<a href="#trip">Trip</a>&gt;</code>

**Since:** 0.0.1

--------------------


### getStatus()

```typescript
getStatus() => Promise<ServiceStatus>
```

**Returns:** <code>Promise&lt;<a href="#servicestatus">ServiceStatus</a>&gt;</code>

--------------------


### Interfaces


#### GtBackgroundGeolocationConfig

Represents the configuration for the background geolocation plugin.

| Prop                  | Type                | Description                                                                                                                                                                        |
| --------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`title`**           | <code>string</code> | The title of the notification for the foreground service.                                                                                                                          |
| **`text`**            | <code>string</code> | The text of the notification for the foreground service.                                                                                                                           |
| **`url`**             | <code>string</code> | The URL of the endpoint to which the location data will be posted.                                                                                                                 |
| **`bearerToken`**     | <code>string</code> | The bearer token for authorization.                                                                                                                                                |
| **`icon`**            | <code>string</code> | The small icon for the notification. This should be the name of a drawable resource in your Android project. e.g. 'ic_stat_name' which resolves to `res/drawable/ic_stat_name.xml` |
| **`messageTemplate`** | <code>string</code> | A template for the JSON payload to be sent. Use placeholders like `{latitude}`, `{longitude}`, `{accuracy}`, `{speed}`, `{altitude}`, `{time}`.                                    |
| **`interval`**        | <code>number</code> | Interval between send position                                                                                                                                                     |
| **`maxInterval`**     | <code>number</code> | Maximum interval between send position                                                                                                                                             |
| **`minDist`**         | <code>number</code> | Minimal distance in meters between send position                                                                                                                                   |


#### Location

Represents a geographical location.

| Prop            | Type                | Description                                               |
| --------------- | ------------------- | --------------------------------------------------------- |
| **`latitude`**  | <code>number</code> | The latitude of the location.                             |
| **`longitude`** | <code>number</code> | The longitude of the location.                            |
| **`accuracy`**  | <code>number</code> | The accuracy of the location in meters.                   |
| **`speed`**     | <code>number</code> | The speed of the location in meters per second.           |
| **`altitude`**  | <code>number</code> | The altitude of the location in meters.                   |
| **`time`**      | <code>number</code> | The time of the location in milliseconds since the epoch. |


#### GrantedPermissions

| Prop                     | Type                 |
| ------------------------ | -------------------- |
| **`fineLocation`**       | <code>boolean</code> |
| **`backgroundLocation`** | <code>boolean</code> |


#### Trip

Represent a trip

| Prop                  | Type                    | Description                                    | Since |
| --------------------- | ----------------------- | ---------------------------------------------- | ----- |
| **`timestampInicio`** | <code>number</code>     |  Represent start timestamp                     | 0.0.1 |
| **`timestampFin`**    | <code>number</code>     |  Represent end timestamp                       | 0.0.1 |
| **`puntos`**          | <code>Location[]</code> |  Represent path of the trip                    | 0.0.1 |
| **`distancia`**       | <code>number</code>     |  Represent total distance in meter of the trip | 0.0.1 |


#### ServiceStatus

| Prop         | Type                                                  |
| ------------ | ----------------------------------------------------- |
| **`status`** | <code>'UNCONFIGURED' \| 'STARTED' \| 'STOPPED'</code> |

</docgen-api>
