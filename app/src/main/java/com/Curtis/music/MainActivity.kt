package com.Curtis.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.Curtis.music.ui.theme.AcidYellow
import com.Curtis.music.ui.theme.AvantGradient
import com.Curtis.music.ui.theme.DeepBlack
import com.Curtis.music.ui.theme.ElectricPurple
import com.Curtis.music.ui.theme.MusicTheme
import com.Curtis.music.ui.theme.NeonBlue
import com.Curtis.music.ui.theme.NeonGreen
import com.Curtis.music.ui.theme.NeonPink
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = PlayerViewModel()
        viewModel.getFcmToken()
        setContent {
            MusicTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MusicAppNavigation(navController = navController, viewModel = viewModel)
                }
            }
        }
    }
}

// --- Data Model ---
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val audioUrl: String
)

data class Playlist(val name: String, val songs: List<Song>)

// --- Placeholder ViewModel ---
class PlayerViewModel : ViewModel() {
    var songs by mutableStateOf(
        listOf(
            Song("1", "Neon Dreams", "DJ Avant", "https://picsum.photos/300?1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
            Song("2", "Electric Pulse", "Synthwave", "https://picsum.photos/300?2", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
            Song("3", "Acid Sunrise", "Futurist", "https://picsum.photos/300?3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3")
        )
    )
    var isPlaying by mutableStateOf(false)
    var currentSong: Song? by mutableStateOf(null)
    private var exoPlayer: ExoPlayer? = null
    var isLoggedIn by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)
    var isAuthLoading by mutableStateOf(false)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    var playlists by mutableStateOf(listOf<Playlist>())
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference
    var isUploading by mutableStateOf(false)
    var uploadError by mutableStateOf<String?>(null)
    private val analytics = Firebase.analytics

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun initializePlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying_: Boolean) {
                        isPlaying = isPlaying_
                    }
                })
            }
        }
    }

    fun playSong(context: Context, song: Song) {
        initializePlayer(context)
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(song.audioUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            isPlaying = true
            currentSong = song
        }
    }

    fun togglePlayback() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.play()
                isPlaying = true
            }
        }
    }

    fun skipNext(context: Context) {
        val idx = songs.indexOf(currentSong)
        val nextSong = songs.getOrNull((idx + 1) % songs.size)
        if (nextSong != null) playSong(context, nextSong)
    }
    fun skipPrevious(context: Context) {
        val idx = songs.indexOf(currentSong)
        val prevSong = songs.getOrNull(if (idx - 1 < 0) songs.size - 1 else idx - 1)
        if (prevSong != null) playSong(context, prevSong)
    }
    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun logEvent(name: String, params: Bundle.() -> Unit = {}) {
        val bundle = Bundle().apply(params)
        analytics.logEvent(name, bundle)
    }

    fun login(email: String, password: String) {
        isAuthLoading = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoggedIn = task.isSuccessful
                authError = if (task.isSuccessful) null else task.exception?.localizedMessage
                isAuthLoading = false
                if (task.isSuccessful) logEvent("login")
            }
    }

    fun register(email: String, password: String) {
        isAuthLoading = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoggedIn = task.isSuccessful
                authError = if (task.isSuccessful) null else task.exception?.localizedMessage
                isAuthLoading = false
                if (task.isSuccessful) logEvent("register")
            }
    }

    fun addPlaylist(name: String) {
        playlists = playlists + Playlist(name, emptyList())
    }

    // Upload file to Firebase Storage and save metadata to Firestore
    fun uploadSong(
        fileUri: Uri?,
        title: String,
        artist: String,
        onResult: (Boolean) -> Unit
    ) {
        if (fileUri == null) {
            uploadError = "No file selected"
            onResult(false)
            return
        }
        isUploading = true
        uploadError = null
        val fileRef = storage.child("songs/${System.currentTimeMillis()}_${title}.mp3")
        fileRef.putFile(fileUri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { uri ->
                    val song = Song(
                        id = firestore.collection("songs").document().id,
                        title = title,
                        artist = artist,
                        albumArtUrl = "https://picsum.photos/300?${System.currentTimeMillis()}",
                        audioUrl = uri.toString()
                    )
                    firestore.collection("songs").add(song)
                        .addOnSuccessListener {
                            isUploading = false
                            logEvent("upload_song") { putString("title", title); putString("artist", artist) }
                            onResult(true)
                        }
                        .addOnFailureListener { e ->
                            uploadError = e.localizedMessage
                            isUploading = false
                            onResult(false)
                        }
                }
            }
            .addOnFailureListener { e ->
                uploadError = e.localizedMessage
                isUploading = false
                onResult(false)
            }
    }

    // Fetch songs from Firestore
    fun fetchSongs(onResult: (List<Song>) -> Unit) {
        firestore.collection("songs").get()
            .addOnSuccessListener { result ->
                val songs = result.mapNotNull { it.toObject(Song::class.java) }
                onResult(songs)
            }
    }

    fun getFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "FCM Token: $token")
            } else {
                Log.e("FCM", "Failed to get FCM token", task.exception)
            }
        }
    }

    fun getSongById(id: String?): Song? = songs.find { it.id == id }
}

