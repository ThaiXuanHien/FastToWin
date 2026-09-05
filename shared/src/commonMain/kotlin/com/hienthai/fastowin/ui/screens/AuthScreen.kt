package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hienthai.fastowin.protocol.DEFAULT_FEMALE_AVATAR_ID
import com.hienthai.fastowin.protocol.DEFAULT_MALE_AVATAR_ID
import com.hienthai.fastowin.protocol.PlayerGender
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeBrandLockup
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

@Composable
fun AuthScreen(
    state: AuthState,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenPasswordReset: () -> Unit,
    onPlayAsGuest: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, PlayerGender) -> Unit,
    onUpgradeGuest: (String, String) -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onConfirmPasswordReset: (String, String, String) -> Unit,
    onRequestEmailVerification: () -> Unit,
    onConfirmEmailVerification: (String) -> Unit,
    onBack: () -> Unit,
    onCancelUpgrade: () -> Unit,
    onCancelEmailVerification: () -> Unit
) {
    SystemBackHandler(
        enabled = state.stage != AuthStage.WELCOME && state.stage != AuthStage.PLAYING
    ) {
        if (!state.isLoading) {
            when (state.stage) {
                AuthStage.UPGRADE_GUEST -> onCancelUpgrade()
                AuthStage.VERIFY_EMAIL -> onCancelEmailVerification()
                else -> onBack()
            }
        }
    }
    ArcadeBackdrop(modifier = Modifier.fillMaxSize()) {
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
                    AuthStage.VERIFY_EMAIL -> EmailVerificationContent(
                        state = state,
                        onRequest = onRequestEmailVerification,
                        onConfirm = onConfirmEmailVerification,
                        onCancel = onCancelEmailVerification
                    )
                    AuthStage.UPGRADE_GUEST -> UpgradeGuestContent(state, onUpgradeGuest, onCancelUpgrade)
                    AuthStage.PLAYING -> Unit
                }
            }
        }
    }
}

