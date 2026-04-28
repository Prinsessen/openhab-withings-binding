# openHAB Withings Health Binding

A comprehensive openHAB binding for **Withings** health devices — smart scales, blood pressure monitors, activity trackers, sleep monitors, and thermometers. Retrieves body measurements, cardiovascular data, activity metrics, sleep analysis, and device status via the Withings Cloud API with full OAuth2 authorization support.

![openHAB](https://img.shields.io/badge/openHAB-5.x-blue) ![Withings](https://img.shields.io/badge/Withings-API%20v2-00b388) ![License](https://img.shields.io/badge/license-EPL--2.0-orange)

---

## Table of Contents

- [Features](#features)
- [Supported Devices](#supported-devices)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
  - [Withings Developer Application](#withings-developer-application)
  - [Bridge Configuration (Account)](#bridge-configuration-account)
  - [Thing Configuration (Person)](#thing-configuration-person)
- [OAuth2 Authorization](#oauth2-authorization)
  - [Re-Authorization After Adding New Scopes](#re-authorization-after-adding-new-scopes)
  - [Web-Based Authorization (Recommended)](#web-based-authorization-recommended)
  - [Pre-Configured Tokens](#pre-configured-tokens)
  - [Token Persistence (Reboot Survival)](#token-persistence-reboot-survival)
  - [Reverse Proxy Setup](#reverse-proxy-setup)
- [Channels](#channels)
  - [Body Measurements](#body-measurements)
  - [Cardiovascular](#cardiovascular)
  - [Activity](#activity)
  - [Sleep](#sleep)
  - [Device](#device)
- [Items Example](#items-example)
- [Sitemap Example](#sitemap-example)
- [Rules Examples](#rules-examples)
- [Persistence](#persistence)
- [Building from Source](#building-from-source)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)
- [License](#license)
- [Changelog](#changelog)
- [Credits](#credits)

---

## Features

- **Body composition** — Weight, fat ratio, fat mass, fat-free mass, muscle mass, bone mass, hydration
- **Cardiovascular monitoring** — Heart rate, blood pressure (systolic/diastolic), SpO2, pulse wave velocity, VO2 max, vascular age, body temperature
- **Activity tracking** — Steps, distance, calories, elevation, activity durations (light/moderate/intense), active duration, heart rate zones 0–3
- **Sleep analysis** — Total sleep time, deep/light/REM sleep stages, wakeup count, sleep score, efficiency, latency, snoring episodes, breathing disturbances, night events, HR and respiration rate during sleep
- **Device status** — Battery level, device model name, last session timestamp
- **OAuth2 web authorization** — Built-in servlet at `/withings` for browser-based authorization (like HomeConnect binding)
- **Automatic token refresh** — Tokens are refreshed transparently before expiry
- **Multi-person support** — Multiple person things per account bridge
- **Configurable polling** — Separate intervals for body (default 15 min), activity (30 min), sleep (60 min), and device (60 min)
- **User filtering** — Measurements are filtered by user ID to avoid mixing data from shared scales

---

## Supported Devices

| Device Type | Supported Data |
|---|---|
| **Smart Scales** (Body, Body+, Body Comp, Body Scan) | Weight, fat ratio, fat/muscle/bone mass, hydration, heart rate, vascular age |
| **Blood Pressure Monitors** (BPM, BPM Connect, BPM Core) | Systolic/diastolic blood pressure, heart rate |
| **Activity Trackers** (ScanWatch, Steel HR, Move) | Steps, distance, calories, elevation, heart rate zones, active duration |
| **Sleep Monitors** (Sleep Analyzer, Sleep) | Sleep stages, score, snoring, heart/respiration rate during sleep |
| **Thermometers** (Thermo) | Body temperature |

> **Note:** Not all devices report all channels. Channels for data not supported by your device will remain `UNDEF` — this is harmless. For example, a ScanWatch reports activity and sleep data but does not report blood pressure. A smart scale reports body composition but not sleep data.

---

## Requirements

- openHAB 4.x or 5.x
- A [Withings developer account](https://developer.withings.com/) with a registered application
- At least one Withings health device linked to your Withings account
- Network access to the Withings Cloud API (`wbsapi.withings.net`)
- A publicly accessible callback URL for OAuth2 (or use pre-configured tokens)

---

## Installation

1. Build the binding from source (see [Building from Source](#building-from-source))
2. Copy the JAR to your openHAB addons folder:

```bash
sudo cp target/org.openhab.binding.withings-5.2.0-SNAPSHOT.jar /usr/share/openhab/addons/
```

3. The binding will be loaded automatically. Verify in **Settings → Add-ons → Bindings**.

---

## Configuration

### Withings Developer Application

1. Go to [Withings Developer Portal](https://developer.withings.com/)
2. Create a new application
3. Note your **Client ID** and **Client Secret**
4. Set the **Redirect URI** to your openHAB callback URL (e.g., `https://your-openhab.example.com/callback`)
5. Required scopes: `user.metrics,user.activity,user.sleepevents,user.info`

> **Important:** The binding requests all scopes automatically. If you previously authorized with an older version that used fewer scopes (e.g., only `user.metrics,user.activity`), you must **re-authorize** — see [Re-Authorization After Adding New Scopes](#re-authorization-after-adding-new-scopes).

### Bridge Configuration (Account)

The bridge represents your Withings API account and manages OAuth2 authentication.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `clientId` | text | Yes | — | OAuth2 Client ID from your Withings developer application |
| `clientSecret` | text | Yes | — | OAuth2 Client Secret from your Withings developer application |
| `redirectUri` | text | Yes | — | OAuth2 callback URL registered in your Withings developer application |
| `accessToken` | text | No | — | OAuth2 access token (leave empty for web-based authorization) |
| `refreshToken` | text | No | — | OAuth2 refresh token (leave empty for web-based authorization) |

**Thing file example:**

```java
Bridge withings:account:home "Withings Account" [
    clientId="YOUR_CLIENT_ID",
    clientSecret="YOUR_CLIENT_SECRET",
    redirectUri="https://your-openhab.example.com/callback"
]
```

### Thing Configuration (Person)

Each person thing represents an individual Withings user.

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `userId` | integer | Yes | — | Withings user ID (shown after OAuth2 authorization) |
| `pollingIntervalBody` | integer | No | 15 | Polling interval for body measurements (minutes) |
| `pollingIntervalActivity` | integer | No | 30 | Polling interval for activity data (minutes) |
| `pollingIntervalSleep` | integer | No | 60 | Polling interval for sleep data (minutes) |
| `pollingIntervalDevice` | integer | No | 60 | Polling interval for device status (minutes) |
| `deviceModel` | text | No | — | Filter device status channels to a specific device model. If empty, the most recently synced device is used. Example: `ScanWatch`, `Body+`, `BPM Connect` |

**Thing file example:**

```java
Bridge withings:account:home "Withings Account" [
    clientId="YOUR_CLIENT_ID",
    clientSecret="YOUR_CLIENT_SECRET",
    redirectUri="https://your-openhab.example.com/callback"
] {
    Thing person john "John" [userId=12345678, pollingIntervalBody=15, pollingIntervalActivity=30, pollingIntervalSleep=60, pollingIntervalDevice=60]
    Thing person jane "Jane" [userId=87654321]
}
```

---

## OAuth2 Authorization

The binding supports two authorization methods.

### Re-Authorization After Adding New Scopes

> **When is this required?**
> If you upgrade from a version of the binding that did not include the `device` channel group (added April 2026), your existing OAuth2 token was issued with a limited set of scopes (`user.metrics,user.activity`). The binding now requires `user.sleepevents` and `user.info` as well.
>
> **Symptoms:** Device channels (`device#battery`, `device#model`, `device#lastSession`) return `UNDEF` or the log shows HTTP 403 errors when polling device data.
>
> **Fix:** Force a complete re-authorization via the `/withings` page. The existing stored tokens will be discarded and replaced by a new grant with all required scopes.

**Steps to re-authorize:**

1. Open `http(s)://your-openhab:8080/withings` in a browser
2. Click **"Authorize with Withings"** next to your bridge
3. Log in to Withings and grant access (ensure all permissions are ticked)
4. You will be redirected back and the bridge will go `ONLINE`
5. All channel groups (including `device`) will now populate

You only need to do this once — tokens are stored persistently and survive reboots.

### Web-Based Authorization (Recommended)

1. Configure your bridge with `clientId`, `clientSecret`, and `redirectUri` — leave `accessToken` and `refreshToken` empty
2. The bridge will go to status `OFFLINE / CONFIGURATION_PENDING`
3. Open `http(s)://your-openhab:8080/withings` in a browser
4. Click **"Authorize with Withings"** next to your bridge
5. Log in to Withings and grant access
6. You will be redirected back and the bridge will go `ONLINE`
7. Note the **User ID** shown on the success page — use it for your person thing configuration

### Token Persistence (Reboot Survival)

The binding uses openHAB's `StorageService` to persist OAuth2 tokens independently of the thing configuration. This is critical because `.things` text files are read-only — `updateConfiguration()` only updates in-memory state and is lost on restart.

**How it works:**
1. On successful authorization or token refresh → tokens are saved to `/var/lib/openhab/jsondb/withings.tokens.<bridgeUID>.json`
2. On `initialize()` → tokens are loaded from storage first, falling back to `.things` config values
3. Each bridge gets its own isolated storage key

This means you only need to authorize once — the binding will automatically reconnect after reboots using the persisted tokens.

### Pre-Configured Tokens

If you already have tokens (e.g., from a manual OAuth2 flow), you can set them directly:

```java
Bridge withings:account:home "Withings Account" [
    clientId="YOUR_CLIENT_ID",
    clientSecret="YOUR_CLIENT_SECRET",
    redirectUri="https://your-openhab.example.com/callback",
    accessToken="YOUR_ACCESS_TOKEN",
    refreshToken="YOUR_REFRESH_TOKEN"
]
```

### Reverse Proxy Setup

If your openHAB is behind a reverse proxy (e.g., nginx), route the callback URL to the binding's servlet:

```nginx
location /callback {
    proxy_pass http://127.0.0.1:8080/withings;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

This maps the public `/callback` URL to the binding's internal `/withings` servlet endpoint.

---

## Channels

All channels are read-only. The binding provides five channel groups.

### Body Measurements

Channel group: `body`

| Channel ID | Type | Description |
|---|---|---|
| `weight` | `Number:Mass` | Body weight (kg) |
| `fatRatio` | `Number` | Body fat percentage (%) |
| `fatMass` | `Number:Mass` | Fat mass (kg) |
| `fatFreeMass` | `Number:Mass` | Fat-free / lean body mass (kg) |
| `muscleMass` | `Number:Mass` | Muscle mass (kg) |
| `hydration` | `Number:Mass` | Body hydration (kg) |
| `boneMass` | `Number:Mass` | Bone mass (kg) |
| `lastMeasurement` | `DateTime` | Timestamp of the most recent measurement |

**Devices:** Body, Body+, Body Comp, Body Scan

### Cardiovascular

Channel group: `cardiovascular`

| Channel ID | Type | Description |
|---|---|---|
| `heartPulse` | `Number` | Heart rate (bpm) |
| `systolicBP` | `Number:Pressure` | Systolic blood pressure (mmHg) |
| `diastolicBP` | `Number:Pressure` | Diastolic blood pressure (mmHg) |
| `pulseWaveVelocity` | `Number` | Pulse wave velocity (m/s) |
| `vo2Max` | `Number` | VO2 Max — maximum oxygen consumption (ml/min/kg) |
| `vascularAge` | `Number` | Estimated vascular age (years) |
| `spo2` | `Number` | Blood oxygen saturation (%) |
| `temperature` | `Number:Temperature` | Body temperature (°C) |

**Devices:** BPM Connect, BPM Core, Body Scan, ScanWatch, Thermo

### Activity

Channel group: `activity`

| Channel ID | Type | Description |
|---|---|---|
| `steps` | `Number` | Number of steps taken today |
| `distance` | `Number:Length` | Distance travelled today (m) |
| `calories` | `Number:Energy` | Active calories burned today (J) |
| `totalCalories` | `Number:Energy` | Total calories burned today — active + passive (J) |
| `elevation` | `Number` | Floors climbed today |
| `softActivity` | `Number:Time` | Duration of light activities (s) |
| `moderateActivity` | `Number:Time` | Duration of moderate activities (s) |
| `intenseActivity` | `Number:Time` | Duration of intense activities (s) |
| `activeDuration` | `Number:Time` | Total active duration today (s) — sum of all non-rest activity |
| `hrAverage` | `Number` | Average heart rate during the day (bpm) |
| `hrMin` | `Number` | Minimum heart rate during the day (bpm) |
| `hrMax` | `Number` | Maximum heart rate during the day (bpm) |
| `hrZone0` | `Number:Time` | Time in HR zone 0 (rest) today (s) |
| `hrZone1` | `Number:Time` | Time in HR zone 1 (light) today (s) |
| `hrZone2` | `Number:Time` | Time in HR zone 2 (moderate) today (s) |
| `hrZone3` | `Number:Time` | Time in HR zone 3 (intense) today (s) |

**Devices:** ScanWatch, Steel HR, Move, Go

> **HR Zones:** Withings defines zones by heart rate relative to max HR. Zone 0 = rest/very light; Zone 1 = light (fat burn); Zone 2 = moderate (aerobic); Zone 3 = intense (anaerobic/peak). Values are in seconds of cumulative duration per day.

### Sleep

Channel group: `sleep`

| Channel ID | Type | Description |
|---|---|---|
| `totalSleepTime` | `Number:Time` | Total time spent asleep (s) |
| `totalTimeInBed` | `Number:Time` | Total time spent in bed (asleep + awake) (s) |
| `deepSleepDuration` | `Number:Time` | Duration of deep sleep (s) |
| `lightSleepDuration` | `Number:Time` | Duration of light sleep (s) |
| `remSleepDuration` | `Number:Time` | Duration of REM sleep (s) |
| `nbRemEpisodes` | `Number` | Number of distinct REM sleep episodes |
| `wakeupCount` | `Number` | Number of times woken up during the night |
| `wakeupDuration` | `Number:Time` | Total time spent awake during the night (s) |
| `outOfBedCount` | `Number` | Number of times out of bed during the night |
| `timeToSleep` | `Number:Time` | Time spent in bed before falling asleep (s) |
| `sleepLatency` | `Number:Time` | Time from lights-out to first sleep onset (s) |
| `wakeupLatency` | `Number:Time` | Time spent in bed after final wakeup before rising (s) |
| `sleepScore` | `Number` | Overall sleep quality score (0–100) |
| `sleepEfficiency` | `Number` | Sleep efficiency — percentage of bed time spent asleep (%) |
| `snoring` | `Number:Time` | Total snoring duration during sleep (s) |
| `snoringEpisodes` | `Number` | Number of distinct snoring episodes |
| `nightEvents` | `Number` | Total number of detected night events |
| `breathingDisturbances` | `Number` | Breathing disturbance index |
| `sleepHrAverage` | `Number` | Average heart rate during sleep (bpm) |
| `sleepHrMin` | `Number` | Minimum heart rate during sleep (bpm) |
| `sleepHrMax` | `Number` | Maximum heart rate during sleep (bpm) |
| `sleepRrAverage` | `Number` | Average respiration rate during sleep (brpm) |
| `sleepRrMin` | `Number` | Minimum respiration rate during sleep (brpm) |
| `sleepRrMax` | `Number` | Maximum respiration rate during sleep (brpm) |

**Devices:** Sleep Analyzer, ScanWatch, Steel HR

> **Sleep Latency vs Time to Sleep:** `sleepLatency` is the clinical measure from when you intend to sleep to first sleep onset. `timeToSleep` is derived from the Withings API `tts` field (time-to-sleep from lying down). These may differ slightly.

### Device

Channel group: `device`

| Channel ID | Type | Description |
|---|---|---|
| `battery` | `String` | Battery level — reported as a percentage string (e.g., `"75%"`) or status (`"low"`) |
| `model` | `String` | Device model name (e.g., `"ScanWatch 2"`) |
| `lastSession` | `DateTime` | Timestamp of the last sync session with the device |

**Devices:** All Withings devices

> **Re-authorization required:** The `device` channel group requires the `user.info` OAuth2 scope. If you are upgrading from a version without device support, you must re-authorize via the `/withings` page to grant this additional scope. See [Re-Authorization After Adding New Scopes](#re-authorization-after-adding-new-scopes).

---

## Items Example

Minimal example — body composition only (smart scale):

```java
Group gWithings "Withings Health" <body> ["Equipment"]

Number:Mass   Withings_Weight         "Weight [%.1f kg]"                              <scale>  (gWithings) ["Measurement"] { channel="withings:person:home:john:body#weight" }
Number        Withings_Fat_Ratio      "Fat Ratio [%.1f %%]"                           <body>   (gWithings) ["Measurement"] { channel="withings:person:home:john:body#fatRatio" }
Number:Mass   Withings_Fat_Mass       "Fat Mass [%.1f kg]"                            <body>   (gWithings) ["Measurement"] { channel="withings:person:home:john:body#fatMass" }
Number:Mass   Withings_Fat_Free_Mass  "Fat Free Mass [%.1f kg]"                       <body>   (gWithings) ["Measurement"] { channel="withings:person:home:john:body#fatFreeMass" }
Number:Mass   Withings_Muscle_Mass    "Muscle Mass [%.1f kg]"                         <body>   (gWithings) ["Measurement"] { channel="withings:person:home:john:body#muscleMass" }
Number:Mass   Withings_Bone_Mass      "Bone Mass [%.2f kg]"                           <body>   (gWithings) ["Measurement"] { channel="withings:person:home:john:body#boneMass" }
Number:Mass   Withings_Hydration      "Hydration [%.1f kg]"                           <body>   (gWithings) ["Measurement"] { channel="withings:person:home:john:body#hydration" }
DateTime      Withings_Last           "Last Measurement [%1$td-%1$tm-%1$tY %1$tH:%1$tM]" <time> (gWithings) ["Status"]  { channel="withings:person:home:john:body#lastMeasurement" }
```

Full example — all channel groups (ScanWatch + smart scale):

```java
Group gWithings        "Withings Health"   <body>   ["Equipment"]
Group gWithings_Body   "Body"              <scale>  (gWithings)
Group gWithings_Cardio "Cardiovascular"    <heart>  (gWithings)
Group gWithings_Act    "Activity"          <motion> (gWithings)
Group gWithings_Sleep  "Sleep"             <moon>   (gWithings)
Group gWithings_Device "Device"            <shield> (gWithings)

// --- Body ---
Number:Mass   Withings_Weight         "Weight [%.1f kg]"                              <scale>       (gWithings_Body) ["Measurement"] { channel="withings:person:home:john:body#weight" }
Number        Withings_Fat_Ratio      "Fat [%.1f %%]"                                 <body>        (gWithings_Body) ["Measurement"] { channel="withings:person:home:john:body#fatRatio" }
Number:Mass   Withings_Muscle_Mass    "Muscle Mass [%.1f kg]"                         <body>        (gWithings_Body) ["Measurement"] { channel="withings:person:home:john:body#muscleMass" }
Number:Mass   Withings_Bone_Mass      "Bone Mass [%.2f kg]"                           <body>        (gWithings_Body) ["Measurement"] { channel="withings:person:home:john:body#boneMass" }
Number:Mass   Withings_Hydration      "Hydration [%.1f kg]"                           <body>        (gWithings_Body) ["Measurement"] { channel="withings:person:home:john:body#hydration" }
DateTime      Withings_Last           "Last Measurement [%1$td-%1$tm %1$tH:%1$tM]"   <time>        (gWithings_Body) ["Status"]      { channel="withings:person:home:john:body#lastMeasurement" }

// --- Cardiovascular ---
Number        Withings_Heart_Pulse    "Heart Rate [%d bpm]"                           <heart>       (gWithings_Cardio) ["HeartRate"]   { channel="withings:person:home:john:cardiovascular#heartPulse" }
Number:Pressure Withings_Systolic     "Systolic BP [%d mmHg]"                         <heart>       (gWithings_Cardio) ["Measurement"] { channel="withings:person:home:john:cardiovascular#systolicBP" }
Number:Pressure Withings_Diastolic    "Diastolic BP [%d mmHg]"                        <heart>       (gWithings_Cardio) ["Measurement"] { channel="withings:person:home:john:cardiovascular#diastolicBP" }
Number        Withings_SpO2           "SpO2 [%.1f %%]"                                <heart>       (gWithings_Cardio) ["Measurement"] { channel="withings:person:home:john:cardiovascular#spo2" }
Number        Withings_VO2Max         "VO2 Max [%.1f ml/min/kg]"                      <heart>       (gWithings_Cardio) ["Measurement"] { channel="withings:person:home:john:cardiovascular#vo2Max" }

// --- Activity ---
Number        Withings_Steps          "Steps [%d]"                                    <motion>      (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#steps" }
Number:Length Withings_Distance       "Distance [%.0f m]"                             <motion>      (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#distance" }
Number:Energy Withings_Calories       "Active Calories [%.0f kcal]"                   <fire>        (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#calories" }
Number:Time   Withings_Active_Dur     "Active Duration [%.0f min]"                    <motion>      (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#activeDuration" }
Number        Withings_HR_Avg         "HR Average [%d bpm]"                           <heart>       (gWithings_Act) ["HeartRate"]   { channel="withings:person:home:john:activity#hrAverage" }
Number:Time   Withings_HR_Zone_0      "HR Zone 0 Rest [%.0f min]"                     <heart>       (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#hrZone0" }
Number:Time   Withings_HR_Zone_1      "HR Zone 1 Light [%.0f min]"                    <heart>       (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#hrZone1" }
Number:Time   Withings_HR_Zone_2      "HR Zone 2 Moderate [%.0f min]"                 <heart>       (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#hrZone2" }
Number:Time   Withings_HR_Zone_3      "HR Zone 3 Intense [%.0f min]"                  <heart>       (gWithings_Act) ["Measurement"] { channel="withings:person:home:john:activity#hrZone3" }

// --- Sleep ---
Number:Time   Withings_TotalSleep     "Total Sleep [%.0f min]"                        <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#totalSleepTime" }
Number:Time   Withings_TimeInBed      "Time in Bed [%.0f min]"                        <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#totalTimeInBed" }
Number:Time   Withings_DeepSleep      "Deep Sleep [%.0f min]"                         <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#deepSleepDuration" }
Number:Time   Withings_LightSleep     "Light Sleep [%.0f min]"                        <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#lightSleepDuration" }
Number:Time   Withings_REMSleep       "REM Sleep [%.0f min]"                          <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#remSleepDuration" }
Number        Withings_SleepScore     "Sleep Score [%d]"                              <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#sleepScore" }
Number        Withings_SleepEff       "Sleep Efficiency [%.1f %%]"                    <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#sleepEfficiency" }
Number        Withings_WakeupCount    "Wakeups [%d]"                                  <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#wakeupCount" }
Number        Withings_OutOfBed       "Out of Bed [%d]"                               <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#outOfBedCount" }
Number:Time   Withings_SleepLatency   "Sleep Latency [%.0f min]"                      <time>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#sleepLatency" }
Number:Time   Withings_WakeupLatency  "Wakeup Latency [%.0f min]"                     <time>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#wakeupLatency" }
Number:Time   Withings_Snoring        "Snoring [%.0f min]"                            <soundvolume> (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#snoring" }
Number        Withings_SnoringEp      "Snoring Episodes [%d]"                         <soundvolume> (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#snoringEpisodes" }
Number        Withings_BreathingDisturb "Breathing Disturbances [%.1f]"               <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#breathingDisturbances" }
Number        Withings_NightEvents     "Night Events [%d]"                            <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#nightEvents" }
Number        Withings_SleepHR_Avg    "Sleep HR Avg [%d bpm]"                         <heart>       (gWithings_Sleep) ["HeartRate"]   { channel="withings:person:home:john:sleep#sleepHrAverage" }
Number        Withings_SleepRR_Avg    "Sleep RR Avg [%d brpm]"                        <moon>        (gWithings_Sleep) ["Measurement"] { channel="withings:person:home:john:sleep#sleepRrAverage" }

// --- Device ---
String        Withings_Battery        "Battery [%s]"                                  <battery>     (gWithings_Device) ["Status"] { channel="withings:person:home:john:device#battery" }
String        Withings_Model          "Model [%s]"                                    <shield>      (gWithings_Device) ["Status"] { channel="withings:person:home:john:device#model" }
DateTime      Withings_LastSession    "Last Session [%1$td-%1$tm %1$tH:%1$tM]"        <time>        (gWithings_Device) ["Status"] { channel="withings:person:home:john:device#lastSession" }
```

---

## Sitemap Example

```perl
sitemap withings label="Withings Health" {
    Frame label="Body Composition" {
        Text item=Withings_Weight
        Text item=Withings_Fat_Ratio
        Text item=Withings_Muscle_Mass
        Text item=Withings_Bone_Mass
        Text item=Withings_Hydration
        Text item=Withings_Last
    }
    Frame label="Cardiovascular" {
        Text item=Withings_Heart_Pulse
        Text item=Withings_Systolic
        Text item=Withings_Diastolic
        Text item=Withings_SpO2
        Text item=Withings_VO2Max
    }
    Frame label="Activity" {
        Text item=Withings_Steps
        Text item=Withings_Distance
        Text item=Withings_Calories
        Text item=Withings_Active_Dur
        Text item=Withings_HR_Avg
        Text item=Withings_HR_Zone_0
        Text item=Withings_HR_Zone_1
        Text item=Withings_HR_Zone_2
        Text item=Withings_HR_Zone_3
    }
    Frame label="Sleep" {
        Text item=Withings_TotalSleep
        Text item=Withings_TimeInBed
        Text item=Withings_SleepScore
        Text item=Withings_SleepEff
        Text item=Withings_DeepSleep
        Text item=Withings_LightSleep
        Text item=Withings_REMSleep
        Text item=Withings_WakeupCount
        Text item=Withings_Snoring
        Text item=Withings_SnoringEp
        Text item=Withings_BreathingDisturb
        Text item=Withings_SleepHR_Avg
        Text item=Withings_SleepRR_Avg
    }
    Frame label="Device" {
        Text item=Withings_Model
        Text item=Withings_Battery
        Text item=Withings_LastSession
    }
}
```

---

## Rules Examples

### Weight Change Alert

Notify when weight changes by more than 1 kg since the last measurement:

```javascript
// automation/js/withings-weight-alert.js
rules.when()
    .item("Withings_Weight").changed()
    .then(event => {
        const weight = items.getItem("Withings_Weight").numericState;
        const previous = parseFloat(event.oldState);
        if (!isNaN(previous)) {
            const diff = weight - previous;
            if (Math.abs(diff) > 1.0) {
                actions.NotificationAction.sendBroadcastNotification(
                    `Weight change: ${diff > 0 ? '+' : ''}${diff.toFixed(1)} kg (now ${weight.toFixed(1)} kg)`
                );
            }
        }
    })
    .build("Withings Weight Change Alert");
```

### Sleep Quality Notification

Send a morning notification with last night's sleep summary:

```javascript
// automation/js/withings-sleep-notify.js
rules.when()
    .item("Withings_SleepScore").changed()
    .then(() => {
        const score  = items.getItem("Withings_SleepScore").numericState;
        const deep   = Math.round(items.getItem("Withings_DeepSleep").numericState / 60);
        const rem    = Math.round(items.getItem("Withings_REMSleep").numericState / 60);
        const snores = items.getItem("Withings_SnoringEp").numericState;

        let quality = score >= 70 ? "good" : score >= 50 ? "fair" : "poor";
        console.log(`Sleep: score ${score} (${quality}) | Deep ${deep} min | REM ${rem} min | Snoring ${snores} episodes`);

        actions.NotificationAction.sendBroadcastNotification(
            `Sleep last night: score ${score} (${quality}), ${deep} min deep, ${rem} min REM`
        );
    })
    .build("Withings Sleep Summary");
```

### HR Zone Daily Summary

Log cumulative heart rate zone distribution:

```javascript
// automation/js/withings-hrzone-summary.js
rules.when()
    .item("Withings_HR_Zone_3").changed()
    .then(() => {
        const z0 = Math.round(items.getItem("Withings_HR_Zone_0").numericState / 60);
        const z1 = Math.round(items.getItem("Withings_HR_Zone_1").numericState / 60);
        const z2 = Math.round(items.getItem("Withings_HR_Zone_2").numericState / 60);
        const z3 = Math.round(items.getItem("Withings_HR_Zone_3").numericState / 60);
        console.log(`HR Zones today — Rest: ${z0} min | Light: ${z1} min | Moderate: ${z2} min | Intense: ${z3} min`);
    })
    .build("Withings HR Zone Summary");
```

### Battery Low Alert

Alert when the device battery is reported as low:

```javascript
// automation/js/withings-battery-alert.js
rules.when()
    .item("Withings_Battery").changed()
    .then(() => {
        const battery = items.getItem("Withings_Battery").state;
        if (battery && battery.toLowerCase().includes("low")) {
            actions.NotificationAction.sendBroadcastNotification(
                `Withings ${items.getItem("Withings_Model").state} battery is low — please charge`
            );
        }
    })
    .build("Withings Battery Low Alert");
```

---

## Persistence

Recommended persistence configuration for trending and charting:

```yaml
// persistence/influxdb.persist (or rrd4j.persist for charts)

// Body — infrequent changes, persist on each new value
Withings_Weight           : strategy = everyChange, restoreOnStartup
Withings_Fat_Ratio        : strategy = everyChange, restoreOnStartup
Withings_Muscle_Mass      : strategy = everyChange, restoreOnStartup
Withings_Bone_Mass        : strategy = everyChange, restoreOnStartup
Withings_Hydration        : strategy = everyChange, restoreOnStartup

// Sleep — nightly values
Withings_SleepScore       : strategy = everyChange, restoreOnStartup
Withings_TotalSleep       : strategy = everyChange, restoreOnStartup
Withings_DeepSleep        : strategy = everyChange, restoreOnStartup
Withings_REMSleep         : strategy = everyChange, restoreOnStartup
Withings_SleepEff         : strategy = everyChange, restoreOnStartup
Withings_SnoringEp        : strategy = everyChange, restoreOnStartup
Withings_BreathingDisturb : strategy = everyChange, restoreOnStartup

// Activity — daily values
Withings_Steps            : strategy = everyChange, restoreOnStartup
Withings_Calories         : strategy = everyChange, restoreOnStartup
Withings_HR_Zone_3        : strategy = everyChange, restoreOnStartup
```

---

## Building from Source

The binding is built as part of the [openHAB Add-ons](https://github.com/openhab/openhab-addons) Maven project.

### Prerequisites

- Java 21+
- Maven 3.9+
- openHAB Add-ons repository cloned

### Build Steps

```bash
# Clone the add-ons repository (if not already done)
git clone https://github.com/openhab/openhab-addons.git
cd openhab-addons

# Copy the binding source into the bundles directory
cp -r /path/to/withings-binding bundles/org.openhab.binding.withings

# Build the binding
cd bundles/org.openhab.binding.withings
mvn clean package -DskipTests

# Deploy the JAR
sudo cp target/org.openhab.binding.withings-*.jar /usr/share/openhab/addons/
```

> **Note:** The binding must be built within the openHAB Add-ons repository where all dependencies (core APIs, OSGi annotations, etc.) are available.

---

## Architecture

```
withings-binding/
├── pom.xml                                    # Maven build configuration
└── src/main/
    ├── feature/feature.xml                    # Karaf feature descriptor
    ├── history/dependencies.xml               # Dependency history
    ├── java/org/openhab/binding/withings/
    │   ├── WithingsBindingConstants.java       # Binding IDs, channel IDs, API URLs, measure types
    │   ├── dto/
    │   │   └── WithingsApiResponse.java        # DTO for API JSON responses (Gson)
    │   ├── handler/
    │   │   ├── WithingsAccountConfiguration.java  # Bridge config POJO
    │   │   ├── WithingsAccountHandler.java        # Bridge handler — OAuth2 lifecycle
    │   │   ├── WithingsApiClient.java             # HTTP client — API calls + token refresh
    │   │   ├── WithingsHandlerFactory.java        # OSGi factory — creates handlers
    │   │   ├── WithingsPersonConfiguration.java   # Thing config POJO (includes pollingIntervalDevice)
    │   │   └── WithingsPersonHandler.java         # Thing handler — polling + channel updates
    │   └── servlet/
    │       └── WithingsServlet.java               # OAuth2 web UI at /withings
    └── resources/OH-INF/
        ├── addon/addon.xml                    # Binding metadata
        └── thing/thing-types.xml              # Bridge, thing, channel group, and channel type definitions
```

### Key Design Decisions

- **Manual OAuth2** — Withings uses a non-standard OAuth2 flow (`action=requesttoken` form param instead of standard token endpoint). The binding handles this directly rather than using openHAB's OAuth2 service.
- **StorageService token persistence** — Tokens are stored in openHAB's JSON database via `StorageService`, not in thing configuration. This ensures tokens survive reboots when using `.things` text file configuration (where `updateConfiguration()` is not persisted to disk).
- **Servlet-based authorization** — Modeled after the HomeConnect binding. The `WithingsServlet` is an OSGi singleton component registered at `/withings` via `HttpService`. Bridge handlers register/unregister with the servlet for callback routing.
- **Four polling jobs** — Body measurements, activity, sleep, and device are polled on separate schedules since they update at different frequencies.
- **Latest-value-only updates** — When fetching measurements, only the most recent value per measure type is used to avoid "scrolling" through historical data on each poll.
- **Percentage normalization** — Withings returns fat ratio and SpO2 as fractions (0–1). The binding converts to percentages (0–100) for display.
- **Scope management** — The full required scope is `user.metrics,user.activity,user.sleepevents,user.info`. All four are needed to populate all channel groups. Missing scopes cause HTTP 403 on the affected API endpoints — re-authorize to fix.

---

## Troubleshooting

### Bridge goes OFFLINE / CONFIGURATION_PENDING

Normal when tokens are not configured and no persisted tokens exist. Open `http://your-openhab:8080/withings` and authorize.

### Bridge goes OFFLINE after reboot

Check that `/var/lib/openhab/jsondb/` contains a `withings.tokens.*.json` file. If missing, re-authorize. The binding version must include the `StorageService` persistence fix (April 2026+).

### Token refresh fails (status 401)

Withings refresh tokens can expire after extended inactivity. Re-authorize via the `/withings` page.

### Device channels show UNDEF (battery, model, lastSession)

The `device` channel group requires the `user.info` OAuth2 scope. If you authorized with an older version, re-authorize to grant the new scope — see [Re-Authorization After Adding New Scopes](#re-authorization-after-adding-new-scopes).

### Activity and sleep channels show UNDEF

These channels require a device that tracks activity/sleep (ScanWatch, Steel HR, Sleep Analyzer). A smart scale alone only populates body and cardiovascular channels. Some channels may also remain `UNDEF` if your specific device does not report that metric — this is normal and harmless.

### Measurements from other users appear

Configure the `userId` parameter on the person thing. The binding filters measurements by user ID. Find your user ID on the success page after OAuth2 authorization.

### Log verbosity

```
log:set DEBUG org.openhab.binding.withings   # Enable debug logging
log:set INFO org.openhab.binding.withings    # Return to normal
```

### Withings API rate limits

Avoid polling intervals below 5 minutes. The defaults (15/30/60 min) are well within Withings API rate limits.

---

## License

This binding is licensed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).

```
Copyright (c) 2010-2026 Contributors to the openHAB project

See the NOTICE file(s) distributed with this work for additional
information.

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0

SPDX-License-Identifier: EPL-2.0
```

---

## Changelog

### v1.3.0 — April 2026
- **Fix:** Device status channels (`battery`, `model`, `lastSession`) now show the **most recently synced** device instead of always the first in the API list
- **New:** Optional `deviceModel` parameter on person thing — pin device status to a specific device by model name substring match (e.g. `deviceModel="ScanWatch"`)
- Fallback to most recently synced device if `deviceModel` is set but no match found (with warning in log)

### v1.2.0 — April 2026
- **New:** `device` channel group — `battery`, `model`, `lastSession` for all Withings devices
- **New:** Extended sleep channels — `nbRemEpisodes`, `outOfBedCount`, `timeToSleep`, `sleepLatency`, `wakeupLatency`, `sleepEfficiency`, `nightEvents`, `sleepHrMin/Max`, `sleepRrAverage/Min/Max`
- **New:** Extended activity channels — `totalCalories`, `hrMin`, `hrMax`, `hrZone0–3`
- Added `user.sleepevents` and `user.info` OAuth2 scopes (re-authorization required when upgrading from v1.1.x)

### v1.1.0 — March 2026
- **New:** Token persistence via `StorageService` — tokens survive reboots without needing manual re-entry in `.things` files
- **New:** Multi-person support — multiple `person` things per bridge, filtered by Withings user ID
- **Fix:** Stale token handling — bridge recovers gracefully from 401 by triggering re-authorization

### v1.0.0 — March 2026
- Initial release
- Body composition channels (weight, fat, muscle, bone, hydration)
- Cardiovascular channels (heart rate, blood pressure, SpO2, VO2 max, vascular age)
- Activity channels (steps, distance, calories, elevation, activity durations)
- Sleep channels (sleep stages, wakeup count, snoring, breathing disturbances, sleep score)
- OAuth2 web authorization servlet at `/withings`
- Configurable polling intervals per channel group

---

## Credits

**Author:** [Nanna Agesen](https://github.com/Prinsessen)

Built as a community binding for the [openHAB](https://www.openhab.org/) smart home platform.
Feedback and contributions welcome via [GitHub Issues](https://github.com/Prinsessen/openhab-withings-binding/issues).
