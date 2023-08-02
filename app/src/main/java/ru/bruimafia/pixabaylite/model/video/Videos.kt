package ru.bruimafia.pixabaylite.model.video

import androidx.annotation.Keep
@Keep
data class Videos(
    val large: Large,
    val medium: Medium,
    val small: Small,
    val tiny: Tiny
)