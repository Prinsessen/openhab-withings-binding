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
 * Configuration class for the Withings Person thing.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
public class WithingsPersonConfiguration {

    public int userId;
    public int pollingIntervalBody = 15;
    public int pollingIntervalActivity = 30;
    public int pollingIntervalSleep = 60;
    public int pollingIntervalDevice = 60;

    /**
     * Optional: filter device status channels to a specific device model name.
     * If empty, the most recently synced device is used.
     * Example: "ScanWatch", "Body+", "BPM Connect"
     */
    public @org.eclipse.jdt.annotation.Nullable String deviceModel;
}
