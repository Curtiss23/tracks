package com.Curtis.music

class MusicRepository(private val api: MusicApiService) {
    suspend fun getSongs(): List<Song> = api.getSongs()
    suspend fun uploadSong(title: String, artist: String): Boolean = api.uploadSong(title, artist)
    suspend fun login(username: String, password: String): AuthResponse = api.login(LoginRequest(username, password))
} 