package com.chirihome.music.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(alConectar: () -> Unit, modifier: Modifier = Modifier, vm: LoginViewModel = viewModel()) {
    var url by remember { mutableStateOf("http://192.168.1.88:4533") }
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    val estadoActual = vm.estado
    LaunchedEffect(estadoActual) { if (estadoActual is EstadoLogin.Exito) alConectar() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ChiriMusic", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Servidor") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = clave,
            onValueChange = { clave = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { vm.conectar(url, usuario, clave) },
            enabled = vm.estado !is EstadoLogin.Cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.estado is EstadoLogin.Cargando) "Conectando..." else "Conectar")
        }
        Spacer(Modifier.height(16.dp))

        when (val e = vm.estado) {
            is EstadoLogin.Exito -> Text("Conectado: ${e.servidor}", color = MaterialTheme.colorScheme.primary)
            is EstadoLogin.Fallo -> Text(e.mensaje, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}