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

import static org.openhab.binding.magichomeled.internal.MagicHomeLedBindingConstants.*;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.ittim.openhab.ledbinding.library.ControllerChannels;
import ru.ittim.openhab.ledbinding.library.DiscoveryFinder;
import ru.ittim.openhab.ledbinding.library.LedController;
import ru.ittim.openhab.ledbinding.library.PowerState;

/**
 * The {@link MagicHomeLedHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Timofey Fedyanin - Initial contribution
 */
@NonNullByDefault
public class MagicHomeLedHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(MagicHomeLedHandler.class);

    @Nullable
    private MagicHomeLedConfiguration config;

    @Nullable
    private LedController controller;

    private AtomicBoolean operable = new AtomicBoolean(false);

    public MagicHomeLedHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!operable.get()) {
            logger.error("Received command {} to non-operable controller {}", command, controller);
            return;
        }
        try {
            switch (channelUID.getId()) {
                case POWER:
                    if (command instanceof RefreshType) {
                        initController();
                    } else if (command instanceof OnOffType) {
                        OnOffType onOffCommand = (OnOffType) command;
                        switch (onOffCommand) {
                            case ON:
                                controller.turnOn();
                                break;
                            case OFF:
                                controller.turnOff();
                        }
                    }
                    return;
                case COLD_WHITE:
                    if (command instanceof RefreshType) {
                        initController();
                    } else if (command instanceof PercentType) {
                        PercentType percentCommand = (PercentType) command;
                        controller.setCw(percentCommand.intValue());
                    }
                    return;
                case COLOR:
                    HSBType hsbCommand;
                    if (command instanceof RefreshType) {
                        initController();
                        return;
                    } else if (command instanceof HSBType) {
                        hsbCommand = (HSBType) command;
                    } else if (command instanceof PercentType) {
                        ControllerChannels ch = controller.getChannels();
                        int r = ch.getR();
                        int g = ch.getG();
                        int b = ch.getB();
                        hsbCommand = HSBType.fromRGB(r, g, b);
                        hsbCommand = new HSBType(hsbCommand.getHue(), hsbCommand.getSaturation(),
                                (PercentType) command);
                    } else {
                        return;
                    }
                    int r = hsbCommand.getRed().intValue();
                    int g = hsbCommand.getGreen().intValue();
                    int b = hsbCommand.getBlue().intValue();
                    logger.debug("HSB converted to RGB: ({}, {}, {})", r, g, b);
                    controller.setRGB(r, g, b);
                    return;
                case WARN_WHITE:
                    if (command instanceof RefreshType) {
                        initController();
                        return;
                    } else if (command instanceof PercentType) {
                        PercentType percentCommand = (PercentType) command;
                        controller.setWw(percentCommand.intValue());
                    }
                    return;
            }
        } catch (Exception e) {
            logger.error("Communication error", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            operable.set(false);
            recovery(10);
        }
    }

    @Override
    public void initialize() {
        logger.debug("Start initializing!");
        config = getConfigAs(MagicHomeLedConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            Optional<LedController> target = null;
            if (config.discovery) {
                DiscoveryFinder discovery = new DiscoveryFinder.DiscoveryFinderBuilder()
                        .discoveryAddress(config.broadcastAddress).build();
                Set<LedController> controllers = discovery.getControllers();
                target = controllers.stream().filter(it -> {
                    boolean res = it.getMac().equalsIgnoreCase(config.mac);
                    if (res) {
                        logger.debug("Found target device {}", it);
                    } else {
                        logger.debug("Filtered device with mac {} (target {})", it.getMac(), config.mac);
                    }
                    return res;
                }).findFirst();
            }
            if (!target.isPresent() && config.host != null && !config.host.isEmpty()) {
                try {
                    target = Optional.of(new LedController(config.host, config.mac, "manual"));
                } catch (IOException e) {
                    logger.warn("Can't find thing by static address {}", config.mac, e);
                    updateStatus(ThingStatus.UNINITIALIZED, ThingStatusDetail.COMMUNICATION_ERROR);
                }
            }
            target.ifPresentOrElse(t -> {
                controller = t;
                initController();
            }, () -> {
                logger.error("Can't initialize controller");
                updateStatus(ThingStatus.UNKNOWN);
            });
        });

        logger.debug("Finished initializing!");

    }

    private boolean initController() {
        try {
            controller.init();
        } catch (IOException e) {
            updateStatus(ThingStatus.UNINITIALIZED, ThingStatusDetail.COMMUNICATION_ERROR);
            return false;
        }
        logger.debug("Initialized controller {}", controller);
        updateState(POWER, controller.getPower() == PowerState.ON ? OnOffType.ON : OnOffType.OFF);
        ControllerChannels ch = controller.getChannels();
        int cw = ch.getCw();
        updateState(COLD_WHITE, new PercentType(cw));
        int ww = ch.getWw();
        updateState(WARN_WHITE, new PercentType(ww));
        int r = ch.getR();
        int g = ch.getG();
        int b = ch.getB();
        HSBType color = HSBType.fromRGB(r, g, b);
        updateState(COLOR, color);
        updateStatus(ThingStatus.ONLINE);
        operable.set(true);
        return true;
    }

    AtomicBoolean inRecovery = new AtomicBoolean(false);

    private void recovery(int periodInSeconds) {
        scheduler.execute(() -> {
            if (inRecovery.getAndSet(true)) {
                return;
            }
            logger.debug("Try to recovery controller {}", controller);
            boolean recovered = false;
            while (!recovered) {
                try {
                    TimeUnit.SECONDS.sleep(periodInSeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Recovery for controller {} was interrupted", controller);
                    return;
                }
                recovered = initController();
            }
            logger.info("Controller {} recovered", controller);
            updateStatus(ThingStatus.ONLINE);
            inRecovery.set(false);
        });

    }
}
