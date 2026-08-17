package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState
import com.hienthai.fastowin.state.MAX_ACCOUNT_PASSWORD_LENGTH
import com.hienthai.fastowin.state.accountPasswordConfirmationError
import com.hienthai.fastowin.state.accountPasswordError
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@Composable
fun AuthScreen(
    state: AuthState,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenPasswordReset: () -> Unit,
    onPlayAsGuest: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onUpgradeGuest: (String, String) -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onConfirmPasswordReset: (String, String, String) -> Unit,
    onBack: () -> Unit,
    onCancelUpgrade: () -> Unit
) {
    ResponsiveScreen(maxContentWidth = 520.dp, avoidKeyboard = true) { contentModifier ->
        Box(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state.stage) {
                AuthStage.WELCOME -> WelcomeContent(state, onOpenLogin, onOpenRegister, onPlayAsGuest)
                AuthStage.LOGIN -> LoginContent(state, onLogin, onOpenPasswordReset, onBack)
                AuthStage.REGISTER -> RegisterContent(state, onRegister, onBack)
                AuthStage.RESET_PASSWORD -> PasswordResetContent(
                    state,
                    onRequestPasswordReset,
                    onConfirmPasswordReset,
                    onOpenPasswordReset,
                    onBack
                )
                AuthStage.UPGRADE_GUEST -> UpgradeGuestContent(state, onUpgradeGuest, onCancelUpgrade)
                AuthStage.PLAYING -> Unit
            }
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
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun LoginContent(
    state: AuthState,
    onLogin: (String, String) -> Unit,
    onOpenPasswordReset: () -> Unit,
    onBack: () -> Unit
) {
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
        TextButton(onClick = onOpenPasswordReset, enabled = !state.isLoading) {
            Text("Quên mật khẩu?")
        }
    }
}

@Composable
private fun PasswordResetContent(
    state: AuthState,
    onRequest: (String) -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onUseDifferentEmail: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val requestedEmail = state.passwordResetEmail
    val passwordError = accountPasswordError(newPassword)
    val confirmationError = accountPasswordConfirmationError(newPassword, confirmPassword)
    LaunchedEffect(state.devResetToken) {
        state.devResetToken?.let { resetToken = it }
    }
    AuthForm(title = "Khôi phục mật khẩu", state = state, onBack = onBack) {
        Text(
            "Nhập email để nhận mã khôi phục có hiệu lực trong 15 phút.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        EmailField(email, enabled = requestedEmail == null && !state.isLoading) { email = it }
        if (requestedEmail == null) {
            Button(
                onClick = { onRequest(email) },
                enabled = email.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Tạo mã khôi phục")
            }
        } else {
            state.devResetToken?.let { token ->
                Text(
                    "Môi trường dev – mã đã được tự điền:\n$token",
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            OutlinedTextField(
                value = resetToken,
                onValueChange = { resetToken = it.trim() },
                label = { Text("Mã khôi phục") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            PasswordField(newPassword, "Mật khẩu mới (ít nhất 8 ký tự)") { newPassword = it }
            PasswordField(confirmPassword, "Nhập lại mật khẩu mới") { confirmPassword = it }
            if (newPassword.isNotEmpty() && passwordError != null) {
                Text(passwordError, color = MaterialTheme.colorScheme.error)
            }
            confirmationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onConfirm(requestedEmail, resetToken, newPassword) },
                enabled = resetToken.isNotBlank() && passwordError == null &&
                    confirmationError == null && confirmPassword.isNotEmpty() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Đặt lại mật khẩu")
            }
            TextButton(onClick = onUseDifferentEmail, enabled = !state.isLoading) {
                Text("Dùng email khác")
            }
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
    val passwordError = accountPasswordError(password)
    val confirmationError = accountPasswordConfirmationError(password, confirmPassword)
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
        if (password.isNotEmpty() && passwordError != null) Text(passwordError, color = MaterialTheme.colorScheme.error)
        confirmationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onRegister(email, password, displayName) },
            enabled = displayName.isNotBlank() && email.isNotBlank() && passwordError == null &&
                confirmationError == null && confirmPassword.isNotEmpty() && !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Tạo tài khoản")
        }
    }
}

@Composable
private fun UpgradeGuestContent(
    state: AuthState,
    onUpgradeGuest: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordError = accountPasswordError(password)
    val confirmationError = accountPasswordConfirmationError(password, confirmPassword)
    AuthForm(title = "Lưu tài khoản khách", state = state, onBack = onBack) {
        Text(
            "Elo, lịch sử, thống kê và thành tích hiện tại sẽ được giữ nguyên.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        EmailField(email) { email = it }
        PasswordField(password, "Mật khẩu (ít nhất 8 ký tự)") { password = it }
        PasswordField(confirmPassword, "Nhập lại mật khẩu") { confirmPassword = it }
        if (password.isNotEmpty() && passwordError != null) Text(passwordError, color = MaterialTheme.colorScheme.error)
        confirmationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onUpgradeGuest(email, password) },
            enabled = email.isNotBlank() && passwordError == null && confirmationError == null &&
                confirmPassword.isNotEmpty() && !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Lưu và tạo tài khoản")
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
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        TextButton(onClick = onBack, enabled = !state.isLoading) { Text("Quay lại") }
    }
}

@Composable
private fun EmailField(
    value: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= MAX_ACCOUNT_PASSWORD_LENGTH) onValueChange(it) },
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
