/**
 * Copyright (c) 2014,2018 by the respective copyright holders.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.magichomeled.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link MagicHomeLedBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Timofey Fedyanin - Initial contribution
 */
@NonNullByDefault
public class MagicHomeLedBindingConstants {

    private static final String BINDING_ID = "magichomeled";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_MAGIC_HOME = new ThingTypeUID(BINDING_ID, "magichomeled311");

    // List of all Channel ids
    public static final String POWER = "power";
    public static final String COLOR = "color";
    public static final String COLD_WHITE = "cold-white";
    public static final String WARN_WHITE = "warn-white";
}
