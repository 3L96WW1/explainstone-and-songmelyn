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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 简易配置：config/explainstone.json */
public final class ExplainstoneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Boolean broadcastRequiresOp;

    private ExplainstoneConfig() {
    }

    private static Path path() {
        return Explainstone.platform().configDir().resolve("explainstone.json");
    }

    public static boolean broadcastRequiresOp() {
        if (broadcastRequiresOp != null) {
            return broadcastRequiresOp;
        }
        boolean value = false;
        Path file = path();
        try {
            if (Files.isRegularFile(file)) {
                JsonObject obj = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (obj != null && obj.has("broadcastRequiresOp")) {
                    value = obj.get("broadcastRequiresOp").getAsBoolean();
                }
            } else {
                JsonObject obj = new JsonObject();
                obj.addProperty("broadcastRequiresOp", false);
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(obj));
            }
        } catch (IOException | RuntimeException ignored) {
        }
        broadcastRequiresOp = value;
        return value;
    }
}
