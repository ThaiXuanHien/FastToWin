package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState

@Composable
fun AuthScreen(
    state: AuthState,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onPlayAsGuest: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state.stage) {
            AuthStage.WELCOME -> WelcomeContent(state, onOpenLogin, onOpenRegister, onPlayAsGuest)
            AuthStage.LOGIN -> LoginContent(state, onLogin, onBack)
            AuthStage.REGISTER -> RegisterContent(state, onRegister, onBack)
            AuthStage.PLAYING -> Unit
        }
    }
}

@Composable
private fun WelcomeContent(
    state: AuthState,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onPlayAsGuest: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Fast To Win",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Đăng nhập để đồng bộ Elo, lịch sử và thành tích trên mọi thiết bị.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Đăng nhập")
        }
        OutlinedButton(onClick = onOpenRegister, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Tạo tài khoản")
        }
        TextButton(onClick = onPlayAsGuest) { Text("Chơi với tư cách khách") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun LoginContent(state: AuthState, onLogin: (String, String) -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthForm(title = "Đăng nhập", state = state, onBack = onBack) {
        EmailField(email) { email = it }
        PasswordField(password, "Mật khẩu") { password = it }
        Button(
            onClick = { onLogin(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank() && !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Đăng nhập")
        }
    }
}

@Composable
private fun RegisterContent(
    state: AuthState,
    onRegister: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordsMatch = password == confirmPassword
    AuthForm(title = "Tạo tài khoản", state = state, onBack = onBack) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Biệt danh") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        EmailField(email) { email = it }
        PasswordField(password, "Mật khẩu (ít nhất 8 ký tự)") { password = it }
        PasswordField(confirmPassword, "Nhập lại mật khẩu") { confirmPassword = it }
        if (confirmPassword.isNotEmpty() && !passwordsMatch) {
            Text("Mật khẩu nhập lại chưa khớp.", color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { onRegister(email, password, displayName) },
            enabled = displayName.isNotBlank() && email.isNotBlank() && password.length >= 8 &&
                passwordsMatch && !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Tạo tài khoản")
        }
    }
}

@Composable
private fun AuthForm(
    title: String,
    state: AuthState,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        content()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        TextButton(onClick = onBack, enabled = !state.isLoading) { Text("Quay lại") }
    }
}

@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
