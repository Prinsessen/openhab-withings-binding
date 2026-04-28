/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.withings.handler;

import static org.openhab.binding.withings.WithingsBindingConstants.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.quantity.Mass;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Time;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.withings.dto.WithingsApiResponse;
import org.openhab.binding.withings.dto.WithingsApiResponse.Activity;
import org.openhab.binding.withings.dto.WithingsApiResponse.Device;
import org.openhab.binding.withings.dto.WithingsApiResponse.Measure;
import org.openhab.binding.withings.dto.WithingsApiResponse.MeasureGroup;
import org.openhab.binding.withings.dto.WithingsApiResponse.SleepData;
import org.openhab.binding.withings.dto.WithingsApiResponse.SleepSummary;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WithingsPersonHandler} handles data polling and channel updates for a Withings person.
 * Supports body measurements (scales), cardiovascular data (BP monitors, watches),
 * activity tracking (watches, trackers), and sleep monitoring (sleep mat, watches).
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsPersonHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(WithingsPersonHandler.class);

    private @Nullable ScheduledFuture<?> bodyPollingJob;
    private @Nullable ScheduledFuture<?> activityPollingJob;
    private @Nullable ScheduledFuture<?> sleepPollingJob;
    private @Nullable ScheduledFuture<?> devicePollingJob;

    // Initialize to 7 days ago to avoid loading entire measurement history on startup
    private long lastBodyUpdate = Instant.now().minusSeconds(7 * 24 * 3600).getEpochSecond();
    private int userId = 0;

    /** Optional device model filter — empty means "most recently synced" */
    private String deviceModel = "";

    public WithingsPersonHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(WithingsDiscoveryService.class);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Withings Person handler");

        WithingsPersonConfiguration config = getConfigAs(WithingsPersonConfiguration.class);

        if (config.userId <= 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "User ID must be configured");
            return;
        }

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "No bridge (Withings Account) configured");
            return;
        }

        if (bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "Bridge (Withings Account) is not online");
            return;
        }

        this.userId = config.userId;
        this.deviceModel = config.deviceModel != null ? config.deviceModel.trim() : "";
        if (!deviceModel.isEmpty()) {
            logger.debug("Withings device filter: will use device matching '{}'", deviceModel);
        }

        updateStatus(ThingStatus.ONLINE);

        // Start polling jobs
        startPolling(config);
    }

    @Override
    public void dispose() {
        stopPolling();
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            // Trigger immediate refresh based on channel group
            String groupId = channelUID.getGroupId();
            if (groupId != null) {
                switch (groupId) {
                    case CHANNEL_GROUP_BODY:
                    case CHANNEL_GROUP_CARDIOVASCULAR:
                        scheduler.execute(this::pollBodyMeasurements);
                        break;
                    case CHANNEL_GROUP_ACTIVITY:
                        scheduler.execute(this::pollActivity);
                        break;
                    case CHANNEL_GROUP_SLEEP:
                        scheduler.execute(this::pollSleep);
                        break;
                    case CHANNEL_GROUP_DEVICE:
                        scheduler.execute(this::pollDevices);
                        break;
                }
            }
        }
    }

    private void startPolling(WithingsPersonConfiguration config) {
        stopPolling();

        logger.info("Starting Withings polling: body={}min, activity={}min, sleep={}min, device={}min",
                config.pollingIntervalBody, config.pollingIntervalActivity, config.pollingIntervalSleep,
                config.pollingIntervalDevice);

        // Body measurements (includes cardiovascular from scales/BP monitors)
        bodyPollingJob = scheduler.scheduleWithFixedDelay(this::pollBodyMeasurements, 5,
                config.pollingIntervalBody * 60L, TimeUnit.SECONDS);

        // Activity data
        activityPollingJob = scheduler.scheduleWithFixedDelay(this::pollActivity, 15,
                config.pollingIntervalActivity * 60L, TimeUnit.SECONDS);

        // Sleep data
        sleepPollingJob = scheduler.scheduleWithFixedDelay(this::pollSleep, 25, config.pollingIntervalSleep * 60L,
                TimeUnit.SECONDS);

        // Device info
        devicePollingJob = scheduler.scheduleWithFixedDelay(this::pollDevices, 35, config.pollingIntervalDevice * 60L,
                TimeUnit.SECONDS);
    }

    private void stopPolling() {
        ScheduledFuture<?> job = bodyPollingJob;
        if (job != null) {
            job.cancel(true);
            bodyPollingJob = null;
        }
        job = activityPollingJob;
        if (job != null) {
            job.cancel(true);
            activityPollingJob = null;
        }
        job = sleepPollingJob;
        if (job != null) {
            job.cancel(true);
            sleepPollingJob = null;
        }
        job = devicePollingJob;
        if (job != null) {
            job.cancel(true);
            devicePollingJob = null;
        }
    }

    public @Nullable WithingsApiClient getApiClient() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof WithingsAccountHandler accountHandler) {
            return accountHandler.getApiClient();
        }
        return null;
    }

    // ==================== Body Measurements ====================

    private void pollBodyMeasurements() {
        WithingsApiClient client = getApiClient();
        if (client == null) {
            logger.debug("Cannot poll body measurements - no API client");
            return;
        }

        try {
            WithingsApiResponse response = client.getMeasurements(lastBodyUpdate, userId);
            if (response == null || response.body == null) {
                return;
            }

            List<MeasureGroup> groups = response.body.measuregrps;
            if (groups == null || groups.isEmpty()) {
                logger.debug("No new body measurements");
                return;
            }

            logger.debug("Processing {} measurement groups", groups.size());

            // Collect ONLY the latest value per measure type (avoid "scrolling" through history)
            Map<Integer, BigDecimal> latestValues = new HashMap<>();
            Map<Integer, Long> latestTimestamps = new HashMap<>();
            long overallLatestDate = 0;
            int acceptedGroups = 0;
            int skippedGroups = 0;

            for (MeasureGroup group : groups) {
                if (group.category != 1) {
                    continue; // Skip non-real measures
                }

                // Filter by userId if configured (skip other people's measurements)
                if (userId > 0 && group.userid > 0 && group.userid != userId) {
                    skippedGroups++;
                    continue;
                }

                acceptedGroups++;
                List<Measure> measures = group.measures;
                if (measures == null) {
                    continue;
                }

                for (Measure measure : measures) {
                    Long prev = latestTimestamps.get(measure.type);
                    if (prev == null || group.date > prev) {
                        BigDecimal realValue = BigDecimal.valueOf(measure.value).scaleByPowerOfTen(measure.unit);
                        latestValues.put(measure.type, realValue);
                        latestTimestamps.put(measure.type, group.date);
                    }
                }

                if (group.date > overallLatestDate) {
                    overallLatestDate = group.date;
                }
            }

            // Now update channels once with only the newest values
            for (Map.Entry<Integer, BigDecimal> entry : latestValues.entrySet()) {
                updateMeasureChannel(entry.getKey(), entry.getValue());
            }

            // Update last measurement timestamp
            if (overallLatestDate > 0) {
                ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(overallLatestDate),
                        ZoneId.systemDefault());
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_LAST_MEASUREMENT, new DateTimeType(dateTime));
                lastBodyUpdate = overallLatestDate + 1; // Next time, only fetch newer data
            }

            logger.debug("Withings body updated: {} measure types from {} groups (accepted={}, skipped={})",
                    latestValues.size(), groups.size(), acceptedGroups, skippedGroups);
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            logger.error("Error polling body measurements: {}", e.getMessage(), e);
        }
    }

    private void updateMeasureChannel(int measureType, BigDecimal value) {
        switch (measureType) {
            case MEASURE_TYPE_WEIGHT:
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_WEIGHT, new QuantityType<Mass>(value, SIUnits.KILOGRAM));
                break;
            case MEASURE_TYPE_FAT_RATIO:
                // Withings returns fat ratio as fraction (0-1), convert to percentage (0-100)
                BigDecimal pctValue = value.compareTo(BigDecimal.ONE) <= 0
                        ? value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                        : value.setScale(2, RoundingMode.HALF_UP);
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_FAT_RATIO, new DecimalType(pctValue));
                break;
            case MEASURE_TYPE_FAT_MASS:
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_FAT_MASS,
                        new QuantityType<Mass>(value, SIUnits.KILOGRAM));
                break;
            case MEASURE_TYPE_FAT_FREE_MASS:
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_FAT_FREE_MASS,
                        new QuantityType<Mass>(value, SIUnits.KILOGRAM));
                break;
            case MEASURE_TYPE_MUSCLE_MASS:
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_MUSCLE_MASS,
                        new QuantityType<Mass>(value, SIUnits.KILOGRAM));
                break;
            case MEASURE_TYPE_HYDRATION:
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_HYDRATION,
                        new QuantityType<Mass>(value, SIUnits.KILOGRAM));
                break;
            case MEASURE_TYPE_BONE_MASS:
                updateState(CHANNEL_GROUP_BODY + "#" + CHANNEL_BONE_MASS,
                        new QuantityType<Mass>(value, SIUnits.KILOGRAM));
                break;
            case MEASURE_TYPE_HEART_PULSE:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_HEART_PULSE, new DecimalType(value));
                break;
            case MEASURE_TYPE_SYSTOLIC_BP:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_SYSTOLIC_BP,
                        new QuantityType<>(value, Units.MILLIMETRE_OF_MERCURY));
                break;
            case MEASURE_TYPE_DIASTOLIC_BP:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_DIASTOLIC_BP,
                        new QuantityType<>(value, Units.MILLIMETRE_OF_MERCURY));
                break;
            case MEASURE_TYPE_PULSE_WAVE_VELOCITY:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_PULSE_WAVE_VELOCITY, new DecimalType(value));
                break;
            case MEASURE_TYPE_VO2_MAX:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_VO2_MAX, new DecimalType(value));
                break;
            case MEASURE_TYPE_VASCULAR_AGE:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_VASCULAR_AGE, new DecimalType(value));
                break;
            case MEASURE_TYPE_SPO2:
                // Withings returns SpO2 as fraction (0-1), convert to percentage (0-100)
                BigDecimal spo2Pct = value.compareTo(BigDecimal.ONE) <= 0
                        ? value.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                        : value.setScale(1, RoundingMode.HALF_UP);
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_SPO2, new DecimalType(spo2Pct));
                break;
            case MEASURE_TYPE_BODY_TEMPERATURE:
            case MEASURE_TYPE_TEMPERATURE:
                updateState(CHANNEL_GROUP_CARDIOVASCULAR + "#" + CHANNEL_BODY_TEMPERATURE,
                        new QuantityType<Temperature>(value, SIUnits.CELSIUS));
                break;
            default:
                logger.trace("Unhandled Withings measure type: {} value: {}", measureType, value);
                break;
        }
    }

    // ==================== Activity Data ====================

    private void pollActivity() {
        WithingsApiClient client = getApiClient();
        if (client == null) {
            logger.debug("Cannot poll activity - no API client");
            return;
        }

        try {
            WithingsApiResponse response = client.getActivity(null); // Today
            if (response == null || response.body == null) {
                return;
            }

            List<Activity> activities = response.body.activities;
            if (activities == null || activities.isEmpty()) {
                logger.debug("Withings activity: no data for today (API returned empty list)");
                return;
            }

            // Use the most recent activity entry
            Activity activity = activities.get(activities.size() - 1);

            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_STEPS, new DecimalType(activity.steps));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_DISTANCE,
                    new QuantityType<Length>(activity.distance, SIUnits.METRE));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_CALORIES,
                    new QuantityType<Energy>(activity.calories * 1000, Units.JOULE)); // kcal to cal approx
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_TOTAL_CALORIES,
                    new QuantityType<Energy>(activity.totalcalories * 1000, Units.JOULE));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_ELEVATION, new DecimalType(activity.elevation));

            // Duration channels in seconds
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_SOFT_ACTIVITY,
                    new QuantityType<Time>(activity.soft, Units.SECOND));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_MODERATE_ACTIVITY,
                    new QuantityType<Time>(activity.moderate, Units.SECOND));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_INTENSE_ACTIVITY,
                    new QuantityType<Time>(activity.intense, Units.SECOND));

            // Heart rate
            if (activity.hr_average > 0) {
                updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_AVERAGE, new DecimalType(activity.hr_average));
            }
            if (activity.hr_min > 0) {
                updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_MIN, new DecimalType(activity.hr_min));
            }
            if (activity.hr_max > 0) {
                updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_MAX, new DecimalType(activity.hr_max));
            }

            // Heart rate zones (seconds per zone)
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_ZONE_0,
                    new QuantityType<Time>(activity.hr_zone_0, Units.SECOND));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_ZONE_1,
                    new QuantityType<Time>(activity.hr_zone_1, Units.SECOND));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_ZONE_2,
                    new QuantityType<Time>(activity.hr_zone_2, Units.SECOND));
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_HR_ZONE_3,
                    new QuantityType<Time>(activity.hr_zone_3, Units.SECOND));

            // Active duration
            updateState(CHANNEL_GROUP_ACTIVITY + "#" + CHANNEL_ACTIVE_DURATION,
                    new QuantityType<Time>(activity.active, Units.SECOND));

            logger.debug("Withings activity updated: {} steps, {}m distance, {} cal", activity.steps, activity.distance,
                    activity.calories);

        } catch (Exception e) {
            logger.warn("Error polling activity: {}", e.getMessage());
        }
    }

    // ==================== Sleep Data ====================

    private void pollSleep() {
        WithingsApiClient client = getApiClient();
        if (client == null) {
            logger.debug("Cannot poll sleep - no API client");
            return;
        }

        try {
            WithingsApiResponse response = client.getSleepSummary(null); // Today
            if (response == null || response.body == null) {
                return;
            }

            List<SleepSummary> series = response.body.series;
            if (series == null || series.isEmpty()) {
                logger.debug("Withings sleep: no data for today (API returned empty list)");
                return;
            }

            // Use the most recent sleep summary
            SleepSummary summary = series.get(series.size() - 1);
            SleepData data = summary.data;
            if (data == null) {
                return;
            }

            // Sleep durations (API returns seconds)
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_TOTAL_SLEEP_TIME,
                    new QuantityType<Time>(data.total_sleep_time, Units.SECOND));
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_DEEP_SLEEP,
                    new QuantityType<Time>(data.deepsleepduration, Units.SECOND));
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_LIGHT_SLEEP,
                    new QuantityType<Time>(data.lightsleepduration, Units.SECOND));
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_REM_SLEEP,
                    new QuantityType<Time>(data.remsleepduration, Units.SECOND));

            // Wakeup data
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_WAKEUP_COUNT, new DecimalType(data.wakeupcount));
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_WAKEUP_DURATION,
                    new QuantityType<Time>(data.wakeupduration, Units.SECOND));
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_TIME_TO_SLEEP,
                    new QuantityType<Time>(data.durationtosleep, Units.SECOND));
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_TIME_TO_WAKEUP,
                    new QuantityType<Time>(data.durationtowakeup, Units.SECOND));

            // Sleep quality
            if (data.sleep_score > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_SCORE, new DecimalType(data.sleep_score));
            }
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SNORING,
                    new QuantityType<Time>(data.snoring, Units.SECOND));

            // Heart rate during sleep
            if (data.hr_average > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_HR_AVG, new DecimalType(data.hr_average));
            }
            if (data.hr_min > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_HR_MIN, new DecimalType(data.hr_min));
            }
            if (data.hr_max > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_HR_MAX, new DecimalType(data.hr_max));
            }

            // Respiration rate during sleep
            if (data.rr_average > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_RR_AVG, new DecimalType(data.rr_average));
            }
            if (data.rr_min > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_RR_MIN, new DecimalType(data.rr_min));
            }
            if (data.rr_max > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_RR_MAX, new DecimalType(data.rr_max));
            }

            // Additional sleep metrics
            if (data.sleep_efficiency > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_EFFICIENCY,
                        new DecimalType(data.sleep_efficiency));
            }
            if (data.snoringepisodecount > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SNORING_EPISODES,
                        new DecimalType(data.snoringepisodecount));
            }
            updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_TOTAL_TIME_IN_BED,
                    new QuantityType<Time>(data.total_timeinbed, Units.SECOND));
            if (data.nb_rem_episodes > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_NB_REM_EPISODES, new DecimalType(data.nb_rem_episodes));
            }
            if (data.night_events > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_NIGHT_EVENTS, new DecimalType(data.night_events));
            }
            if (data.out_of_bed_count > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_OUT_OF_BED_COUNT,
                        new DecimalType(data.out_of_bed_count));
            }
            if (data.breathing_disturbances_intensity > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_BREATHING_DISTURBANCES,
                        new DecimalType(data.breathing_disturbances_intensity));
            }
            if (data.wakeup_latency > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_WAKEUP_LATENCY,
                        new QuantityType<Time>(data.wakeup_latency, Units.SECOND));
            }
            if (data.sleep_latency > 0) {
                updateState(CHANNEL_GROUP_SLEEP + "#" + CHANNEL_SLEEP_LATENCY,
                        new QuantityType<Time>(data.sleep_latency, Units.SECOND));
            }

            logger.debug("Withings sleep updated: {}s total, score={}, {} wakeups", data.total_sleep_time,
                    data.sleep_score, data.wakeupcount);

        } catch (Exception e) {
            logger.warn("Error polling sleep: {}", e.getMessage());
        }
    }

    // ==================== Device Data ====================

    private void pollDevices() {
        WithingsApiClient client = getApiClient();
        if (client == null) {
            logger.debug("Cannot poll devices - no API client");
            return;
        }

        try {
            WithingsApiResponse response = client.getDevices();
            if (response == null || response.body == null) {
                return;
            }

            List<Device> devices = response.body.devices;
            if (devices == null || devices.isEmpty()) {
                logger.debug("Withings devices: no devices returned");
                return;
            }

            // Select device: match deviceModel filter if configured, else use most recently synced
            Device device = mostRecentDevice(devices);
            if (!deviceModel.isEmpty()) {
                String filterLower = deviceModel.toLowerCase();
                Device matched = null;
                for (Device d : devices) {
                    if (d.model != null && d.model.toLowerCase().contains(filterLower)) {
                        matched = d;
                        break;
                    }
                }
                if (matched != null) {
                    device = matched;
                } else {
                    logger.warn("Withings: no device matching '{}' found, falling back to most recently synced",
                            deviceModel);
                }
            }
            logger.debug("Withings device selected: model={} (from {} available)", device.model, devices.size());

            String battery = device.battery;
            if (battery != null && !battery.isEmpty()) {
                updateState(CHANNEL_GROUP_DEVICE + "#" + CHANNEL_DEVICE_BATTERY, new StringType(battery));
            }

            if (device.last_session_date > 0) {
                ZonedDateTime lastSession = ZonedDateTime.ofInstant(Instant.ofEpochSecond(device.last_session_date),
                        ZoneId.systemDefault());
                updateState(CHANNEL_GROUP_DEVICE + "#" + CHANNEL_DEVICE_LAST_SESSION, new DateTimeType(lastSession));
            }

            String model = device.model;
            if (model != null && !model.isEmpty()) {
                updateState(CHANNEL_GROUP_DEVICE + "#" + CHANNEL_DEVICE_MODEL, new StringType(model));
            }

            logger.debug("Withings device updated: model={}, battery={}", device.model, device.battery);

        } catch (Exception e) {
            logger.warn("Error polling devices: {}", e.getMessage());
        }
    }

    /** Returns the device with the highest last_session_date (most recently synced). */
    private Device mostRecentDevice(List<Device> devices) {
        Device best = devices.get(0);
        for (Device d : devices) {
            if (d.last_session_date > best.last_session_date) {
                best = d;
            }
        }
        return best;
    }
}
