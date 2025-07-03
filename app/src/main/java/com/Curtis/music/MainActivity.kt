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
import android.content.SharedPreferences
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TextButton
import com.Curtis.music.ui.theme.PremiumGradient
import com.Curtis.music.ui.theme.PremiumDark
import com.Curtis.music.ui.theme.HighContrastWhite
import com.Curtis.music.ui.theme.PremiumGradient
import com.Curtis.music.ui.theme.PremiumGold
import com.Curtis.music.ui.theme.PremiumDark
import com.Curtis.music.ui.theme.TrueBlack
import com.google.firebase.FirebaseApp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateListOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        val viewModel = PlayerViewModel()
        viewModel.getFcmToken()
        val prefs = getSharedPreferences("avant_garde_prefs", Context.MODE_PRIVATE)
        val isOnboardingComplete = prefs.getBoolean("onboarding_complete", false)
        setContent {
            MusicTheme {
                val navController = rememberNavController()
                var showOnboarding by remember { mutableStateOf(!isOnboardingComplete) }
                if (showOnboarding) {
                    OnboardingScreen(onContinue = {
                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                        showOnboarding = false
                    })
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MusicAppNavigation(navController = navController, viewModel = viewModel)
                    }
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

data class SongAnalytics(
    val plays: Int = 0,
    val likes: Int = 0,
    val shares: Int = 0
)

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
    var isPremium by mutableStateOf(false)
    var artistBio by mutableStateOf<String?>(null)
    var artistLinks by mutableStateOf<String?>(null)
    var songAnalytics by mutableStateOf(mutableMapOf<Song, SongAnalytics>())

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun initializePlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying_: Boolean) {
                        this@PlayerViewModel.isPlaying = isPlaying_
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

    fun signOut() {
        auth.signOut()
        isLoggedIn = false
    }
}

// --- Navigation ---
@Composable
fun MusicAppNavigation(navController: NavHostController, viewModel: PlayerViewModel = PlayerViewModel()) {
    var isDarkTheme by remember { mutableStateOf(false) }
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
                    onBack = { navController.popBackStack() },
                    isPremium = viewModel.isPremium,
                    onGoPremium = { navController.navigate("goPremium") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onGoPremium = { navController.navigate("goPremium") },
                    onSettings = { navController.navigate("settingsProfile") },
                    onDistributeMusic = { navController.navigate("distributeMusic") },
                    onDiscover = { navController.navigate("discover") },
                    onFeed = { navController.navigate("feed") },
                    onPlaylists = { navController.navigate("playlists") },
                    onNotifications = { navController.navigate("notifications") },
                    onMessaging = { navController.navigate("messaging") },
                    onUploadHistory = { navController.navigate("uploadHistory") },
                    onLeaderboard = { navController.navigate("leaderboard") }
                )
            }
            composable("feed") {
                FeedScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("goPremium") {
                GoPremiumScreen(
                    onUpgrade = {
                        viewModel.isPremium = true
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("distributeMusic") {
                DistributeMusicScreen(onBack = { navController.popBackStack() })
            }
            composable("artistProfile") {
                ArtistProfileScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("songAnalytics") {
                SongAnalyticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable("discover") {
                DiscoverScreen(onBack = { navController.popBackStack() })
            }
            composable("notifications") {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable("messaging") {
                MessagingScreen(onBack = { navController.popBackStack() })
            }
            composable("settingsProfile") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("uploadHistory") {
                UploadHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable("leaderboard") {
                LeaderboardScreen(onBack = { navController.popBackStack() })
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
            .background(TrueBlack)
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
                .background(TrueBlack)
                .padding(padding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Your ProBeat Library",
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
                    SongListItem(song = displaySongs[idx], onClick = { onSongClick(displaySongs[idx]) }, onLike = {}, onComment = {})
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: Song, onClick: () -> Unit, onLike: () -> Unit, onComment: (String) -> Unit) {
    var comment by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrueBlack)
            .padding(16.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.Start
    ) {
        Text(song.title, style = MaterialTheme.typography.headlineMedium)
        Text("by ${song.artist}", color = HighContrastWhite)
        Row {
            Button(onClick = onLike, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Like")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Comment") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HighContrastWhite,
                    unfocusedBorderColor = HighContrastWhite,
                    cursorColor = HighContrastWhite
                ),
                textStyle = LocalTextStyle.current.copy(color = HighContrastWhite),
                modifier = Modifier.width(200.dp)
            )
            Button(onClick = { onComment(comment); comment = "" }, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Post")
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
            .background(TrueBlack),
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
    var isLoginMode by remember { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isLoginMode) "ProBeat Login" else "Register for ProBeat",
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
            if (isLoginMode) {
                Button(
                    onClick = { onLogin(email, password) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = DeepBlack)
                ) {
                    Text("Login")
                }
            } else {
                Button(
                    onClick = { onRegister(email, password) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)
                ) {
                    Text("Register")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(
                    if (isLoginMode) "Don't have an account? Register here" else "Already have an account? Login here",
                    color = NeonGreen
                )
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onAddPlaylist: (String) -> Unit,
    onBack: () -> Unit,
    isPremium: Boolean = false,
    onGoPremium: () -> Unit = {}
) {
    if (!isPremium) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumGradient),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Premium Feature", style = MaterialTheme.typography.displayLarge, color = PremiumGold)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Playlists are available for premium users only.", color = Color.White)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onGoPremium, colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PremiumDark)) {
                    Text("Go Premium")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)) {
                    Text("Back")
                }
            }
        }
        return
    }
    var newPlaylistName by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack),
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
fun ProfileScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoPremium: () -> Unit = {},
    onSettings: () -> Unit = {},
    onDistributeMusic: () -> Unit = {},
    onDiscover: () -> Unit = {},
    onFeed: () -> Unit = {},
    onPlaylists: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onMessaging: () -> Unit = {},
    onUploadHistory: () -> Unit = {},
    onLeaderboard: () -> Unit = {}
) {
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
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", style = MaterialTheme.typography.displayLarge, color = HighContrastWhite)
            if (viewModel.isPremium) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("★ Premium", color = HighContrastWhite, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Email: $email", color = HighContrastWhite)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Your Uploaded Songs:", color = HighContrastWhite, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        userSongs.forEach { song ->
            Text("- ${song.title}", color = HighContrastWhite)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.signOut() }, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Sign Out")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (!viewModel.isPremium) {
            Button(onClick = onGoPremium, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Go Premium")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDistributeMusic, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Distribute Your Music")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDiscover, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Discover")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onFeed, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Feed")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPlaylists, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Playlists")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNotifications, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Notifications")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onMessaging, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Messaging")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onUploadHistory, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Upload History")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLeaderboard, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Leaderboard")
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
            .background(TrueBlack)
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

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome to ProBeat!", style = MaterialTheme.typography.displayLarge, color = PremiumGold)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Experience premium features:", color = NeonGreen, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("• Premium sound quality\n• Exclusive playlists\n• Personalized recommendations\n• And more!", color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PremiumDark)) {
                Text("Get Started")
            }
        }
    }
}

