package com.chirihome.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chirihome.music.data.Favoritos
import com.chirihome.music.data.Playlists
import com.chirihome.music.data.Sesion
import com.chirihome.music.player.Descargas
import com.chirihome.music.player.Reproductor
import com.chirihome.music.ui.Navegacion
import com.chirihome.music.ui.album.AlbumScreen
import com.chirihome.music.ui.albumes.AlbumesScreen
import com.chirihome.music.ui.buscar.BuscarScreen
import com.chirihome.music.ui.cola.ColaScreen
import com.chirihome.music.ui.descargas.DescargasScreen
import com.chirihome.music.ui.ecualizador.EcualizadorScreen
import com.chirihome.music.ui.favoritos.FavoritosScreen
import com.chirihome.music.ui.login.LoginScreen
import com.chirihome.music.ui.playlist.PlaylistScreen
import com.chirihome.music.ui.playlists.PlaylistsScreen
import com.chirihome.music.ui.theme.ChiriMusicTheme

class MainActivity : ComponentActivity() {

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sesion.init(this)
        val haySesion = Sesion.api != null || Sesion.restaurar()
        Reproductor.iniciar(this)
        Descargas.iniciar(this)
        if (Build.VERSION.SDK_INT >= 33) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            ChiriMusicTheme {
                val nav = rememberNavController()
                SideEffect {
                    Navegacion.abrirCola = { nav.navigate("cola") }
                    Navegacion.abrirEcualizador = { nav.navigate("ecualizador") }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = nav,
                        startDestination = if (haySesion) "albumes" else "login",
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
                            AlbumesScreen(
                                alAbrir = { id -> nav.navigate("album/$id") },
                                alBuscar = { nav.navigate("buscar") },
                                alFavoritos = { nav.navigate("favoritos") },
                                alPlaylists = { nav.navigate("playlists") },
                                alDescargas = { nav.navigate("descargas") },
                                alSalir = {
                                    Reproductor.detener()
                                    Favoritos.limpiar()
                                    Playlists.limpiar()
                                    Sesion.cerrar()
                                    nav.navigate("login") {
                                        popUpTo("albumes") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("buscar") {
                            BuscarScreen(alAbrirAlbum = { id -> nav.navigate("album/$id") })
                        }
                        composable("favoritos") { FavoritosScreen() }
                        composable("cola") { ColaScreen() }
                        composable("ecualizador") { EcualizadorScreen() }
                        composable("descargas") { DescargasScreen() }
                        composable("playlists") {
                            PlaylistsScreen(alAbrir = { id -> nav.navigate("playlist/$id") })
                        }
                        composable("playlist/{id}") { PlaylistScreen() }
                        composable("album/{id}") { AlbumScreen() }
                    }
                }
            }
        }
    }
}