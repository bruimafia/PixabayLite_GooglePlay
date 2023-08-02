package ru.bruimafia.pixabaylite.model.video

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
@Keep
data class VideoResponse(
    @SerializedName("hits")
    val videos: List<Video>
)