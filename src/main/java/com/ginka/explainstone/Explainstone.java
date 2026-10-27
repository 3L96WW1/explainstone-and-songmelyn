/*
 * explainstone —— 讲解石 / 颂美铃 / 广播发信器（Minecraft Fabric 1.21.1 模组）
 *
 *  作者 / Author : GINKA_3L96WW1
 *
 * 本文件由作者手工编写；贴图与模型由 tools/ 下的脚本程序化生成。
 * 保留此注释即可随意修改、分发。 喵
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * ========================= 676767676 nyanya=========================

 *      _,met$$$$$gg.             karine@Komorebi
 *    ,g$$$$$$$$$$$$$$$P.         ---------------
 *  ,g$$P""       """Y$$.".       OS: Debian GNU/Linux 13 (trixie) x86_64
 * ,$$P'              `$$$.       Host: 21N5 (ThinkBook 16P G5 IRX)
 *',$$P       ,ggs.     `$$b:     Kernel: Linux 6.12.113+deb13-amd64
 *`d$$'     ,$P"'   .    $$$      Uptime: 14 hours, 49 mins
 * $$P      d$'     ,    $$P      Packages: 2114 (dpkg), 7 (snap)
 * $$:      $$.   -    ,d$$'      Shell: zsh 5.9.2
 * $$;      Y$b._   _,d$P'        Display (LC34G55T): 3440x1440 @ 165 Hz [External]
 *  $$b      "-.__                Display (CSO1626): 3200x2000 @ 67 Hz in 16" [External]
 *  `Y$$.                         Display (S24A31x): 1080x1920 @ 67 Hz in 24" [External]
 *   `Y$$b.                       DE: GNOME 48.7
 *     `"Y$b._                    WM: Mutter (X11)
 *         `"""                    WM Theme: Adwaita
 *
 *
 * ======================================================================
 */
package com.ginka.explainstone;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Explainstone implements ModInitializer {
	// 定义模组ID喵
	public static final String MOD_ID = "explainstone";
	// 定义日志记录器，方便在控制台看输出喵
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 打印一句欢迎语喵
		LOGGER.info("Hello Fabric world!");

		// 加载方块喵
		ModBlocks.initialize();
	}
}