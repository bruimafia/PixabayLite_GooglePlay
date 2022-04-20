package ru.bruimafia.pixabaylite.util

import android.content.Context
import android.content.SharedPreferences


object SharedPreferencesManager {
    private const val NAME = "com.bruimafia.donotforget"
    private const val IS_PLAY_RATING = "PLAY_RATING" // оценка игры (Google play)
    private const val IS_FULL_VERSION = "FULL_VERSION" // версия приложения

    private lateinit var sPref: SharedPreferences

    fun init(context: Context) {
        sPref = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    var isPlayRating: Boolean
        get() = sPref.getBoolean(IS_PLAY_RATING, false)
        set(value) {
            sPref.edit().putBoolean(IS_PLAY_RATING, value).apply()
        }

    var isFullVersion: Boolean
        get() = sPref.getBoolean(IS_FULL_VERSION, false)
        set(value) {
            sPref.edit().putBoolean(IS_FULL_VERSION, value).apply()
        }
}