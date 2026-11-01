/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.ginka.explainstone.client.audio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 让用户挑一个音频文件，返回绝对路径；用户取消或这个环境弹不出对话框时返回 null。
 * <p>
 * 调用方必须在后台线程调它——弹窗是阻塞的。
 * <p>
 * 为什么不用 AWT 一把梭：Linux 上的 {@code java.awt.FileDialog} 只走 X11，纯 Wayland
 * （GNOME、KDE、Hyprland 的默认会话）会直接抛 {@code AWTError: Can't connect to X11
 * window server}，而且它画的是 Java 自绘的 Motif 窗口，本来也不是系统原生选择器。
 * zenity / kdialog 在纯 Wayland 下能正常弹，所以 Unix 上优先用它们，AWT 只兜底。喵
 */
public final class AudioFilePicker {
    private AudioFilePicker() {
    }

    public static String pick(Path startDir, String title) {
        if (isUnixLike()) {
            for (String[] command : nativeDialogs(startDir, title)) {
                if (!commandExists(command[0])) {
                    continue;
                }
                // 找到一个就不再试下一个：用户点了取消就是取消，不该再弹第二次
                return runDialog(command);
            }
        }
        return awtDialog(startDir, title);
    }

    private static boolean isUnixLike() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("freebsd") || os.contains("unix");
    }

    /** 按桌面环境覆盖顺序排：zenity 在 KDE 下也能用，kdialog 只在 KDE 系有。 */
    static List<String[]> nativeDialogs(Path startDir, String title) {
        String dir = startDir == null ? "" : startDir.toString();
        return List.of(
                new String[]{"zenity", "--file-selection", "--title=" + title,
                        "--file-filter=*.ogg", "--filename=" + dir + File.separator},
                new String[]{"kdialog", "--title", title, "--getopenfilename", dir, "*.ogg"});
    }

    static boolean commandExists(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, name))) {
                return true;
            }
        }
        return false;
    }

    /** 跑一个选择器，路径从 stdout 读。取消（非 0 退出）或出错返回 null。 */
    static String runDialog(String[] command) {
        try {
            Process process = new ProcessBuilder(command).start();
            // 先读完 stdout 再 waitFor：大目录下管道可能写满，顺序反了会互相等
            byte[] output = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            String chosen = new String(output, StandardCharsets.UTF_8).trim();
            return exit == 0 && !chosen.isEmpty() ? chosen : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** AWT 能不能用：Android 没有 java.awt；Unix 上必须有 DISPLAY（纯 Wayland 会 AWTError）。 */
    static boolean awtAvailable() {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return false;
            }
            return !isUnixLike() || System.getenv("DISPLAY") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** AWT 兜底。Windows / macOS 上是原生对话框，Linux 上要靠 XWayland。 */
    static String awtDialog(Path startDir, String title) {
        if (!awtAvailable()) {
            return null;
        }
        String[] picked = {null};
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        // 必须建在 AWT 事件线程上：普通线程里 setVisible(true) 会永久阻塞且不抛异常喵
        javax.swing.SwingUtilities.invokeLater(() -> {
            java.awt.FileDialog dialog = null;
            try {
                dialog = new java.awt.FileDialog((java.awt.Frame) null, title, java.awt.FileDialog.LOAD);
                if (startDir != null) {
                    dialog.setDirectory(startDir.toString());
                }
                dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".ogg"));
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowOpened(java.awt.event.WindowEvent event) {
                        opened.countDown();
                    }
                });
                dialog.setVisible(true);
                if (dialog.getFile() != null) {
                    picked[0] = new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
                }
            } catch (Throwable ignored) {
                // 弹不出来就当没选
            } finally {
                opened.countDown();
                closed.countDown();
                if (dialog != null) {
                    dialog.dispose();
                }
            }
        });
        try {
            // 3 秒还没窗口出现，认为这个环境弹不出来，别再干等
            if (!opened.await(3, TimeUnit.SECONDS)) {
                return null;
            }
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return picked[0];
    }
}
