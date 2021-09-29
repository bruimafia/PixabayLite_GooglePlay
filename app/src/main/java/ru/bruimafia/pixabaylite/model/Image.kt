package ru.bruimafia.pixabaylite.model

data class Image(
    val collections: Int,
    val comments: Int,
    val downloads: Int,
    val fullHDURL: String,
    val id: Int,
    val id_hash: String,
    val imageHeight: Int,
    val imageSize: Int,
    val imageURL: String,
    val imageWidth: Int,
    val largeImageURL: String,
    val likes: Int,
    val pageURL: String,
    val previewHeight: Int,
    val previewURL: String,
    val previewWidth: Int,
    val tags: String,
    val type: String,
    val user: String,
    val userImageURL: String,
    val user_id: Int,
    val views: Int,
    val webformatHeight: Int,
    val webformatURL: String,
    val webformatWidth: Int
)