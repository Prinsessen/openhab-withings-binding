/**
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
package org.openhab.binding.withings.dto;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * DTO for Withings API responses. Withings wraps all responses in a standard envelope
 * with status code and body.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsApiResponse {

    public int status;
    public @Nullable Body body;

    public static class Body {
        public @Nullable String updatetime;
        public @Nullable String timezone;
        public @Nullable List<MeasureGroup> measuregrps;
        public int offset;

        // OAuth2 token response fields
        public @Nullable String access_token;
        public @Nullable String refresh_token;
        public int expires_in;
        public @Nullable String scope;
        public int userid;

        // Activity response fields
        public @Nullable List<Activity> activities;

        // Sleep response fields
        public @Nullable List<SleepSummary> series;

        // Device response fields
        public @Nullable List<Device> devices;
    }

    public static class MeasureGroup {
        public long grpid;
        public int attrib;
        public long date;
        public long created;
        public int category;
        public int userid;
        public @Nullable String deviceid;
        public @Nullable String hash_deviceid;
        public @Nullable List<Measure> measures;
        public @Nullable String comment;
    }

    public static class Measure {
        public int value;
        public int type;
        public int unit;
    }

    public static class Activity {
        public @Nullable String date;
        public @Nullable String timezone;
        public @Nullable String deviceid;
        public @Nullable String hash_deviceid;
        public @Nullable String brand;
        public @Nullable String is_tracker;
        public int steps;
        public double distance;
        public double elevation;
        public int soft;
        public int moderate;
        public int intense;
        public int active;
        public double calories;
        public double totalcalories;
        public int hr_average;
        public int hr_min;
        public int hr_max;
        public int hr_zone_0;
        public int hr_zone_1;
        public int hr_zone_2;
        public int hr_zone_3;
    }

    public static class SleepSummary {
        public long id;
        public @Nullable String timezone;
        public @Nullable String model;
        public long startdate;
        public long enddate;
        public @Nullable String date;
        public long modified;
        public @Nullable SleepData data;
    }

    public static class SleepData {
        public int breathing_disturbances_intensity;
        public int deepsleepduration;
        public int durationtosleep;
        public int durationtowakeup;
        public int hr_average;
        public int hr_max;
        public int hr_min;
        public int lightsleepduration;
        public int nb_rem_episodes;
        public int night_events;
        public int out_of_bed_count;
        public int remsleepduration;
        public int rr_average;
        public int rr_max;
        public int rr_min;
        public int sleep_efficiency;
        public int sleep_latency;
        public int sleep_score;
        public int snoring;
        public int snoringepisodecount;
        public int total_sleep_time;
        public int total_timeinbed;
        public int wakeup_latency;
        public int wakeupcount;
        public int wakeupduration;
    }

    public static class Device {
        public @Nullable String type;
        public @Nullable String model;
        public @Nullable String model_id;
        public @Nullable String battery;
        public @Nullable String deviceid;
        public @Nullable String hash_deviceid;
        public long last_session_date;
    }
}
