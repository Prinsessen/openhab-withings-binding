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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.withings.dto.WithingsApiResponse;
import org.openhab.binding.withings.dto.WithingsApiResponse.Device;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WithingsDeviceHandler} handles a single Withings physical device (watch, scale, etc.)
 * discovered under a {@link WithingsPersonHandler} bridge. It polls device status (battery,
 * model, last session) for the specific device identified by {@code deviceId}.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsDeviceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(WithingsDeviceHandler.class);

    private @Nullable ScheduledFuture<?> pollingJob;
    private String deviceId = "";

    public WithingsDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        WithingsDeviceConfiguration config = getConfigAs(WithingsDeviceConfiguration.class);
        deviceId = config.deviceId.trim();

        if (deviceId.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "deviceId must be configured");
            return;
        }

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "No parent person bridge configured");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);

        long intervalSeconds = config.pollingIntervalDevice * 60L;
        pollingJob = scheduler.scheduleWithFixedDelay(this::pollDevice, 5, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::pollDevice);
        }
    }

    // ==================== Device Polling ====================

    private void pollDevice() {
        WithingsPersonHandler personHandler = getPersonHandler();
        if (personHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "Person bridge handler not available");
            return;
        }

        WithingsApiClient client = personHandler.getApiClient();
        if (client == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                    "No API client — account bridge offline?");
            return;
        }

        try {
            WithingsApiResponse response = client.getDevices();
            if (response == null || response.body == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Empty response from Withings API");
                return;
            }

            List<Device> devices = response.body.devices;
            if (devices == null || devices.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "No devices returned by Withings API");
                return;
            }

            Device device = findDevice(devices);
            if (device == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Device with id '" + deviceId + "' not found in account");
                return;
            }

            updateChannels(device);
            updateStatus(ThingStatus.ONLINE);

        } catch (Exception e) {
            logger.warn("Error polling Withings device {}: {}", deviceId, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    private @Nullable Device findDevice(List<Device> devices) {
        for (Device d : devices) {
            if (deviceId.equals(d.deviceid) || deviceId.equals(d.hash_deviceid)) {
                return d;
            }
        }
        return null;
    }

    private void updateChannels(Device device) {
        String battery = device.battery;
        if (battery != null && !battery.isEmpty()) {
            updateState(CHANNEL_GROUP_DEVICE + "#" + CHANNEL_DEVICE_BATTERY, new StringType(battery));
        }

        String model = device.model;
        if (model != null && !model.isEmpty()) {
            updateState(CHANNEL_GROUP_DEVICE + "#" + CHANNEL_DEVICE_MODEL, new StringType(model));
        }

        if (device.last_session_date > 0) {
            ZonedDateTime lastSession = ZonedDateTime.ofInstant(Instant.ofEpochSecond(device.last_session_date),
                    ZoneId.systemDefault());
            updateState(CHANNEL_GROUP_DEVICE + "#" + CHANNEL_DEVICE_LAST_SESSION, new DateTimeType(lastSession));
        }

        logger.debug("Withings device '{}' updated: model={}, battery={}", deviceId, device.model, device.battery);
    }

    // ==================== Helpers ====================

    private @Nullable WithingsPersonHandler getPersonHandler() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof WithingsPersonHandler personHandler) {
            return personHandler;
        }
        return null;
    }
}
