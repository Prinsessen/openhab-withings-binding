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
package org.openhab.binding.withings;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link WithingsBindingConstants} class defines common constants used across the binding.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsBindingConstants {

    public static final String BINDING_ID = "withings";

    // Bridge Type
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");

    // Thing Types
    public static final ThingTypeUID THING_TYPE_PERSON = new ThingTypeUID(BINDING_ID, "person");
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_PERSON,
            THING_TYPE_DEVICE);

    // Thing Properties
    public static final String PROPERTY_DEVICE_ID = "deviceId";
    public static final String PROPERTY_DEVICE_MODEL_NAME = "deviceModelName";

    // Channel Group IDs
    public static final String CHANNEL_GROUP_BODY = "body";
    public static final String CHANNEL_GROUP_CARDIOVASCULAR = "cardiovascular";
    public static final String CHANNEL_GROUP_ACTIVITY = "activity";
    public static final String CHANNEL_GROUP_SLEEP = "sleep";
    public static final String CHANNEL_GROUP_DEVICE = "device";

    // Body Channels
    public static final String CHANNEL_WEIGHT = "weight";
    public static final String CHANNEL_FAT_RATIO = "fatRatio";
    public static final String CHANNEL_FAT_MASS = "fatMass";
    public static final String CHANNEL_FAT_FREE_MASS = "fatFreeMass";
    public static final String CHANNEL_MUSCLE_MASS = "muscleMass";
    public static final String CHANNEL_HYDRATION = "hydration";
    public static final String CHANNEL_BONE_MASS = "boneMass";
    public static final String CHANNEL_LAST_MEASUREMENT = "lastMeasurement";

    // Cardiovascular Channels
    public static final String CHANNEL_HEART_PULSE = "heartPulse";
    public static final String CHANNEL_SYSTOLIC_BP = "systolicBP";
    public static final String CHANNEL_DIASTOLIC_BP = "diastolicBP";
    public static final String CHANNEL_PULSE_WAVE_VELOCITY = "pulseWaveVelocity";
    public static final String CHANNEL_VO2_MAX = "vo2Max";
    public static final String CHANNEL_VASCULAR_AGE = "vascularAge";
    public static final String CHANNEL_SPO2 = "spo2";
    public static final String CHANNEL_BODY_TEMPERATURE = "temperature";

    // Device Channels
    public static final String CHANNEL_DEVICE_BATTERY = "battery";
    public static final String CHANNEL_DEVICE_LAST_SESSION = "lastSession";
    public static final String CHANNEL_DEVICE_MODEL = "model";

    // Activity Channels
    public static final String CHANNEL_STEPS = "steps";
    public static final String CHANNEL_DISTANCE = "distance";
    public static final String CHANNEL_CALORIES = "calories";
    public static final String CHANNEL_TOTAL_CALORIES = "totalCalories";
    public static final String CHANNEL_ELEVATION = "elevation";
    public static final String CHANNEL_SOFT_ACTIVITY = "softActivity";
    public static final String CHANNEL_MODERATE_ACTIVITY = "moderateActivity";
    public static final String CHANNEL_INTENSE_ACTIVITY = "intenseActivity";
    public static final String CHANNEL_HR_AVERAGE = "hrAverage";
    public static final String CHANNEL_HR_MIN = "hrMin";
    public static final String CHANNEL_HR_MAX = "hrMax";
    public static final String CHANNEL_HR_ZONE_0 = "hrZone0";
    public static final String CHANNEL_HR_ZONE_1 = "hrZone1";
    public static final String CHANNEL_HR_ZONE_2 = "hrZone2";
    public static final String CHANNEL_HR_ZONE_3 = "hrZone3";
    public static final String CHANNEL_ACTIVE_DURATION = "activeDuration";

    // Sleep Channels
    public static final String CHANNEL_TOTAL_SLEEP_TIME = "totalSleepTime";
    public static final String CHANNEL_DEEP_SLEEP = "deepSleepDuration";
    public static final String CHANNEL_LIGHT_SLEEP = "lightSleepDuration";
    public static final String CHANNEL_REM_SLEEP = "remSleepDuration";
    public static final String CHANNEL_WAKEUP_COUNT = "wakeupCount";
    public static final String CHANNEL_WAKEUP_DURATION = "wakeupDuration";
    public static final String CHANNEL_TIME_TO_SLEEP = "timeToSleep";
    public static final String CHANNEL_TIME_TO_WAKEUP = "timeToWakeup";
    public static final String CHANNEL_SLEEP_SCORE = "sleepScore";
    public static final String CHANNEL_SNORING = "snoring";
    public static final String CHANNEL_SLEEP_HR_AVG = "sleepHrAverage";
    public static final String CHANNEL_SLEEP_HR_MIN = "sleepHrMin";
    public static final String CHANNEL_SLEEP_HR_MAX = "sleepHrMax";
    public static final String CHANNEL_SLEEP_RR_AVG = "sleepRrAverage";
    public static final String CHANNEL_SLEEP_RR_MIN = "sleepRrMin";
    public static final String CHANNEL_SLEEP_RR_MAX = "sleepRrMax";
    public static final String CHANNEL_SLEEP_EFFICIENCY = "sleepEfficiency";
    public static final String CHANNEL_SNORING_EPISODES = "snoringEpisodes";
    public static final String CHANNEL_TOTAL_TIME_IN_BED = "totalTimeInBed";
    public static final String CHANNEL_NB_REM_EPISODES = "nbRemEpisodes";
    public static final String CHANNEL_NIGHT_EVENTS = "nightEvents";
    public static final String CHANNEL_OUT_OF_BED_COUNT = "outOfBedCount";
    public static final String CHANNEL_BREATHING_DISTURBANCES = "breathingDisturbances";
    public static final String CHANNEL_WAKEUP_LATENCY = "wakeupLatency";
    public static final String CHANNEL_SLEEP_LATENCY = "sleepLatency";

    // OAuth2 Scope
    public static final String OAUTH_SCOPE = "user.metrics,user.activity,user.sleepevents,user.info";

    // Withings API URLs
    public static final String API_AUTH_URL = "https://account.withings.com/oauth2_user/authorize2";
    public static final String API_TOKEN_URL = "https://wbsapi.withings.net/v2/oauth2";
    public static final String API_MEASURE_URL = "https://wbsapi.withings.net/measure";
    public static final String API_MEASURE_V2_URL = "https://wbsapi.withings.net/v2/measure";
    public static final String API_SLEEP_V2_URL = "https://wbsapi.withings.net/v2/sleep";
    public static final String API_HEART_V2_URL = "https://wbsapi.withings.net/v2/heart";
    public static final String API_USER_V2_URL = "https://wbsapi.withings.net/v2/user";

    // Withings Measure Types (from API documentation)
    public static final int MEASURE_TYPE_WEIGHT = 1;
    public static final int MEASURE_TYPE_HEIGHT = 4;
    public static final int MEASURE_TYPE_FAT_FREE_MASS = 5;
    public static final int MEASURE_TYPE_FAT_RATIO = 6;
    public static final int MEASURE_TYPE_FAT_MASS = 8;
    public static final int MEASURE_TYPE_DIASTOLIC_BP = 9;
    public static final int MEASURE_TYPE_SYSTOLIC_BP = 10;
    public static final int MEASURE_TYPE_HEART_PULSE = 11;
    public static final int MEASURE_TYPE_TEMPERATURE = 12;
    public static final int MEASURE_TYPE_SPO2 = 54;
    public static final int MEASURE_TYPE_BODY_TEMPERATURE = 71;
    public static final int MEASURE_TYPE_SKIN_TEMPERATURE = 73;
    public static final int MEASURE_TYPE_MUSCLE_MASS = 76;
    public static final int MEASURE_TYPE_HYDRATION = 77;
    public static final int MEASURE_TYPE_BONE_MASS = 88;
    public static final int MEASURE_TYPE_PULSE_WAVE_VELOCITY = 91;
    public static final int MEASURE_TYPE_VO2_MAX = 123;
    public static final int MEASURE_TYPE_VASCULAR_AGE = 155;

    // All measure types we want to fetch
    public static final String ALL_MEASURE_TYPES = "1,5,6,8,9,10,11,12,54,71,73,76,77,88,91,123,155";
}
