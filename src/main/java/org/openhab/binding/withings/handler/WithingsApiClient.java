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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.withings.dto.WithingsApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link WithingsApiClient} handles all communication with the Withings Cloud API.
 * Manages OAuth2 token lifecycle including automatic refresh.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsApiClient {

    private final Logger logger = LoggerFactory.getLogger(WithingsApiClient.class);
    private final Gson gson = new GsonBuilder().create();
    private final HttpClient httpClient;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private final String clientId;
    private final String clientSecret;
    private String accessToken;
    private String refreshToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    // Callback to persist refreshed tokens back to the bridge config
    private @Nullable TokenRefreshCallback tokenRefreshCallback;

    /**
     * Callback interface for notifying the bridge handler when tokens are refreshed.
     */
    public interface TokenRefreshCallback {
        void onTokenRefreshed(String newAccessToken, String newRefreshToken);
    }

    public WithingsApiClient(String clientId, String clientSecret, String accessToken, String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public void setTokenRefreshCallback(@Nullable TokenRefreshCallback callback) {
        this.tokenRefreshCallback = callback;
    }

    /**
     * Ensures we have a valid access token, refreshing if necessary.
     *
     * @return true if token is valid, false if refresh failed
     */
    private boolean ensureValidToken() {
        tokenLock.lock();
        try {
            if (Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
                return true; // Token still valid with 60s buffer
            }
            return refreshAccessToken();
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     *
     * @return true if refresh was successful
     */
    private boolean refreshAccessToken() {
        logger.debug("Refreshing Withings access token");

        Map<String, String> params = Map.of("action", "requesttoken", "grant_type", "refresh_token", "client_id",
                clientId, "client_secret", clientSecret, "refresh_token", refreshToken);

        try {
            String responseBody = postForm(API_TOKEN_URL, params, false);
            WithingsApiResponse response = gson.fromJson(responseBody, WithingsApiResponse.class);

            if (response == null || response.status != 0 || response.body == null) {
                int status = response != null ? response.status : -1;
                logger.error("Token refresh failed with status: {}", status);
                return false;
            }

            WithingsApiResponse.Body body = response.body;
            String newAccessToken = body.access_token;
            String newRefreshToken = body.refresh_token;

            if (newAccessToken == null || newRefreshToken == null) {
                logger.error("Token refresh returned null tokens");
                return false;
            }

            this.accessToken = newAccessToken;
            this.refreshToken = newRefreshToken;
            this.tokenExpiresAt = Instant.now().plusSeconds(body.expires_in);

            logger.debug("Withings token refreshed successfully, expires in {} seconds", body.expires_in);

            // Notify bridge handler to persist new tokens
            TokenRefreshCallback callback = this.tokenRefreshCallback;
            if (callback != null) {
                callback.onTokenRefreshed(newAccessToken, newRefreshToken);
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to refresh Withings token: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Fetches body measurements (weight, fat, etc.) from Withings API.
     *
     * @param lastUpdate unix timestamp to fetch data since (0 for all recent)
     * @param userId optional user ID to filter measurements for a specific person
     * @return API response or null on error
     */
    public @Nullable WithingsApiResponse getMeasurements(long lastUpdate, int userId) {
        if (!ensureValidToken()) {
            logger.warn("Cannot fetch measurements - no valid token");
            return null;
        }

        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "getmeas");
        params.put("meastypes", ALL_MEASURE_TYPES);
        // Note: Do not filter by category here — some devices (e.g. ScanWatch 2) report
        // temperature measurements under a different category and they would be silently excluded.

        if (userId > 0) {
            params.put("userid", String.valueOf(userId));
        }

        if (lastUpdate > 0) {
            params.put("lastupdate", String.valueOf(lastUpdate));
        }

        try {
            String responseBody = postForm(API_MEASURE_URL, params, true);
            WithingsApiResponse response = gson.fromJson(responseBody, WithingsApiResponse.class);

            if (response == null || response.status != 0) {
                int status = response != null ? response.status : -1;
                logger.warn("getMeasurements failed with status: {}", status);
                return null;
            }
            return response;
        } catch (Exception e) {
            logger.error("Error fetching measurements: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetches daily activity data (steps, distance, calories, etc.).
     *
     * @param date the date to fetch activity for (format: yyyy-MM-dd), or null for today
     * @return API response or null on error
     */
    public @Nullable WithingsApiResponse getActivity(@Nullable String date) {
        if (!ensureValidToken()) {
            logger.warn("Cannot fetch activity - no valid token");
            return null;
        }

        String targetDate = date != null ? date : LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "getactivity");
        params.put("startdateymd", targetDate);
        params.put("enddateymd", targetDate);
        params.put("data_fields", "steps,distance,elevation,soft,moderate,intense,active,calories,totalcalories,"
                + "hr_average,hr_min,hr_max,hr_zone_0,hr_zone_1,hr_zone_2,hr_zone_3");

        try {
            String responseBody = postForm(API_MEASURE_V2_URL, params, true);
            WithingsApiResponse response = gson.fromJson(responseBody, WithingsApiResponse.class);

            if (response == null || response.status != 0) {
                int status = response != null ? response.status : -1;
                logger.debug("getActivity returned status: {} (may require user.activity scope)", status);
                return null;
            }
            return response;
        } catch (Exception e) {
            logger.error("Error fetching activity: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetches sleep summary data for the last 7 days.
     * Uses a rolling 7-day window so nightly metrics (skin_temperature, HRV) are always available.
     *
     * @param date unused - kept for API compatibility; always fetches last 7 days
     * @return API response or null on error
     */
    public @Nullable WithingsApiResponse getSleepSummary(@Nullable String date) {
        if (!ensureValidToken()) {
            logger.warn("Cannot fetch sleep data - no valid token");
            return null;
        }

        // Use a 7-day rolling window so nightly metrics like skin_temperature are captured
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "getsummary");
        params.put("startdateymd", weekAgo.format(DateTimeFormatter.ISO_LOCAL_DATE));
        params.put("enddateymd", today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        params.put("data_fields",
                "total_sleep_time,total_timeinbed,deepsleepduration,lightsleepduration,remsleepduration,"
                        + "wakeupduration,wakeupcount,durationtosleep,durationtowakeup,sleep_score,snoring,"
                        + "snoringepisodecount,nb_rem_episodes,night_events,out_of_bed_count,"
                        + "hr_average,hr_min,hr_max,rr_average,rr_min,rr_max,sleep_efficiency,"
                        + "sleep_latency,wakeup_latency,breathing_disturbances_intensity,"
                        + "skin_temperature,rmssd,sdnn_1,hrv_quality");

        try {
            String responseBody = postForm(API_SLEEP_V2_URL, params, true);
            WithingsApiResponse response = gson.fromJson(responseBody, WithingsApiResponse.class);

            if (response == null || response.status != 0) {
                int status = response != null ? response.status : -1;
                logger.debug("getSleepSummary returned status: {} (may require user.activity scope)", status);
                return null;
            }
            return response;
        } catch (Exception e) {
            logger.error("Error fetching sleep summary: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fetches the most recent Afib classification from the Heart v2 - List API.
     * Returns the afib value from the most recent ECG recording:
     * 0 = sinus rhythm, 1 = afib detected, 2 = inconclusive/poor signal
     *
     * @return afib classification (0/1/2), or -1 if no ECG data available
     */
    public int getLatestAfib() {
        if (!ensureValidToken()) {
            logger.warn("Cannot fetch heart data - no valid token");
            return -1;
        }

        // Query last 30 days — ECG recordings are infrequent
        long endTs = Instant.now().getEpochSecond();
        long startTs = endTs - (30L * 24 * 3600);

        Map<String, String> params = new HashMap<>();
        params.put("action", "list");
        params.put("startdate", String.valueOf(startTs));
        params.put("enddate", String.valueOf(endTs));

        try {
            String responseBody = postForm(API_HEART_V2_URL, params, true);
            logger.trace("Withings heart list response: {}", responseBody);
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (root.get("status").getAsInt() != 0) {
                logger.debug("Heart v2 list returned non-zero status: {}", responseBody);
                return -1;
            }
            JsonElement bodyEl = root.get("body");
            if (bodyEl == null || !bodyEl.isJsonObject()) {
                return -1;
            }
            JsonElement seriesEl = bodyEl.getAsJsonObject().get("series");
            if (seriesEl == null || !seriesEl.isJsonArray()) {
                return -1;
            }
            JsonArray series = seriesEl.getAsJsonArray();
            int latestAfib = -1;
            long latestTs = 0;
            for (JsonElement el : series) {
                JsonObject entry = el.getAsJsonObject();
                long ts = entry.has("timestamp") ? entry.get("timestamp").getAsLong() : 0;
                JsonObject ecg = entry.has("ecg") ? entry.getAsJsonObject("ecg") : null;
                if (ecg != null && ecg.has("afib") && ts > latestTs) {
                    latestTs = ts;
                    latestAfib = ecg.get("afib").getAsInt();
                }
            }
            if (latestTs > 0) {
                logger.debug("Heart v2 latest ECG afib={} at ts={}", latestAfib, latestTs);
            }
            return latestAfib;
        } catch (Exception e) {
            logger.debug("Error fetching heart ECG data: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Fetches user devices.
     *
     * @return API response or null on error
     */
    public @Nullable WithingsApiResponse getDevices() {
        if (!ensureValidToken()) {
            logger.warn("Cannot fetch devices - no valid token");
            return null;
        }

        Map<String, String> params = Map.of("action", "getdevice");

        try {
            String responseBody = postForm(API_USER_V2_URL, params, true);
            WithingsApiResponse response = gson.fromJson(responseBody, WithingsApiResponse.class);

            if (response == null || response.status != 0) {
                int status = response != null ? response.status : -1;
                logger.warn("getDevices failed with status: {}", status);
                return null;
            }
            return response;
        } catch (Exception e) {
            logger.error("Error fetching devices: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Posts a form-encoded request to the Withings API.
     *
     * @param url the API endpoint
     * @param params the form parameters
     * @param withAuth whether to include the Bearer token
     * @return the response body as string
     */
    private String postForm(String url, Map<String, String> params, boolean withAuth)
            throws IOException, InterruptedException {
        String formBody = params.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody));

        if (withAuth) {
            requestBuilder.header("Authorization", "Bearer " + accessToken);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        logger.trace("Withings API {} response ({}): {}", url, response.statusCode(), response.body());

        if (response.statusCode() != 200) {
            throw new IOException("Withings API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Disposes resources.
     */
    public void dispose() {
        // HttpClient doesn't require explicit cleanup in Java 11+
        logger.debug("Withings API client disposed");
    }
}
