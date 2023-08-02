package ru.bruimafia.pixabaylite.model.video

import androidx.annotation.Keep
@Keep
data class Tiny(
    val height: Int,
    val size: Int,
    val url: String,
    val width: Int
)