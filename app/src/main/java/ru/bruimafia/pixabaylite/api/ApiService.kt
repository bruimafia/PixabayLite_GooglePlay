package ru.bruimafia.pixabaylite.api

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query
import ru.bruimafia.pixabaylite.model.ImagesResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Url

import retrofit2.http.Streaming

interface ApiService {
    @GET("?key=12509481-d4dac1b6d0057180c8643284a&per_page=10")
    fun getImages(
        @Query("q") q: String,
        @Query("order") order: String,
        @Query("page") page: Int
    ): Observable<ImagesResponse>

    @Streaming
    @GET
    fun downloadImage(@Url url: String): Observable<Response<ResponseBody>>
}