package ru.bruimafia.pixabaylite.util

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val NAME = "ru.bruimafia.pixabaylite"
    private const val IS_FULL_VERSION = "FULL_VERSION" // версия приложения
    private const val IS_FIRST_LAUNCH = "FIRST_LAUNCH" // первый запуск

    private lateinit var sPref: SharedPreferences

    fun init(context: Context) {
        sPref = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    var isFullVersion: Boolean
        get() = sPref.getBoolean(IS_FULL_VERSION, false)
        set(value) {
            sPref.edit().putBoolean(IS_FULL_VERSION, value).apply()
        }

    var isFirstLaunch: Boolean
        get() = sPref.getBoolean(IS_FIRST_LAUNCH, true)
        set(value) {
            sPref.edit().putBoolean(IS_FIRST_LAUNCH, value).apply()
        }
}