/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.platform;

import java.nio.file.Path;

/** Loader-neutral access to the few environment facts the mod needs. */
public interface Platform {
    Path configDir();

    boolean isModLoaded(String modId);

    String modVersion();
}
