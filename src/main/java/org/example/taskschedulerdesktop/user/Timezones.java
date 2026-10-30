package org.example.taskschedulerdesktop.user;

import java.util.List;

/**
 * Часовые пояса для выбора в формах. Можно ввести и любой другой из базы IANA.
 */
public final class Timezones {

    public static final String DEFAULT = "Europe/Moscow";

    public static final List<String> COMMON = List.of(
            "Europe/Kaliningrad", "Europe/Moscow", "Europe/Samara", "Asia/Yekaterinburg", "Asia/Omsk",
            "Asia/Novosibirsk", "Asia/Krasnoyarsk", "Asia/Irkutsk", "Asia/Yakutsk", "Asia/Vladivostok",
            "Asia/Magadan", "Asia/Kamchatka", "Asia/Almaty", "Asia/Tashkent");

    private Timezones() {}
}
