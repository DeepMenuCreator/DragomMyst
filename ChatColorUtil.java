package ru.myst.manager;

public class ChatColorUtil {
    public static String strip(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)&[0-9A-FK-OR]", "");
    }
}
