package ru.bruimafia.pixabaylite.model.image

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class ImagesResponse(
    @SerializedName("hits")
    var images: List<Image>
)