package com.Curtis.music

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part

interface MusicApiService {
    @GET("songs")
    suspend fun getSongs(): List<Song> // Replace with Response<List<Song>> for real backend

    @Multipart
    @POST("upload")
    suspend fun uploadSong(
        @Part("title") title: String,
        @Part("artist") artist: String,
        // @Part file: MultipartBody.Part // Uncomment for real file upload
    ): Boolean

    @POST("login")
    suspend fun login(@Body credentials: LoginRequest): AuthResponse
}

data class LoginRequest(val username: String, val password: String)
data class AuthResponse(val token: String) 