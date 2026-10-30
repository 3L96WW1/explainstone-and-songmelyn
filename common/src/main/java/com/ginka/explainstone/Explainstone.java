/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器
 *
 * 作者 / Author : GINKA_3L96WW1
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone;

import com.ginka.explainstone.platform.Network;
import com.ginka.explainstone.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Explainstone {
    public static final String MOD_ID = "explainstone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Platform platform;
    private static Network network;

    private Explainstone() {
    }

    public static void init(Platform platform, Network network) {
        if (Explainstone.platform != null) {
            throw new IllegalStateException("Explainstone already initialized");
        }
        Explainstone.platform = platform;
        Explainstone.network = network;
        LOGGER.info("Explainstone common initialized");
    }

    public static Platform platform() {
        return platform;
    }

    public static Network network() {
        return network;
    }

    /** Client entrypoints replace the server-side network facade with a client-capable one. */
    public static void setNetwork(Network network) {
        Explainstone.network = network;
    }
}
