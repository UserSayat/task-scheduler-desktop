package org.example.taskschedulerdesktop.shared.ui;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Системные скруглённые углы и тень Windows 11 для окон со своей рамкой.
 * <p>
 * Окно без системной рамки Windows 11 скругляет сама, если попросить её об этом. Тогда углы, тень и тонкий
 * контур — как у остальных окон системы, и это ничего не стоит приложению. В Windows 10 и на macOS такого нет:
 * там окно с прямыми углами и контуром без тени (тень пришлось бы рисовать в прозрачном окне, а это заметно
 * нагружает процессор). Отключить для проверки: {@code -Dtaskhub.drawnCorners=true}.
 */
public final class NativeCorners {

    private static final Logger log = LoggerFactory.getLogger(NativeCorners.class);

    /** DWMWA_WINDOW_CORNER_PREFERENCE и его значения: DWMWCP_DONOTROUND, DWMWCP_ROUND. */
    private static final int CORNER_PREFERENCE = 33;
    private static final int SQUARE = 1;
    private static final int ROUND = 2;
    /** Первая сборка Windows 11. */
    private static final int WINDOWS_11_BUILD = 22000;
    private static final String HANDLE_KEY = NativeCorners.class.getName() + ".hwnd";

    private static final boolean WINDOWS_11 = detectWindows11();
    private static final boolean SUPPORTED = WINDOWS_11 && !Boolean.getBoolean("taskhub.drawnCorners");

    private NativeCorners() {}

    interface Dwm extends StdCallLibrary {
        int DwmSetWindowAttribute(HWND hwnd, int attribute, IntByReference value, int size);
    }

    /** Умеет ли система скруглять окна сама. Решается до показа окна: от этого зависит стиль окна. */
    public static boolean supported() {
        return SUPPORTED;
    }

    /** Скруглить окно, как только оно появится на экране. Вызывать до показа. */
    public static void install(Stage stage) {
        if (!SUPPORTED) {
            return;
        }
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> setRounded(stage, true));
        // После скрытия у окна будет новый системный дескриптор
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stage.getProperties().remove(HANDLE_KEY));
    }

    /** Скруглённые углы или прямые (развёрнутое окно, весь экран). */
    public static void setRounded(Stage stage, boolean rounded) {
        if (!SUPPORTED || !stage.isShowing()) {
            return;
        }
        try {
            HWND hwnd = handle(stage);
            if (hwnd == null) {
                return;
            }
            int result = Holder.DWM.DwmSetWindowAttribute(hwnd, CORNER_PREFERENCE,
                    new IntByReference(rounded ? ROUND : SQUARE), Integer.BYTES);
            if (result != 0) {
                log.debug("DwmSetWindowAttribute failed: 0x{}", Integer.toHexString(result));
            }
        } catch (Throwable e) {
            log.debug("Native corners unavailable: {}", e.toString());
        }
    }

    /** Системный дескриптор окна: на мгновение даём окну уникальный заголовок и ищем окно по нему. */
    private static HWND handle(Stage stage) {
        if (stage.getProperties().get(HANDLE_KEY) instanceof HWND cached) {
            return cached;
        }
        if (stage.titleProperty().isBound()) {
            return null;
        }
        String title = stage.getTitle();
        String marker = "taskhub-window-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        stage.setTitle(marker);
        HWND hwnd;
        try {
            hwnd = User32.INSTANCE.FindWindow(null, marker);
        } finally {
            stage.setTitle(title);
        }
        if (hwnd != null) {
            stage.getProperties().put(HANDLE_KEY, hwnd);
        }
        return hwnd;
    }

    /** Сборку Windows для проверки вида можно задать явно: {@code -Dtaskhub.windowsBuild=19045} — Windows 10. */
    private static boolean detectWindows11() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return false;
        }
        try {
            String build = System.getProperty("taskhub.windowsBuild");
            if (build == null) {
                build = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                        "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuildNumber");
            }
            return Integer.parseInt(build.trim()) >= WINDOWS_11_BUILD;
        } catch (Throwable e) {
            log.debug("Cannot detect Windows build: {}", e.toString());
            return false;
        }
    }

    // Отдельный класс: dwmapi загружается при первом вызове, и только там, где скругление поддерживается
    private static final class Holder {
        static final Dwm DWM = Native.load("dwmapi", Dwm.class);
    }
}