// --- Navigation ---
@Composable
fun MusicAppNavigation(navController: NavHostController, viewModel: PlayerViewModel = PlayerViewModel()) {
    if (!viewModel.isLoggedIn) {
        LoginScreen(
            onLogin = { email, password -> viewModel.login(email, password) },
            onRegister = { email, password -> viewModel.register(email, password) },
            error = viewModel.authError,
            isLoading = viewModel.isAuthLoading
        )
    } else {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                MusicLibraryScreen(
                    songs = viewModel.songs,
                    onSongClick = { song ->
                        viewModel.currentSong = song
                        navController.navigate("player/${song.id}")
                    },
                    onUploadClick = {
                        navController.navigate("upload")
                    },
                    onPlaylistsClick = {
                        navController.navigate("playlists")
                    },
                    onProfileClick = {
                        navController.navigate("profile")
                    },
                    onFeedClick = {
                        navController.navigate("feed")
                    },
                    viewModel = viewModel
                )
            }
            composable("player/{songId}", arguments = listOf(navArgument("songId") { type = NavType.StringType })) { backStackEntry ->
                val songId = backStackEntry.arguments?.getString("songId")
                val song = viewModel.getSongById(songId)
                val context = LocalContext.current
                if (song != null) {
                    MusicPlayerScreen(
                        song = song,
                        isPlaying = viewModel.isPlaying,
                        onPlayPause = { viewModel.togglePlayback() },
                        onSkipNext = { viewModel.skipNext(context) },
                        onSkipPrevious = { viewModel.skipPrevious(context) },
                        viewModel = viewModel
                    )
                }
            }
            composable("upload") {
                UploadScreen(onUploadComplete = { navController.popBackStack() }, viewModel = viewModel)
            }
            composable("playlists") {
                PlaylistsScreen(
                    playlists = viewModel.playlists,
                    onAddPlaylist = { name -> viewModel.addPlaylist(name) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("profile") {
                ProfileScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("feed") {
                FeedScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    DisposableEffect(song) {
        viewModel.playSong(context, song)
        viewModel.logEvent("play_song") { putString("title", song.title); putString("artist", song.artist) }
        onDispose { viewModel.releasePlayer() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AvantGradient)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = song.albumArtUrl,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(NeonPink, NeonBlue, Color.Transparent))),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.displayLarge,
                color = NeonPink
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.headlineMedium,
                color = NeonBlue
            )
            Spacer(modifier = Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = 0.5f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = NeonGreen,
                trackColor = DeepBlack
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = NeonBlue,
                        modifier = Modifier.size(48.dp)
                    )
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(80.dp)
                        .background(Brush.radialGradient(listOf(NeonPink, NeonBlue)), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = AcidYellow,
                        modifier = Modifier.size(56.dp)
                    )
                }
                IconButton(onClick = onSkipNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = NeonBlue,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Check out this song: ${song.title}")
                        putExtra(Intent.EXTRA_TEXT, "Listen to '${song.title}' by ${song.artist}: ${song.audioUrl}")
                    }
                    startActivity(context, Intent.createChooser(shareIntent, "Share Song"), null)
                    viewModel.logEvent("share_song") { putString("title", song.title); putString("artist", song.artist) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DeepBlack)
            ) {
                Text("Share")
            }
        }
    }
}

@Composable
fun MusicLibraryScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onUploadClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onFeedClick: () -> Unit,
    viewModel: PlayerViewModel
) {
    var remoteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.fetchSongs { fetched ->
            remoteSongs = fetched
        }
    }
    val displaySongs = if (remoteSongs.isNotEmpty()) remoteSongs else songs
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onUploadClick,
                containerColor = NeonPink,
                contentColor = DeepBlack
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Upload")
            }
        },
        containerColor = DeepBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AvantGradient)
                .padding(padding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Your Avant-Garde Library",
                    style = MaterialTheme.typography.displayLarge,
                    color = AcidYellow,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Row {
                    Button(
                        onClick = onFeedClick,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DeepBlack),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Feed")
                    }
                    Button(
                        onClick = onProfileClick,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Profile")
                    }
                    Button(
                        onClick = onPlaylistsClick,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = DeepBlack),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text("Playlists")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(displaySongs.size) { idx ->
                    SongListItem(song = displaySongs[idx], onClick = { onSongClick(displaySongs[idx]) })
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: Song, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = ElectricPurple)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumArtUrl,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(NeonBlue),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = song.title, style = MaterialTheme.typography.headlineMedium, color = NeonPink)
                Text(text = song.artist, style = MaterialTheme.typography.bodyLarge, color = AcidYellow)
            }
        }
    }
}

