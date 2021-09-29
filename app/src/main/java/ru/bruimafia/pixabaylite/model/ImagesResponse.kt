package ru.bruimafia.pixabaylite.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ImagesResponse(
    @SerializedName("hits")
    var images: List<Image>
)