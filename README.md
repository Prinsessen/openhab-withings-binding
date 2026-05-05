# Withings Health Binding

A comprehensive openHAB binding for **Withings** health devices — smart scales, blood pressure monitors, activity trackers, sleep monitors, and thermometers. Retrieves body measurements, cardiovascular data, activity metrics, and sleep analysis via the Withings Cloud API v2 with full OAuth2 authorization.

---

## Table of Contents

- [Features](#features)
- [Supported Devices](#supported-devices)
- [Requirements](#requirements)
- [Installation](#installation)
- [Thing Architecture (v2.0)](#thing-architecture-v20)
- [What's New in v3.0](#whats-new-in-v30)
- [Configuration](#configuration)

  - [Withings Developer Application](#withings-developer-application)
  - [Account Bridge](#account-bridge)
  - [Person Bridge](#person-bridge)
  - [Device Thing](#device-thing)
- [OAuth2 Authorization](#oauth2-authorization)
- [Discovery](#discovery)
- [Channels](#channels)
  - [Person Bridge Channels](#person-bridge-channels)
  - [Device Thing Channels](#device-thing-channels)
- [Items Example](#items-example)
- [Sitemap Example](#sitemap-example)
- [Upgrading from v1.x](#upgrading-from-v1x)
- [Building from Source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Changelog](#changelog)
- [License](#license)
- [Credits](#credits)

---

## Features

- **Body composition** — Weight, fat ratio, fat mass, fat-free mass, muscle mass, bone mass, hydration, wrist skin temperature
- **Cardiovascular monitoring** — Heart rate, blood pressure, SpO2, pulse wave velocity, VO2 max, vascular age, body temperature, **ECG Afib classification** (ScanWatch 2)
- **Activity tracking** — Steps, distance, calories, elevation, heart rate zones 0–3, active duration
- **Sleep analysis** — Total sleep, deep/light/REM stages, wakeup count, sleep score, efficiency, latency, snoring, breathing disturbances, night HR and respiration rate, **HRV (RMSSD/SDNN/quality)** and **skin temperature** from sleep summary
- **Per-device status** — Battery level, device model name, last session timestamp — one `device` thing per physical Withings device
- **Auto-discovery** — Physical devices discovered via Inbox when scanning the person bridge
- **OAuth2 web authorization** — Built-in servlet at `/withings` for browser-based authorization
- **Automatic token refresh** — Tokens refreshed transparently before expiry
- **Token persistence** — Tokens survive reboots even with `.things` file configuration
- **Multi-person support** — Multiple person bridges per account bridge

---

## Supported Devices

| Device Type | Supported Data |
|---|---|
| **Smart Scales** (Body, Body+, Body Comp, Body Scan) | Weight, fat ratio, fat/muscle/bone mass, hydration, heart rate, vascular age |
| **Blood Pressure Monitors** (BPM, BPM Connect, BPM Core) | Systolic/diastolic blood pressure, heart rate |
| **Activity Trackers** (ScanWatch, Steel HR, Move) | Steps, distance, calories, elevation, heart rate zones, active duration |
| **Sleep Monitors** (Sleep Analyzer, Sleep) | Sleep stages, score, snoring, heart/respiration rate during sleep |
| **ScanWatch 2 / ScanWatch Nova** | All activity + sleep + skin temperature (sleep summary) + HRV (RMSSD, SDNN, quality) + ECG Afib classification |
| **Thermometers** (Thermo) | Body temperature |

> Channels for data not supported by your device will remain `UNDEF` — this is harmless.

---

## What's New in v3.0

| Feature | Channel | Device |
|---|---|---|
| ECG Afib classification | `cardiovascular#afib` | ScanWatch 2, ScanWatch Nova |
| HRV — RMSSD | `sleep#sleepHrvRmssd` | ScanWatch 2, ScanWatch Nova |
| HRV — SDNN | `sleep#sleepHrvSdnn` | ScanWatch 2, ScanWatch Nova |
| HRV quality score | `sleep#sleepHrvQuality` | ScanWatch 2, ScanWatch Nova |
| Wrist skin temperature | `body#skinTemperature` | ScanWatch 2 |

Skin temperature is now retrieved from the sleep summary API (7-day rolling window) instead of the intraday API — more reliable and no extra API call.

---

## Requirements

- openHAB 4.x or 5.x
- A [Withings developer account](https://developer.withings.com/) with a registered application
- At least one Withings health device linked to your Withings account
- Network access to `wbsapi.withings.net`

---

## Installation

1. Build the binding from source (see [Building from Source](#building-from-source))
2. Copy the JAR to your addons folder:

```bash
sudo cp target/org.openhab.binding.withings-5.2.0-SNAPSHOT.jar /usr/share/openhab/addons/
```

3. The binding loads automatically — verify in **Settings → Add-ons → Bindings**.

---

## Thing Architecture (v2.0)

v2.0 introduces a three-tier bridge hierarchy that mirrors the Withings data model:

```
withings:account (Bridge)           → your Withings developer app / OAuth2 tokens
  └── withings:person (Bridge)      → a Withings user account (health data)
        └── withings:device (Thing) → a physical Withings device (battery, model, last session)
```

**Example — one user, one scale:**

```
Withings Account (bridge)
  └── Nanna (person bridge)
        └── Withings Body+ (device thing)
```

**Example — one user, scale + watch:**

```
Withings Account (bridge)
  └── Nanna (person bridge)
        ├── Withings Body+ (device thing)
        └── Withings ScanWatch 2 (device thing)
```

Physical devices are discovered automatically via the **Inbox** — you do not need to enter device IDs manually.

---

## Configuration

### Withings Developer Application

1. Go to [Withings Developer Portal](https://developer.withings.com/)
2. Create a new application
3. Note your **Client ID** and **Client Secret**
4. Set **Redirect URI** to your openHAB callback URL (e.g. `https://your-openhab.example.com/callback`)
5. Required OAuth2 scopes: `user.metrics,user.activity,user.sleepevents,user.info`

### Account Bridge

| Parameter | Required | Default | Description |
|---|---|---|---|
| `clientId` | Yes | — | OAuth2 Client ID |
| `clientSecret` | Yes | — | OAuth2 Client Secret |
| `redirectUri` | Yes | — | OAuth2 callback URL |
| `accessToken` | No | — | Leave empty for web-based authorization |
| `refreshToken` | No | — | Leave empty for web-based authorization |

### Person Bridge

| Parameter | Required | Default | Description |
|---|---|---|---|
| `userId` | Yes | — | Withings user ID (shown after OAuth2 authorization) |
| `pollingIntervalBody` | No | 15 | Body measurement polling interval (minutes) |
| `pollingIntervalActivity` | No | 30 | Activity polling interval (minutes) |
| `pollingIntervalSleep` | No | 60 | Sleep polling interval (minutes) |

### Device Thing

| Parameter | Required | Default | Description |
|---|---|---|---|
| `deviceId` | Yes | — | Withings internal device ID — set automatically by discovery |
| `pollingIntervalDevice` | No | 60 | Device status polling interval (minutes) |

### Full .things Example

```java
Bridge withings:account:home "Withings Account" [
    clientId="YOUR_CLIENT_ID",
    clientSecret="YOUR_CLIENT_SECRET",
    redirectUri="https://your-openhab.example.com/callback"
] {
    Bridge person nanna "Nanna" [
        userId=17873009,
        pollingIntervalBody=15,
        pollingIntervalActivity=30,
        pollingIntervalSleep=60
    ] {
        Thing device body_plus "Withings Body+" [
            deviceId="030166b1ec0906a5220da271daa01a0cab245cfd"
        ]
    }
}
```

---

## OAuth2 Authorization

1. Configure the account bridge — leave `accessToken`/`refreshToken` empty
2. Bridge goes `OFFLINE / CONFIGURATION_PENDING`
3. Open `http(s)://your-openhab:8080/withings` in a browser
4. Click **"Authorize with Withings"**
5. Log in to Withings and grant access
6. Bridge goes `ONLINE` — note the **User ID** for your person bridge

Tokens are stored in `/var/lib/openhab/jsondb/` and survive reboots. You only authorize once.

**Re-authorization** is needed if scopes were changed (upgrade from old version). Symptoms: 403 errors in log, device/sleep channels returning `UNDEF`.

---

## Discovery

Physical devices are discovered automatically under the person bridge.

1. Ensure the person bridge is `ONLINE`
2. Go to **Settings → Things → (person bridge) → Scan for Things** — or use the Inbox
3. Discovered devices appear as `withings:device` things in the Inbox
4. Approve each device — it is added as a child under the person bridge

You can also define devices manually using the `deviceId` from the discovery result (visible in the Inbox overview or the openHAB log).

---

## Channels

### Person Bridge Channels

#### Body — group `body`

| Channel | Type | Description |
|---|---|---|
| `weight` | `Number:Mass` | Body weight (kg) |
| `fatRatio` | `Number` | Body fat percentage |
| `fatMass` | `Number:Mass` | Fat mass (kg) |
| `fatFreeMass` | `Number:Mass` | Fat-free / lean mass (kg) |
| `muscleMass` | `Number:Mass` | Muscle mass (kg) |
| `hydration` | `Number:Mass` | Hydration (kg) |
| `boneMass` | `Number:Mass` | Bone mass (kg) |
| `lastMeasurement` | `DateTime` | Timestamp of most recent measurement |
| `skinTemperature` | `Number:Temperature` | Wrist skin temperature from sleep summary (°C) *(ScanWatch 2, new in v3.0.0)* |

#### Cardiovascular — group `cardiovascular`

| Channel | Type | Description |
|---|---|---|
| `heartPulse` | `Number` | Heart rate (bpm) |
| `systolicBP` | `Number:Pressure` | Systolic blood pressure (mmHg) |
| `diastolicBP` | `Number:Pressure` | Diastolic blood pressure (mmHg) |
| `pulseWaveVelocity` | `Number` | Pulse wave velocity (m/s) |
| `vo2Max` | `Number` | VO2 Max (ml/min/kg) |
| `vascularAge` | `Number` | Estimated vascular age (years) |
| `spo2` | `Number` | Blood oxygen saturation (%) |
| `temperature` | `Number:Temperature` | Body temperature (°C) |
| `afib` | `Number` | ECG Afib classification: 0 = sinus rhythm, 1 = Afib detected, 2 = inconclusive *(ScanWatch 2, new in v3.0.0)* |

#### Activity — group `activity`

| Channel | Type | Description |
|---|---|---|
| `steps` | `Number` | Steps today |
| `distance` | `Number:Length` | Distance today (m) |
| `calories` | `Number:Energy` | Active calories today |
| `totalCalories` | `Number:Energy` | Total calories today |
| `elevation` | `Number` | Floors climbed today |
| `softActivity` | `Number:Time` | Light activity duration (s) |
| `moderateActivity` | `Number:Time` | Moderate activity duration (s) |
| `intenseActivity` | `Number:Time` | Intense activity duration (s) |
| `activeDuration` | `Number:Time` | Total active duration (s) |
| `hrAverage` | `Number` | Average HR during day (bpm) |
| `hrMin` | `Number` | Minimum HR during day (bpm) |
| `hrMax` | `Number` | Maximum HR during day (bpm) |
| `hrZone0`–`hrZone3` | `Number:Time` | Time in HR zones 0–3 (s) |

#### Sleep — group `sleep`

| Channel | Type | Description |
|---|---|---|
| `totalSleepTime` | `Number:Time` | Total time asleep (s) |
| `totalTimeInBed` | `Number:Time` | Total time in bed (s) |
| `deepSleepDuration` | `Number:Time` | Deep sleep duration (s) |
| `lightSleepDuration` | `Number:Time` | Light sleep duration (s) |
| `remSleepDuration` | `Number:Time` | REM sleep duration (s) |
| `nbRemEpisodes` | `Number` | REM episodes |
| `wakeupCount` | `Number` | Wakeups during night |
| `wakeupDuration` | `Number:Time` | Total awake time during night (s) |
| `outOfBedCount` | `Number` | Out-of-bed events |
| `timeToSleep` | `Number:Time` | Time to fall asleep (s) |
| `sleepLatency` | `Number:Time` | Clinical sleep latency (s) |
| `wakeupLatency` | `Number:Time` | Time in bed after final wakeup (s) |
| `sleepScore` | `Number` | Sleep quality score (0–100) |
| `sleepEfficiency` | `Number` | Sleep efficiency (%) |
| `snoring` | `Number:Time` | Total snoring duration (s) |
| `snoringEpisodes` | `Number` | Snoring episode count |
| `nightEvents` | `Number` | Total detected night events |
| `breathingDisturbances` | `Number` | Breathing disturbance index |
| `sleepHrAverage` | `Number` | Average HR during sleep (bpm) |
| `sleepHrMin` | `Number` | Minimum HR during sleep (bpm) |
| `sleepHrMax` | `Number` | Maximum HR during sleep (bpm) |
| `sleepRrAverage` | `Number` | Average respiration rate (brpm) |
| `sleepRrMin` | `Number` | Minimum respiration rate (brpm) |
| `sleepRrMax` | `Number` | Maximum respiration rate (brpm) |
| `sleepHrvRmssd` | `Number` | HRV — RMSSD during sleep (ms) *(ScanWatch 2, new in v3.0.0)* |
| `sleepHrvSdnn` | `Number` | HRV — SDNN during sleep (ms) *(ScanWatch 2, new in v3.0.0)* |
| `sleepHrvQuality` | `Number` | HRV quality score *(ScanWatch 2, new in v3.0.0)* |
| `sleepSkinTemperature` | `Number:Temperature` | Wrist skin temperature from sleep summary (°C) — also updated to `body#skinTemperature` *(ScanWatch 2, new in v3.0.0)* |

### Device Thing Channels

Channel group: `device`

| Channel | Type | Description |
|---|---|---|
| `battery` | `String` | Battery level — `high`, `medium`, or `low` |
| `model` | `String` | Device model name (e.g. `Body+`, `ScanWatch 2`) |
| `lastSession` | `DateTime` | Timestamp of last sync session |

---

## Items Example

```java
// Body
Number:Mass   Withings_Weight       "Weight [%.1f kg]"      <wh-weight-blue>    (gWithings_Health) ["Measurement"] { channel="withings:person:home:nanna:body#weight" }
Number        Withings_Fat_Ratio    "Fat ratio [%.1f %%]"   <wh-percent-orange> (gWithings_Health) ["Measurement"] { channel="withings:person:home:nanna:body#fatRatio" }
Number:Mass   Withings_Muscle_Mass  "Muscle mass [%.1f kg]" <wh-muscle-green>   (gWithings_Health) ["Measurement"] { channel="withings:person:home:nanna:body#muscleMass" }
DateTime      Withings_Last         "Last measurement [%1$td-%1$tm-%1$tY %1$tH:%1$tM]" <wh-time-teal> (gWithings_Health) { channel="withings:person:home:nanna:body#lastMeasurement" }

// Device (v2.0 — link to device thing, not person thing)
String        Withings_Battery      "Battery [%s]"          <battery>           (gWithings_Device) ["Status"] { channel="withings:device:home:nanna:body_plus:device#battery" }
String        Withings_Model        "Model [%s]"            <wh-watch-teal>     (gWithings_Device) ["Status"] { channel="withings:device:home:nanna:body_plus:device#model" }
DateTime      Withings_LastSession  "Last session [%1$td-%1$tm %1$tH:%1$tM]"   <wh-time-teal> (gWithings_Device) { channel="withings:device:home:nanna:body_plus:device#lastSession" }
```

> The channel UID pattern for a device thing is:  
> `withings:device:<accountId>:<personId>:<deviceThingId>:device#<channelId>`

---

## Sitemap Example

```perl
Frame label="Body Composition" {
    Text item=Withings_Weight
    Text item=Withings_Fat_Ratio
    Text item=Withings_Muscle_Mass
    Text item=Withings_Last
}
Frame label="Device" {
    Text item=Withings_Model
    Text item=Withings_Battery
    Text item=Withings_LastSession
}
```

---

## Rules Examples

### Battery low notification

```javascript
rules.when()
    .item("Withings_Device_Battery").changed()
    .then(() => {
        const battery = items.getItem("Withings_Device_Battery").state;
        if (battery && battery.toLowerCase() === "low") {
            const model = items.getItem("Withings_Device_Model").state;
            actions.NotificationAction.sendBroadcastNotification(
                `Withings ${model} batteri er lavt — husk at oplade`
            );
        }
    })
    .build("Withings Battery Low Alert");
```

### Weight change notification

```javascript
rules.when()
    .item("Withings_Weight").changed()
    .then(event => {
        const weight   = items.getItem("Withings_Weight").numericState;
        const previous = parseFloat(event.oldState);
        if (!isNaN(previous)) {
            const diff = weight - previous;
            if (Math.abs(diff) >= 0.5) {
                actions.NotificationAction.sendBroadcastNotification(
                    `Vægt: ${diff > 0 ? '+' : ''}${diff.toFixed(1)} kg (nu ${weight.toFixed(1)} kg)`
                );
            }
        }
    })
    .build("Withings Weight Change");
```

### Sleep score notification

```javascript
rules.when()
    .item("Withings_Sleep_Score").changed()
    .then(() => {
        const score = items.getItem("Withings_Sleep_Score").numericState;
        const quality = score >= 70 ? "god" : score >= 50 ? "middel" : "dårlig";
        actions.NotificationAction.sendBroadcastNotification(
            `Søvnscore: ${score} (${quality})`
        );
    })
    .build("Withings Sleep Score Notification");
```

---

## Upgrading from v1.x

### ⚠️ Breaking Change: `person` is now a Bridge

In v2.0, `withings:person` is changed from a **Thing** to a **Bridge** (so physical devices can be its children).

**In your `.things` file**, change `Thing person` → `Bridge person` and add a nested `Thing device`:

```java
// BEFORE (v1.x)
Bridge withings:account:home [...] {
    Thing person nanna "Nanna" [userId=17873009, ...]
}

// AFTER (v2.0)
Bridge withings:account:home [...] {
    Bridge person nanna "Nanna" [userId=17873009, ...] {
        Thing device body_plus "Withings Body+" [deviceId="YOUR_DEVICE_ID"]
    }
}
```

**If you configured via the UI**, delete the existing `withings:person` thing and re-add it as a Bridge.

### Device channels moved to the device thing

```java
// BEFORE (v1.x) — device channels on person thing
{ channel="withings:person:home:nanna:device#battery" }

// AFTER (v2.0) — device channels on device thing
{ channel="withings:device:home:nanna:body_plus:device#battery" }
```

**Migration steps:**
1. Change `Thing person` → `Bridge person` in your `.things` file
2. Trigger a scan: **Settings → Things → (person bridge) → Scan for Things**
3. Approve discovered device(s) in the Inbox
4. Update `.items` file to use the new device thing channel UIDs

The `deviceModel` parameter on the person thing is no longer needed and can be removed.

**No re-authorization required** — existing OAuth2 tokens remain valid.

---

## Building from Source

```bash
# Build in the openhab-addons repository
cd /etc/openhab-addons/bundles/org.openhab.binding.withings
mvn spotless:apply && mvn clean package -DskipTests

# Deploy
sudo cp target/org.openhab.binding.withings-5.2.0-SNAPSHOT.jar /usr/share/openhab/addons/
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Bridge `OFFLINE / CONFIGURATION_PENDING` | No tokens configured | Open `/withings` in browser and authorize |
| Bridge `OFFLINE` after reboot | Token file missing | Re-authorize via `/withings` |
| 401 errors in log | Refresh token expired | Re-authorize via `/withings` |
| Device channels `UNDEF` | Missing `user.info` OAuth2 scope | Re-authorize to add missing scope |
| Activity/sleep channels `UNDEF` | Device doesn't track activity/sleep | Normal — Body+ only populates body channels |
| `ThingImpl cannot be cast to Bridge` | Still using v1.x `.things` syntax | Change `Thing person` to `Bridge person` |

**Enable debug logging (from Karaf SSH):**
```
log:set DEBUG org.openhab.binding.withings
```

---

## Changelog

### v3.0.1 — May 2026 *(current)*

- **Fix:** `sleep#sleepEfficiency` now correctly reports percentage (0–100) — previously the raw Withings API ratio (0.0–1.0) was sent without conversion
- **Fix:** Body and skin temperature channel descriptions made more user-friendly

### v3.0.0 — May 2026

- **New:** `cardiovascular#afib` — ECG Afib classification from Heart v2 API (0 = sinus rhythm, 1 = Afib detected, 2 = inconclusive) — ScanWatch 2 / ScanWatch Nova
- **New:** `sleep#sleepHrvRmssd`, `sleep#sleepHrvSdnn`, `sleep#sleepHrvQuality` — HRV metrics from sleep summary — ScanWatch 2 / ScanWatch Nova
- **New:** `body#skinTemperature` — wrist skin temperature from sleep summary (replaces intraday API call) — ScanWatch 2
- **Changed:** `getSleepSummary` now uses a 7-day rolling window and expanded `data_fields` to capture all new metrics
- **Changed:** Separate `getLatestSkinTemperature()` intraday call removed — skin temperature now comes from sleep summary
- **New:** Heart polling job — polls Heart v2 API on the same interval as sleep, with 30 s offset

---

### v2.0.0 — April 2026 ⚠️ BREAKING CHANGE

- **Architecture:** `withings:person` changed from Thing to **Bridge** — three-tier hierarchy (account → person → device)
- **New:** `withings:device` thing type — battery, model, last session per physical device
- **New:** `WithingsDiscoveryService` — physical devices auto-discovered via Inbox when scanning the person bridge
- **Removed:** `deviceModel` and legacy `device#` channel group on person thing
- **Migration:** Change `Thing person` → `Bridge person`; re-link device channel items to the new device thing

### v1.3.0 — April 2026
- **Fix:** Device channels show the most recently synced device (not always the first in API list)
- **New:** Optional `deviceModel` filter parameter on person thing

### v1.2.0 — April 2026
- **New:** `device` channel group — battery, model, lastSession
- **New:** Extended sleep channels (REM episodes, out-of-bed, latency, efficiency, HR/RR during sleep)
- **New:** Extended activity channels (totalCalories, hrMin/Max, hrZone0–3)

### v1.1.0 — March 2026
- **New:** Token persistence via `StorageService` — tokens survive reboots
- **New:** Multi-person support
- **Fix:** Stale token / 401 recovery

### v1.0.0 — March 2026
- Initial release — body, cardiovascular, activity, sleep channels
- OAuth2 web authorization servlet at `/withings`

---

## License

[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)

---

## Credits

**Author:** [Nanna Agesen](https://github.com/Prinsessen)

Built as a community binding for the [openHAB](https://www.openhab.org/) smart home platform.  
Feedback and contributions welcome via [GitHub Issues](https://github.com/Prinsessen/openhab-withings-binding/issues).