@Composable
fun UploadScreen(onUploadComplete: () -> Unit, viewModel: PlayerViewModel) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileName = uri?.lastPathSegment ?: ""
        fileUri = uri
    }
    val isUploading = viewModel.isUploading
    val uploadError = viewModel.uploadError
    var uploaded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AvantGradient),
        contentAlignment = Alignment.Center
    ) {
        if (uploaded) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Upload Successful!",
                    style = MaterialTheme.typography.displayLarge,
                    color = NeonGreen
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onUploadComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)
                ) {
                    Text("Back to Library")
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Upload your track",
                    style = MaterialTheme.typography.displayLarge,
                    color = NeonGreen
                )
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = NeonPink,
                        cursorColor = NeonGreen
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = NeonPink,
                        cursorColor = NeonGreen
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { filePicker.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)
                ) {
                    Text(if (fileName.isBlank()) "Pick Audio File" else fileName)
                }
                Spacer(modifier = Modifier.height(32.dp))
                if (uploadError != null) {
                    Text(text = uploadError, color = Color.Red)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (isUploading) {
                    CircularProgressIndicator(color = NeonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        viewModel.uploadSong(fileUri, title, artist) { success ->
                            if (success) uploaded = true
                        }
                    },
                    enabled = title.isNotBlank() && artist.isNotBlank() && fileName.isNotBlank() && !isUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)
                ) {
                    Text("Upload")
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    error: String?,
    isLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AvantGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Avant-Garde Music Login",
                style = MaterialTheme.typography.displayLarge,
                color = NeonPink
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonPink,
                    cursorColor = NeonGreen
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonPink,
                    cursorColor = NeonGreen
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (error != null) {
                Text(text = error, color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (isLoading) {
                CircularProgressIndicator(color = NeonGreen)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = { onLogin(email, password) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)
            ) {
                Text("Login")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onRegister(email, password) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)
            ) {
                Text("Register")
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onAddPlaylist: (String) -> Unit,
    onBack: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AvantGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Your Playlists",
                style = MaterialTheme.typography.displayLarge,
                color = NeonPink
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                label = { Text("New Playlist Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonPink,
                    cursorColor = NeonGreen
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onAddPlaylist(newPlaylistName)
                    newPlaylistName = ""
                },
                enabled = newPlaylistName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)
            ) {
                Text("Add Playlist")
            }
            Spacer(modifier = Modifier.height(32.dp))
            playlists.forEach { playlist ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ElectricPurple)
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = AcidYellow,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
fun ProfileScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val email = viewModel.currentUserEmail ?: "Unknown"
    var userSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.fetchSongs { fetched ->
            userSongs = fetched.filter { it.artist == email }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AvantGradient)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profile", style = MaterialTheme.typography.displayLarge, color = NeonPink)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Email: $email", color = AcidYellow)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Your Uploaded Songs:", color = NeonGreen, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        userSongs.forEach { song ->
            Text("- ${song.title}", color = NeonBlue)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)) {
            Text("Back")
        }
    }
}

@Composable
fun FeedScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.fetchSongs { fetched ->
            allSongs = fetched.sortedByDescending { it.id }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AvantGradient)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Social Feed", style = MaterialTheme.typography.displayLarge, color = NeonPink)
        Spacer(modifier = Modifier.height(16.dp))
        allSongs.take(20).forEach { song ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = ElectricPurple)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(song.title, color = AcidYellow, style = MaterialTheme.typography.headlineMedium)
                    Text("by ${song.artist}", color = NeonBlue)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)) {
            Text("Back")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMusicPlayerScreen() {
    val fakeViewModel = PlayerViewModel()
    MusicTheme {
        MusicPlayerScreen(
            song = Song("1", "Neon Dreams", "DJ Avant", "https://picsum.photos/300?1", ""),
            isPlaying = true,
            onPlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            viewModel = fakeViewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMusicLibraryScreen() {
    val fakeViewModel = PlayerViewModel()
    MusicTheme {
        MusicLibraryScreen(
            songs = listOf(
                Song("1", "Neon Dreams", "DJ Avant", "https://picsum.photos/300?1", ""),
                Song("2", "Electric Pulse", "Synthwave", "https://picsum.photos/300?2", "")
            ),
            onSongClick = {},
            onUploadClick = {},
            onPlaylistsClick = {},
            onProfileClick = {},
            onFeedClick = {},
            viewModel = fakeViewModel
        )
    }
}

