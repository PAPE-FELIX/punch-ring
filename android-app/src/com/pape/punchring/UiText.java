package com.pape.punchring;

import android.content.Context;
import android.os.LocaleList;

import java.util.Locale;

/** Small runtime string selector for the programmatic UI. */
final class UiText {
    private UiText() {}

    static boolean isKorean(Context context) {
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        Locale locale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        return "ko".equalsIgnoreCase(locale.getLanguage());
    }

    static String get(Context context, String korean, String english) {
        return isKorean(context) ? korean : english;
    }

    static Locale formatLocale(Context context) {
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }
}
