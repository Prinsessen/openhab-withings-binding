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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.withings.dto.WithingsApiResponse;
import org.openhab.binding.withings.dto.WithingsApiResponse.Device;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WithingsDiscoveryService} discovers Withings physical devices (watches, scales, etc.)
 * registered under a {@link WithingsPersonHandler} (person bridge).
 * Each device results in a {@code withings:device} thing in the Inbox.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = WithingsDiscoveryService.class)
@NonNullByDefault
public class WithingsDiscoveryService extends AbstractThingHandlerDiscoveryService<WithingsPersonHandler> {

    private final Logger logger = LoggerFactory.getLogger(WithingsDiscoveryService.class);
    private static final int DISCOVER_TIMEOUT_SECONDS = 15;

    public WithingsDiscoveryService() {
        super(WithingsPersonHandler.class, Set.of(THING_TYPE_DEVICE), DISCOVER_TIMEOUT_SECONDS);
    }

    @Override
    protected void startScan() {
        logger.debug("Starting Withings device discovery");

        WithingsPersonHandler handler = thingHandler;
        if (handler == null) {
            logger.warn("Cannot discover devices: person handler is null");
            return;
        }

        WithingsApiClient client = handler.getApiClient();
        if (client == null) {
            logger.warn("Cannot discover devices: no API client (account bridge offline?)");
            return;
        }

        WithingsApiResponse response = client.getDevices();
        if (response == null || response.body == null) {
            logger.warn("Cannot discover devices: empty response from Withings API");
            return;
        }

        List<Device> devices = response.body.devices;
        if (devices == null || devices.isEmpty()) {
            logger.info("No Withings devices found for this account");
            return;
        }

        ThingUID bridgeUID = handler.getThing().getUID();
        for (Device device : devices) {
            discoverDevice(bridgeUID, device);
        }

        logger.info("Discovered {} Withings device(s)", devices.size());
    }

    private void discoverDevice(ThingUID bridgeUID, Device device) {
        String deviceId = device.deviceid;
        if (deviceId == null || deviceId.isEmpty()) {
            logger.debug("Skipping device with null/empty deviceid");
            return;
        }

        // Use hash_deviceid as thing ID suffix (safe for UID — no special chars)
        String hashId = device.hash_deviceid;
        String thingIdSuffix = (hashId != null && !hashId.isEmpty()) ? hashId : deviceId;

        ThingUID thingUID = new ThingUID(THING_TYPE_DEVICE, bridgeUID, thingIdSuffix);

        String deviceModel = device.model;
        String label = deviceModel != null && !deviceModel.isEmpty() ? "Withings " + deviceModel : "Withings Device";

        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_DEVICE_ID, deviceId);
        properties.put(PROPERTY_DEVICE_MODEL_NAME, deviceModel != null ? deviceModel : "");
        properties.put("deviceId", deviceId); // config param

        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                .withProperties(properties).withRepresentationProperty(PROPERTY_DEVICE_ID).withLabel(label).build();

        thingDiscovered(result);
        logger.debug("Discovered Withings device: {} ({})", label, deviceId);
    }
}
