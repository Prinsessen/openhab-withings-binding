# openHAB Withings Health Binding

A comprehensive openHAB binding for **Withings** health devices — smart scales, blood pressure monitors, activity trackers, sleep monitors, and thermometers. Retrieves body measurements, cardiovascular data, activity metrics, and sleep analysis via the Withings Cloud API with full OAuth2 authorization support.

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
  - [Web-Based Authorization (Recommended)](#web-based-authorization-recommended)
  - [Pre-Configured Tokens](#pre-configured-tokens)
  - [Reverse Proxy Setup](#reverse-proxy-setup)
- [Channels](#channels)
  - [Body Measurements](#body-measurements)
  - [Cardiovascular](#cardiovascular)
  - [Activity](#activity)
  - [Sleep](#sleep)
- [Items Example](#items-example)
- [Sitemap Example](#sitemap-example)
- [Rules Examples](#rules-examples)
- [Persistence](#persistence)
- [Building from Source](#building-from-source)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)
- [Credits](#credits)
- [License](#license)

---

## Features

- **Body composition** — Weight, fat ratio, fat mass, fat-free mass, muscle mass, bone mass, hydration
- **Cardiovascular monitoring** — Heart rate, blood pressure (systolic/diastolic), SpO2, pulse wave velocity, VO2 max, vascular age, body temperature
- **Activity tracking** — Steps, distance, calories, elevation, activity durations (light/moderate/intense), heart rate zones
- **Sleep analysis** — Total sleep time, deep/light/REM sleep, wakeup count, sleep score, snoring, heart rate during sleep, respiration rate
- **OAuth2 web authorization** — Built-in servlet at `/withings` for browser-based authorization (like HomeConnect binding)
- **Automatic token refresh** — Tokens are refreshed transparently before expiry
- **Multi-person support** — Multiple person things per account bridge
- **Configurable polling** — Separate intervals for body (default 15 min), activity (30 min), and sleep (60 min)
- **User filtering** — Measurements are filtered by user ID to avoid mixing data from shared scales

---

## Supported Devices

| Device Type | Supported Data |
|---|---|
| **Smart Scales** (Body, Body+, Body Comp, Body Scan) | Weight, fat ratio, fat/muscle/bone mass, hydration, heart rate, vascular age |
| **Blood Pressure Monitors** (BPM, BPM Connect, BPM Core) | Systolic/diastolic blood pressure, heart rate |
| **Activity Trackers** (ScanWatch, Steel HR, Move) | Steps, distance, calories, elevation, heart rate, activity durations |
| **Sleep Monitors** (Sleep Analyzer, Sleep) | Sleep stages, sleep score, snoring, heart/respiration rate during sleep |
| **Thermometers** (Thermo) | Body temperature |

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
cp target/org.openhab.binding.withings-5.2.0-SNAPSHOT.jar /usr/share/openhab/addons/
```

3. The binding will be loaded automatically. Verify in **Settings → Add-ons → Bindings**.

---

## Configuration

### Withings Developer Application

1. Go to [Withings Developer Portal](https://developer.withings.com/)
2. Create a new application
3. Note your **Client ID** and **Client Secret**
4. Set the **Redirect URI** to your openHAB callback URL (e.g., `https://your-openhab.example.com/callback`)
5. Required scopes: `user.metrics` and `user.activity`

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

**Thing file example:**

```java
Bridge withings:account:home "Withings Account" [
    clientId="YOUR_CLIENT_ID",
    clientSecret="YOUR_CLIENT_SECRET",
    redirectUri="https://your-openhab.example.com/callback"
] {
    Thing person john "John" [userId=12345678, pollingIntervalBody=15, pollingIntervalActivity=30, pollingIntervalSleep=60]
    Thing person jane "Jane" [userId=87654321]
}
```

---

## OAuth2 Authorization

The binding supports two authorization methods.

### Web-Based Authorization (Recommended)

1. Configure your bridge with `clientId`, `clientSecret`, and `redirectUri` — leave `accessToken` and `refreshToken` empty
2. The bridge will go to status `OFFLINE / CONFIGURATION_PENDING`
3. Open `http(s)://your-openhab:8080/withings` in a browser
4. Click **"Authorize with Withings"** next to your bridge
5. Log in to Withings and grant access
6. You will be redirected back and the bridge will go `ONLINE`
7. Note the **User ID** shown on the success page — use it for your person thing configuration

Tokens are persisted in openHAB's JSON database (`StorageService`) and survive reboots — even when the bridge is defined in a `.things` text file. Tokens are refreshed automatically before expiry.

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

All channels are read-only. The binding provides four channel groups.

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
| `hrAverage` | `Number` | Average heart rate during the day (bpm) |
| `hrMin` | `Number` | Minimum heart rate during the day (bpm) |
| `hrMax` | `Number` | Maximum heart rate during the day (bpm) |

**Devices:** ScanWatch, Steel HR, Move, Go

### Sleep

Channel group: `sleep`

| Channel ID | Type | Description |
|---|---|---|
| `totalSleepTime` | `Number:Time` | Total time spent asleep (s) |
| `deepSleepDuration` | `Number:Time` | Duration of deep sleep (s) |
| `lightSleepDuration` | `Number:Time` | Duration of light sleep (s) |
| `remSleepDuration` | `Number:Time` | Duration of REM sleep (s) |
| `wakeupCount` | `Number` | Number of times woken up during the night |
| `wakeupDuration` | `Number:Time` | Total time spent awake during the night (s) |
| `timeToSleep` | `Number:Time` | Time spent in bed before falling asleep (s) |
| `timeToWakeup` | `Number:Time` | Time spent in bed after waking up (s) |
| `sleepScore` | `Number` | Overall sleep quality score (0–100) |
| `snoring` | `Number:Time` | Total snoring duration during sleep (s) |
| `sleepHrAverage` | `Number` | Average heart rate during sleep (bpm) |
| `sleepHrMin` | `Number` | Minimum heart rate during sleep (bpm) |
| `sleepHrMax` | `Number` | Maximum heart rate during sleep (bpm) |
| `sleepRrAverage` | `Number` | Average respiration rate during sleep (brpm) |
| `sleepRrMin` | `Number` | Minimum respiration rate during sleep (brpm) |
| `sleepRrMax` | `Number` | Maximum respiration rate during sleep (brpm) |

**Devices:** Sleep Analyzer, ScanWatch, Steel HR

---

## Items Example

Minimal example using only body composition channels (smart scale):

```java
Group gWithings "Withings Health" <body> ["Equipment"]

// Body Composition
Number:Mass   Withings_Weight         "Weight [%.1f kg]"           <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#weight" }
Number        Withings_Fat_Ratio      "Fat Ratio [%.1f %%]"        <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#fatRatio" }
Number:Mass   Withings_Fat_Mass       "Fat Mass [%.1f kg]"         <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#fatMass" }
Number:Mass   Withings_Fat_Free_Mass  "Fat Free Mass [%.1f kg]"    <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#fatFreeMass" }
Number:Mass   Withings_Muscle_Mass    "Muscle Mass [%.1f kg]"      <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#muscleMass" }
Number:Mass   Withings_Bone_Mass      "Bone Mass [%.2f kg]"        <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#boneMass" }
Number:Mass   Withings_Hydration      "Hydration [%.1f kg]"        <body> (gWithings) ["Measurement"] { channel="withings:person:home:john:body#hydration" }
DateTime      Withings_Last           "Last Measurement [%1$td-%1$tm-%1$tY %1$tH:%1$tM]" <time> (gWithings) ["Status"] { channel="withings:person:home:john:body#lastMeasurement" }
```

Full example including all channel groups:

```java
// Cardiovascular
Number        Withings_Heart_Pulse    "Heart Rate [%d bpm]"        <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#heartPulse" }
Number:Pressure Withings_Systolic     "Systolic BP [%d mmHg]"      <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#systolicBP" }
Number:Pressure Withings_Diastolic    "Diastolic BP [%d mmHg]"     <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#diastolicBP" }
Number        Withings_SpO2           "SpO2 [%.1f %%]"             <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#spo2" }
Number        Withings_PWV            "Pulse Wave Velocity [%.1f m/s]" <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#pulseWaveVelocity" }
Number        Withings_VO2Max         "VO2 Max [%.1f ml/min/kg]"   <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#vo2Max" }
Number        Withings_VascularAge    "Vascular Age [%d yr]"       <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#vascularAge" }
Number:Temperature Withings_BodyTemp  "Body Temp [%.1f °C]"        <temperature> (gWithings) ["Measurement"] { channel="withings:person:home:john:cardiovascular#temperature" }

// Activity
Number        Withings_Steps          "Steps [%d]"                 <motion> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#steps" }
Number:Length Withings_Distance       "Distance [%.0f m]"          <motion> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#distance" }
Number:Energy Withings_Calories       "Active Calories [%.0f J]"   <fire>   (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#calories" }
Number:Energy Withings_TotalCal       "Total Calories [%.0f J]"    <fire>   (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#totalCalories" }
Number        Withings_Elevation      "Elevation [%.0f m]"         <motion> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#elevation" }
Number:Time   Withings_SoftActivity   "Light Activity [%d s]"      <motion> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#softActivity" }
Number:Time   Withings_ModActivity    "Moderate Activity [%d s]"   <motion> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#moderateActivity" }
Number:Time   Withings_IntActivity    "Intense Activity [%d s]"    <motion> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#intenseActivity" }
Number        Withings_HR_Avg         "HR Average [%d bpm]"        <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#hrAverage" }
Number        Withings_HR_Min         "HR Min [%d bpm]"            <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#hrMin" }
Number        Withings_HR_Max         "HR Max [%d bpm]"            <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:activity#hrMax" }

// Sleep
Number:Time   Withings_TotalSleep     "Total Sleep [%d s]"         <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#totalSleepTime" }
Number:Time   Withings_DeepSleep      "Deep Sleep [%d s]"          <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#deepSleepDuration" }
Number:Time   Withings_LightSleep     "Light Sleep [%d s]"         <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#lightSleepDuration" }
Number:Time   Withings_REMSleep       "REM Sleep [%d s]"           <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#remSleepDuration" }
Number        Withings_WakeupCount    "Wakeup Count [%d]"          <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#wakeupCount" }
Number:Time   Withings_WakeupDur      "Wakeup Duration [%d s]"     <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#wakeupDuration" }
Number:Time   Withings_TimeToSleep    "Time to Sleep [%d s]"       <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#timeToSleep" }
Number:Time   Withings_TimeToWakeup   "Time to Wakeup [%d s]"      <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#timeToWakeup" }
Number        Withings_SleepScore     "Sleep Score [%d]"           <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepScore" }
Number:Time   Withings_Snoring        "Snoring [%d s]"             <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#snoring" }
Number        Withings_SleepHR_Avg    "Sleep HR Avg [%d bpm]"      <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepHrAverage" }
Number        Withings_SleepHR_Min    "Sleep HR Min [%d bpm]"      <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepHrMin" }
Number        Withings_SleepHR_Max    "Sleep HR Max [%d bpm]"      <heart> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepHrMax" }
Number        Withings_SleepRR_Avg    "Sleep RR Avg [%d brpm]"     <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepRrAverage" }
Number        Withings_SleepRR_Min    "Sleep RR Min [%d brpm]"     <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepRrMin" }
Number        Withings_SleepRR_Max    "Sleep RR Max [%d brpm]"     <moon> (gWithings) ["Measurement"] { channel="withings:person:home:john:sleep#sleepRrMax" }
```

---

## Sitemap Example

```perl
sitemap withings label="Withings Health" {
    Frame label="Body Composition" {
        Text item=Withings_Weight
        Text item=Withings_Fat_Ratio
        Text item=Withings_Fat_Mass
        Text item=Withings_Fat_Free_Mass
        Text item=Withings_Muscle_Mass
        Text item=Withings_Bone_Mass
        Text item=Withings_Hydration
    }
    Frame label="Cardiovascular" {
        Text item=Withings_Heart_Pulse
        Text item=Withings_Systolic
        Text item=Withings_Diastolic
        Text item=Withings_SpO2
        Text item=Withings_PWV
        Text item=Withings_VO2Max
        Text item=Withings_VascularAge
        Text item=Withings_BodyTemp
    }
    Frame label="Activity" {
        Text item=Withings_Steps
        Text item=Withings_Distance
        Text item=Withings_Calories
        Text item=Withings_Elevation
    }
    Frame label="Sleep" {
        Text item=Withings_TotalSleep
        Text item=Withings_SleepScore
        Text item=Withings_DeepSleep
        Text item=Withings_LightSleep
        Text item=Withings_REMSleep
        Text item=Withings_WakeupCount
    }
    Frame label="Status" {
        Text item=Withings_Last
    }
}
```

---

## Rules Examples

### Weight Change Alert

```javascript
// automation/js/withings-weight-alert.js
rules.when()
    .item("Withings_Weight").changed()
    .then(event => {
        const weight = items.getItem("Withings_Weight").numericState;
        const previous = event.oldState;
        if (previous !== null) {
            const diff = weight - parseFloat(previous);
            if (Math.abs(diff) > 1.0) {
                actions.NotificationAction.sendBroadcastNotification(
                    `Weight change: ${diff > 0 ? '+' : ''}${diff.toFixed(1)} kg (now ${weight.toFixed(1)} kg)`
                );
            }
        }
    })
    .build("Withings Weight Change Alert");
```

### Daily Weight Log to InfluxDB

```yaml
# persistence/influxdb.persist
Withings_Weight      : strategy = everyChange, restoreOnStartup
Withings_Fat_Ratio   : strategy = everyChange, restoreOnStartup
Withings_Muscle_Mass : strategy = everyChange, restoreOnStartup
```

### Sleep Quality Notification

```javascript
// automation/js/withings-sleep-notify.js
rules.when()
    .item("Withings_SleepScore").changed()
    .then(event => {
        const score = items.getItem("Withings_SleepScore").numericState;
        let quality = "good";
        if (score < 50) quality = "poor";
        else if (score < 70) quality = "fair";
        console.log(`Sleep score: ${score} (${quality})`);
    })
    .build("Withings Sleep Score");
```

---

## Persistence

Recommended persistence configuration for trending and charts:

```yaml
// Body measurements change infrequently — use everyChange
Withings_Weight          : strategy = everyChange, restoreOnStartup
Withings_Fat_Ratio       : strategy = everyChange, restoreOnStartup
Withings_Fat_Mass        : strategy = everyChange, restoreOnStartup
Withings_Fat_Free_Mass   : strategy = everyChange, restoreOnStartup
Withings_Muscle_Mass     : strategy = everyChange, restoreOnStartup
Withings_Bone_Mass       : strategy = everyChange, restoreOnStartup
Withings_Hydration       : strategy = everyChange, restoreOnStartup
Withings_Last            : strategy = everyChange, restoreOnStartup
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
mvn spotless:apply
mvn clean package -DskipTests -DskipChecks

# Deploy the JAR
cp target/org.openhab.binding.withings-*.jar /usr/share/openhab/addons/
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
    │   │   ├── WithingsPersonConfiguration.java   # Thing config POJO
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
- **Three polling jobs** — Body measurements, activity, and sleep are polled on separate schedules since they update at different frequencies.
- **Latest-value-only updates** — When fetching measurements, only the most recent value per measure type is used to avoid "scrolling" through historical data on each poll.
- **Percentage normalization** — Withings returns fat ratio and SpO2 as fractions (0–1). The binding converts to percentages (0–100) for display.

---

## Troubleshooting

### Bridge goes OFFLINE / CONFIGURATION_PENDING

This is normal when tokens are not configured and no persisted tokens exist in storage. Open `http://your-openhab:8080/withings` and authorize. After first authorization, tokens are stored persistently and will survive reboots.

### Bridge goes OFFLINE after reboot

If this happens, check that `/var/lib/openhab/jsondb/` contains a `withings.tokens.*.json` file. If missing, re-authorize via the `/withings` page. The binding version must include the `StorageService` persistence fix (April 2026+).

### Token refresh fails (status 401)

Withings refresh tokens can expire after extended inactivity. Re-authorize via the `/withings` page.

### Activity and sleep channels show NULL

These channels require a Withings device that tracks activity/sleep (ScanWatch, Steel HR, Sleep Analyzer). A smart scale alone will only populate body and cardiovascular channels.

### Measurements from other users appear

Configure the `userId` parameter on the person thing. The binding filters measurements by user ID. You can find your user ID on the success page after OAuth2 authorization.

### Log too verbose

The binding logs routine polling at DEBUG level. To see polling details:

```
log:set DEBUG org.openhab.binding.withings
```

To return to normal:

```
log:set INFO org.openhab.binding.withings
```

### Withings API rate limits

The Withings API has rate limits. Avoid setting polling intervals below 5 minutes. The defaults (15/30/60 min) are well within limits.

---

## Credits

**Author:** Nanna Agesen ([@prinsessen](https://github.com/prinsessen))
**Email:** Nanna@agesen.dk

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