@Composable
private fun EmailVerificationContent(
    state: AuthState,
    onRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    LaunchedEffect(state.devEmailVerificationCode) {
        state.devEmailVerificationCode?.let { code = it }
    }
    AuthForm(title = "Xác minh email", state = state, onBack = onCancel) {
        Text(
            "Nhập mã 6 số được gửi tới ${state.session?.email.orEmpty()}. Mã có hiệu lực trong 15 phút.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.devEmailVerificationCode?.let {
            Text(
                "Môi trường dev – mã đã được tự điền: $it",
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = { Text("Mã xác minh") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().testTag("auth_email_verification_code")
        )
        ArcadeActionButton(
            label = if (state.isLoading) "ĐANG XÁC MINH..." else "XÁC MINH",
            onClick = { onConfirm(code) },
            enabled = code.length == 6 && !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("auth_email_verification_confirm"),
            style = ArcadeActionStyle.GOLD,
            content = authLoadingContent(state.isLoading)
        )
        ArcadeActionButton(
            label = "GỬI LẠI MÃ",
            onClick = onRequest,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("auth_email_verification_resend"),
            style = ArcadeActionStyle.OUTLINE
        )
        TextButton(onClick = onCancel, enabled = !state.isLoading) {
            Text("Đăng xuất")
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
        ArcadeBrandLockup()
        Text(
            "Đăng nhập để đồng bộ Elo, lịch sử và thành tích trên mọi thiết bị.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        ArcadeActionButton(
            label = "ĐĂNG NHẬP",
            onClick = onOpenLogin,
            modifier = Modifier.fillMaxWidth().testTag("auth_open_login"),
            style = ArcadeActionStyle.GOLD
        )
        ArcadeActionButton(
            label = "TẠO TÀI KHOẢN",
            onClick = onOpenRegister,
            modifier = Modifier.fillMaxWidth().testTag("auth_open_register"),
            style = ArcadeActionStyle.OUTLINE
        )
        TextButton(onClick = onPlayAsGuest) { Text("Chơi với tư cách khách") }
        AuthFeedback(state)
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
        ArcadeActionButton(
            label = if (state.isLoading) "ĐANG ĐĂNG NHẬP..." else "ĐĂNG NHẬP",
            onClick = { onLogin(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank() && !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("auth_login_submit"),
            style = ArcadeActionStyle.GOLD,
            content = authLoadingContent(state.isLoading)
        )
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
            ArcadeActionButton(
                label = if (state.isLoading) "ĐANG GỬI..." else "GỬI MÃ KHÔI PHỤC",
                onClick = { onRequest(email) },
                enabled = email.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("auth_reset_request"),
                style = ArcadeActionStyle.GOLD,
                content = authLoadingContent(state.isLoading)
            )
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
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth().testTag("auth_reset_token")
            )
            PasswordField(newPassword, "Mật khẩu mới (ít nhất 8 ký tự)") { newPassword = it }
            PasswordField(confirmPassword, "Nhập lại mật khẩu mới") { confirmPassword = it }
            if (newPassword.isNotEmpty() && passwordError != null) {
                Text(passwordError, color = MaterialTheme.colorScheme.error)
            }
            confirmationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ArcadeActionButton(
                label = if (state.isLoading) "ĐANG CẬP NHẬT..." else "ĐẶT LẠI MẬT KHẨU",
                onClick = { onConfirm(requestedEmail, resetToken, newPassword) },
                enabled = resetToken.isNotBlank() && passwordError == null &&
                    confirmationError == null && confirmPassword.isNotEmpty() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("auth_reset_confirm"),
                style = ArcadeActionStyle.GOLD,
                content = authLoadingContent(state.isLoading)
            )
            TextButton(onClick = onUseDifferentEmail, enabled = !state.isLoading) {
                Text("Dùng email khác")
            }
        }
    }
}

@Composable
private fun RegisterContent(
    state: AuthState,
    onRegister: (String, String, String, PlayerGender) -> Unit,
    onBack: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(PlayerGender.MALE) }
    val passwordError = accountPasswordError(password)
    val confirmationError = accountPasswordConfirmationError(password, confirmPassword)
    AuthForm(title = "Tạo tài khoản", state = state, onBack = onBack) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Biệt danh") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().testTag("auth_display_name")
        )
        Text(
            "GIỚI TÍNH · ẢNH ĐẠI DIỆN MẶC ĐỊNH",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        ArcadeSegmentedControl(
            labels = listOf("Nam", "Nữ"),
            selectedIndex = if (gender == PlayerGender.MALE) 0 else 1,
            onSelected = { gender = if (it == 0) PlayerGender.MALE else PlayerGender.FEMALE },
            modifier = Modifier.fillMaxWidth().testTag("auth_gender"),
            itemTestTag = { if (it == 0) "auth_gender_male" else "auth_gender_female" }
        )
        PlayerAvatar(
            displayName = displayName.ifBlank { "Người chơi" },
            avatarId = if (gender == PlayerGender.MALE) DEFAULT_MALE_AVATAR_ID else DEFAULT_FEMALE_AVATAR_ID,
            size = 72.dp
        )
        EmailField(email) { email = it }
        PasswordField(password, "Mật khẩu (ít nhất 8 ký tự)") { password = it }
        PasswordField(confirmPassword, "Nhập lại mật khẩu") { confirmPassword = it }
        if (password.isNotEmpty() && passwordError != null) Text(passwordError, color = MaterialTheme.colorScheme.error)
        confirmationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ArcadeActionButton(
            label = if (state.isLoading) "ĐANG TẠO..." else "TẠO TÀI KHOẢN",
            onClick = { onRegister(email, password, displayName, gender) },
            enabled = displayName.isNotBlank() && email.isNotBlank() && passwordError == null &&
                confirmationError == null && confirmPassword.isNotEmpty() && !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("auth_register_submit"),
            style = ArcadeActionStyle.GOLD,
            content = authLoadingContent(state.isLoading)
        )
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
        ArcadeActionButton(
            label = if (state.isLoading) "ĐANG LƯU..." else "LƯU VÀ TẠO TÀI KHOẢN",
            onClick = { onUpgradeGuest(email, password) },
            enabled = email.isNotBlank() && passwordError == null && confirmationError == null &&
                confirmPassword.isNotEmpty() && !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag("auth_upgrade_submit"),
            style = ArcadeActionStyle.GOLD,
            content = authLoadingContent(state.isLoading)
        )
    }
}

@Composable
private fun AuthForm(
    title: String,
    state: AuthState,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ArcadePanel(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArcadeBrandLockup(compact = true)
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            content()
            AuthFeedback(state)
            ArcadeActionButton(
                label = "QUAY LẠI",
                onClick = onBack,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                style = ArcadeActionStyle.OUTLINE
            )
        }
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
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().testTag("auth_email")
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
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth().testTag("auth_password")
    )
}

@Composable
private fun AuthFeedback(state: AuthState) {
    state.notice?.let { message ->
        ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Mint600) {
            Text(
                message,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = ArcadePalette.Mint600,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
    state.error?.let { message ->
        ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Coral600) {
            Text(
                message,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = ArcadePalette.Coral600,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun authLoadingContent(isLoading: Boolean): (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? =
    if (isLoading) {
        {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = ArcadePalette.Gold400,
                strokeWidth = 2.dp
            )
        }
    } else {
        null
    }
