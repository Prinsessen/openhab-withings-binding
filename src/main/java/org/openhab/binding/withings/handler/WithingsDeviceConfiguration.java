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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration class for the Withings Device thing.
 * Device things are auto-discovered under a person bridge.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsDeviceConfiguration {

    /** Withings internal device ID — set automatically during discovery. */
    public String deviceId = "";

    /** Polling interval for device info (battery, model, last session) in minutes. */
    public int pollingIntervalDevice = 60;
}
