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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.withings.servlet.WithingsServlet;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link WithingsHandlerFactory} is responsible for creating thing handlers.
 *
 * @author OpenHAB Withings Binding - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.withings", service = ThingHandlerFactory.class)
public class WithingsHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_PERSON,
            THING_TYPE_DEVICE);

    private final WithingsServlet withingsServlet;
    private final StorageService storageService;

    @Activate
    public WithingsHandlerFactory(@Reference WithingsServlet withingsServlet,
            @Reference StorageService storageService) {
        this.withingsServlet = withingsServlet;
        this.storageService = storageService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            return new WithingsAccountHandler((Bridge) thing, withingsServlet, storageService);
        } else if (THING_TYPE_PERSON.equals(thingTypeUID)) {
            return new WithingsPersonHandler((Bridge) thing);
        } else if (THING_TYPE_DEVICE.equals(thingTypeUID)) {
            return new WithingsDeviceHandler(thing);
        }

        return null;
    }
}