@Composable
fun GoPremiumScreen(onUpgrade: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Go Premium!", style = MaterialTheme.typography.displayLarge, color = PremiumGold)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Unlock all features:", color = NeonGreen, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("• Premium sound quality\n• Exclusive playlists\n• Personalized recommendations\n• Early access to new features!", color = Color.White)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onUpgrade, colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PremiumDark)) {
                Text("Upgrade Now")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)) {
                Text("Back")
            }
        }
    }
}

@Composable
fun SettingsScreen(isDarkTheme: Boolean, onThemeToggle: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Settings", style = MaterialTheme.typography.displayLarge, color = PremiumGold)
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Theme", color = Color.White)
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onThemeToggle, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)) {
                    Text(if (isDarkTheme) "Disable" else "Enable")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)) {
                Text("Back")
            }
        }
    }
}

@Composable
fun DistributeMusicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedSongUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedSongUri = uri
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Distribute Your Music", style = MaterialTheme.typography.displayLarge, color = TrueBlack)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { launcher.launch("audio/*") }, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Select Song to Distribute")
            }
            selectedSongUri?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Selected: ${it.lastPathSegment}", color = HighContrastWhite)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Choose a distributor:", color = HighContrastWhite)
            Spacer(modifier = Modifier.height(16.dp))
            DistributorLink("DistroKid", "https://distrokid.com/")
            DistributorLink("TuneCore", "https://www.tunecore.com/")
            DistributorLink("CD Baby", "https://cdbaby.com/")
            DistributorLink("Amuse", "https://www.amuse.io/")
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Back")
            }
        }
    }
}

@Composable
fun DistributorLink(name: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }) {
        Text(name, color = TrueBlack)
    }
}

@Composable
fun ArtistProfileScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    var bio by remember { mutableStateOf(viewModel.artistBio ?: "") }
    var links by remember { mutableStateOf(viewModel.artistLinks ?: "") }
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Artist Profile", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (editing) {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HighContrastWhite,
                    unfocusedBorderColor = HighContrastWhite,
                    cursorColor = HighContrastWhite
                ),
                textStyle = LocalTextStyle.current.copy(color = HighContrastWhite)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = links,
                onValueChange = { links = it },
                label = { Text("Links (comma separated)") },
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HighContrastWhite,
                    unfocusedBorderColor = HighContrastWhite,
                    cursorColor = HighContrastWhite
                ),
                textStyle = LocalTextStyle.current.copy(color = HighContrastWhite)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                viewModel.artistBio = bio
                viewModel.artistLinks = links
                editing = false
            }, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Save")
            }
        } else {
            Text("Bio: $bio", color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Links: $links", color = HighContrastWhite)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { editing = true }, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Edit Profile")
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun SongAnalyticsScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val analytics = viewModel.songAnalytics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Song Analytics", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        analytics.forEach { (song, data) ->
            Text("${song.title} - Plays: ${data.plays}, Likes: ${data.likes}, Shares: ${data.shares}", color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun DiscoverScreen(onBack: () -> Unit) {
    val trendingSongs = listOf(
        Song("101", "Blackout", "DJ Mono", "", ""),
        Song("102", "Night Drive", "Synth Noir", "", ""),
        Song("103", "Shadow Beat", "Darkwave", "", "")
    )
    var search by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Discover", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search songs or artists") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HighContrastWhite,
                unfocusedBorderColor = HighContrastWhite,
                cursorColor = HighContrastWhite
            ),
            textStyle = LocalTextStyle.current.copy(color = HighContrastWhite)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Trending Songs:", color = HighContrastWhite)
        trendingSongs.filter { it.title.contains(search, true) || it.artist.contains(search, true) }.forEach { song ->
            Text("${song.title} by ${song.artist}", color = HighContrastWhite)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun FeedScreen(onBack: () -> Unit) {
    val feedItems = listOf(
        "DJ Mono uploaded 'Blackout'",
        "Synth Noir liked 'Shadow Beat'",
        "Darkwave commented on 'Night Drive'"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Feed", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        feedItems.forEach { item ->
            Text(item, color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun PlaylistsScreen(onBack: () -> Unit) {
    val playlists = listOf(
        "Night Vibes",
        "Workout Mix",
        "Chill Sessions"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Playlists", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        playlists.forEach { name ->
            Text(name, color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val notifications = listOf(
        "You have a new follower!",
        "Your song 'Blackout' got 10 new plays.",
        "Synth Noir commented on your song."
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Notifications", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        notifications.forEach { note ->
            Text(note, color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun MessagingScreen(onBack: () -> Unit) {
    val messages = remember { mutableStateListOf<String>(
        "DJ Mono: Hey, check out my new track!",
        "You: Nice! I'll listen now.",
        "Synth Noir: Anyone up for a collab?"
    ) }
    var newMessage by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Messages", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        messages.forEach { msg ->
            Text(msg, color = HighContrastWhite)
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            OutlinedTextField(
                value = newMessage,
                onValueChange = { newMessage = it },
                label = { Text("Type a message") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HighContrastWhite,
                    unfocusedBorderColor = HighContrastWhite,
                    cursorColor = HighContrastWhite
                ),
                textStyle = LocalTextStyle.current.copy(color = HighContrastWhite),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (newMessage.isNotBlank()) {
                    messages.add("You: $newMessage")
                    newMessage = ""
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
                Text("Send")
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var username by remember { mutableStateOf("ProBeatUser") }
    var email by remember { mutableStateOf("user@probeat.com") }
    var darkMode by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HighContrastWhite,
                unfocusedBorderColor = HighContrastWhite,
                cursorColor = HighContrastWhite
            ),
            textStyle = LocalTextStyle.current.copy(color = HighContrastWhite)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HighContrastWhite,
                unfocusedBorderColor = HighContrastWhite,
                cursorColor = HighContrastWhite
            ),
            textStyle = LocalTextStyle.current.copy(color = HighContrastWhite)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark Mode", color = HighContrastWhite)
            Switch(checked = darkMode, onCheckedChange = { darkMode = it })
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun UploadHistoryScreen(onBack: () -> Unit) {
    val uploads = listOf(
        "Blackout",
        "Night Drive",
        "Shadow Beat"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Upload History", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        uploads.forEach { song ->
            Text(song, color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val leaders = listOf(
        "DJ Mono - 1000 plays",
        "Synth Noir - 900 plays",
        "Darkwave - 850 plays"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Leaderboard", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        leaders.forEach { entry ->
            Text(entry, color = HighContrastWhite)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = TrueBlack, contentColor = HighContrastWhite)) {
            Text("Back")
        }
    }
}

