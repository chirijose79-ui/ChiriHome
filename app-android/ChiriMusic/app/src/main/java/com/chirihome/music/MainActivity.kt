package com.chirihome.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.album.AlbumScreen
import com.chirihome.music.ui.albumes.AlbumesScreen
import com.chirihome.music.ui.login.LoginScreen
import com.chirihome.music.ui.theme.ChiriMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reproductor.iniciar(this)
        enableEdgeToEdge()
        setContent {
            ChiriMusicTheme {
                val nav = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = nav,
                        startDestination = "login",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginScreen(alConectar = {
                                nav.navigate("albumes") {
                                    popUpTo("login") { inclusive = true }
                                }
                            })
                        }
                        composable("albumes") {
                            AlbumesScreen(alAbrir = { id -> nav.navigate("album/$id") })
                        }
                        composable("album/{id}") { AlbumScreen() }
                    }
                }
            }
        }
    }
}