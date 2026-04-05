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
package org.openhab.binding.withings.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.withings.servlet.WithingsServlet;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WithingsAccountHandler} is the bridge handler for a Withings API account.
 * It manages the OAuth2 connection and provides the API client to child thing handlers.
 *
 * <p>
 * Supports two initialization modes:
 * <ul>
 * <li><b>Pre-configured tokens</b> — accessToken/refreshToken set in .things file</li>
 * <li><b>Servlet-based OAuth2</b> — user authorizes via /withings web page</li>
 * </ul>
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsAccountHandler extends BaseBridgeHandler implements WithingsApiClient.TokenRefreshCallback {

    private final Logger logger = LoggerFactory.getLogger(WithingsAccountHandler.class);
    private final WithingsServlet servlet;
    private @Nullable WithingsApiClient apiClient;

    public WithingsAccountHandler(Bridge bridge, WithingsServlet servlet) {
        super(bridge);
        this.servlet = servlet;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Withings Account bridge");

        // Register with the servlet for OAuth2 callbacks
        servlet.addBridgeHandler(this);

        WithingsAccountConfiguration config = getConfigAs(WithingsAccountConfiguration.class);

        String clientId = config.clientId;
        String clientSecret = config.clientSecret;
        String accessToken = config.accessToken;
        String refreshToken = config.refreshToken;

        if (clientId == null || clientId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Client ID is not configured");
            return;
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Client Secret is not configured");
            return;
        }

        // If tokens are missing, wait for authorization via the servlet
        if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "Please authorize your Withings account at http(s)://[YOUR_OPENHAB]/withings");
            return;
        }

        initializeWithTokens(clientId, clientSecret, accessToken, refreshToken);
    }

    /**
     * Creates the API client and verifies connectivity.
     */
    private void initializeWithTokens(String clientId, String clientSecret, String accessToken, String refreshToken) {
        WithingsApiClient client = new WithingsApiClient(clientId, clientSecret, accessToken, refreshToken);
        client.setTokenRefreshCallback(this);
        this.apiClient = client;

        // Verify connection by fetching measurements (only requires user.metrics scope)
        scheduler.execute(() -> {
            try {
                var response = client.getMeasurements(0, 0);
                if (response != null) {
                    logger.info("Withings account connected successfully");
                    updateStatus(ThingStatus.ONLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Failed to connect to Withings API. Token may need refresh.");
                }
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Error connecting to Withings API: " + e.getMessage());
            }
        });
    }

    @Override
    public void dispose() {
        // Unregister from servlet
        servlet.removeBridgeHandler(this);

        WithingsApiClient client = this.apiClient;
        if (client != null) {
            client.dispose();
            this.apiClient = null;
        }
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge has no channels to handle commands for
    }

    /**
     * Returns the API client for use by child thing handlers.
     *
     * @return the API client, or null if not initialized
     */
    public @Nullable WithingsApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Called by the servlet after successful OAuth2 authorization. Persists the tokens
     * and reinitializes the bridge with the new credentials.
     *
     * @param accessToken the new access token
     * @param refreshToken the new refresh token
     */
    public void handleAuthorizationComplete(String accessToken, String refreshToken) {
        logger.info("Received new OAuth2 tokens via servlet callback, reinitializing bridge");

        // Persist tokens to bridge configuration
        Configuration config = editConfiguration();
        config.put("accessToken", accessToken);
        config.put("refreshToken", refreshToken);
        updateConfiguration(config);

        // Dispose the old client if any, then reinitialize
        WithingsApiClient client = this.apiClient;
        if (client != null) {
            client.dispose();
            this.apiClient = null;
        }

        WithingsAccountConfiguration bridgeConfig = getConfigAs(WithingsAccountConfiguration.class);
        String clientId = bridgeConfig.clientId;
        String clientSecret = bridgeConfig.clientSecret;

        if (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()) {
            initializeWithTokens(clientId, clientSecret, accessToken, refreshToken);
        }
    }

    /**
     * Called when the API client refreshes tokens. Persists new tokens to the bridge configuration.
     */
    @Override
    public void onTokenRefreshed(String newAccessToken, String newRefreshToken) {
        logger.debug("Persisting refreshed Withings tokens to bridge configuration");

        Configuration config = editConfiguration();
        config.put("accessToken", newAccessToken);
        config.put("refreshToken", newRefreshToken);
        updateConfiguration(config);
    }

    // ===== Config accessors for the servlet =====

    /**
     * Returns the configured Client ID.
     */
    public @Nullable String getClientId() {
        return getConfigAs(WithingsAccountConfiguration.class).clientId;
    }

    /**
     * Returns the configured Client Secret.
     */
    public @Nullable String getClientSecret() {
        return getConfigAs(WithingsAccountConfiguration.class).clientSecret;
    }

    /**
     * Returns the configured Redirect URI.
     */
    public String getRedirectUri() {
        String uri = getConfigAs(WithingsAccountConfiguration.class).redirectUri;
        return uri != null ? uri : "";
    }
}
