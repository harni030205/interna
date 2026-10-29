package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.UserEntity
import com.example.data.repository.PasswordResetChallenge
import com.example.domain.model.LanguageCatalog
import com.example.ui.components.FuturisticCard
import com.example.ui.components.LanguageSelectorChips
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess

@Composable
fun WelcomeOnboardingScreen(
    onGetStartedSignUp: () -> Unit,
    onExistingSignIn: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusPillBadge(
                label = "POWERED BY INTERNA AI",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.illustration_onboarding_globe),
                contentDescription = stringResource(R.string.cd_onboarding_art),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Interna",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Learn, practice, translate, pronounce, and understand world languages with candid AI conversation, verified lexical phonetics, and offline-ready study packs.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onGetStartedSignUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_get_started_button")
            ) {
                Text("Create Free Account")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onExistingSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_sign_in_button")
            ) {
                Text("Sign In to Existing Account")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignUpScreen(
    isBusy: Boolean,
    onRegister: (
        fullName: String,
        email: String,
        password: String,
        nativeLang: String,
        targetLang: String,
        level: String,
        goal: String
    ) -> Unit,
    onNavigateToSignIn: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var nativeLang by rememberSaveable { mutableStateOf("en") }
    var targetLang by rememberSaveable { mutableStateOf("fr") }
    var proficiency by rememberSaveable { mutableStateOf("Beginner") }
    var learningGoal by rememberSaveable { mutableStateOf(LanguageCatalog.learningObjectives.first()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Create Your Interna Account",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            FuturisticCard {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_name_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (min 8 chars, PBKDF2 hashed)") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input")
                )

                Spacer(Modifier.height(16.dp))
                Text("Your Native Language", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LanguageSelectorChips(selectedCode = nativeLang, onSelect = { nativeLang = it })

                Spacer(Modifier.height(16.dp))
                Text("Primary Target Language", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LanguageSelectorChips(selectedCode = targetLang, onSelect = { targetLang = it })

                Spacer(Modifier.height(16.dp))
                Text("Current Level", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageCatalog.proficiencyLevels.forEach { lvl ->
                        FilterChip(
                            selected = proficiency == lvl,
                            onClick = { proficiency = lvl },
                            label = { Text(lvl) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Learning Objective", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageCatalog.learningObjectives.forEach { obj ->
                        FilterChip(
                            selected = learningGoal == obj,
                            onClick = { learningGoal = obj },
                            label = { Text(obj) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        onRegister(fullName, email, password, nativeLang, targetLang, proficiency, learningGoal)
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_submit_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create Account")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onNavigateToSignIn,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Already have an account? Sign In")
            }
        }
    }
}

@Composable
fun SignInScreen(
    isBusy: Boolean,
    onSignIn: (email: String, password: String) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToPasswordRecovery: () -> Unit,
    onNavigateToDeveloperLogin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome Back to Interna",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "One World. Every Language.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))

            FuturisticCard {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signin_email_input")
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signin_password_input")
                )

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onNavigateToPasswordRecovery,
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("forgot_password_button")
                ) {
                    Text("Forgot Password?")
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSignIn(email, password) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signin_submit_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign In")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNavigateToSignUp) {
                Text("New to Interna? Create an Account")
            }
            TextButton(
                onClick = onNavigateToDeveloperLogin,
                modifier = Modifier.testTag("open_developer_login_button")
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Developer Login")
            }
        }
    }
}

@Composable
fun PasswordRecoveryScreen(
    challenge: PasswordResetChallenge?,
    onRequestCode: (email: String) -> Unit,
    onConfirmReset: (email: String, code: String, newPassword: String) -> Unit,
    onBackToSignIn: () -> Unit
) {
    BackHandler(onBack = onBackToSignIn)

    var email by rememberSaveable { mutableStateOf(challenge?.email.orEmpty()) }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.widthIn(max = 480.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToSignIn) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Sign In")
                }
                Text("Password Recovery", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(12.dp))

            FuturisticCard {
                Icon(
                    imageVector = Icons.Default.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Request a time-limited 6-digit recovery code to securely reset your password.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Account Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_email_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onRequestCode(email) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_send_code_button")
                ) {
                    Text("Generate Recovery Code")
                }

                if (challenge != null) {
                    Spacer(Modifier.height(16.dp))
                    StatusPillBadge(
                        label = "Active Code: ${challenge.resetCodeDispatchPreview} (15m expiry)",
                        color = AmberWarning
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("6-Digit Recovery Code") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_code_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (min 8 chars)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_new_password_input")
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onConfirmReset(email, code, newPassword) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_confirm_button")
                    ) {
                        Text("Confirm Password Reset")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperAuthProvisionScreen(
    currentUser: UserEntity?,
    isProvisionTokenConsumed: Boolean,
    isAlreadyVerifiedAdmin: Boolean,
    isBusy: Boolean,
    onDeveloperSignIn: (email: String, password: String) -> Unit,
    onBootstrapOrUpgradeAdmin: (email: String, password: String, fullName: String, provisionToken: String) -> Unit,
    onVerifyEmailCode: (String) -> Unit,
    onResendVerificationCode: () -> Unit,
    onProvisionSignedInAdmin: (String) -> Unit,
    onOpenDeveloperConsole: () -> Unit,
    onNavigateToPasswordRecovery: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedTab by rememberSaveable { mutableStateOf("SIGN_IN") }
    var devEmail by rememberSaveable { mutableStateOf("") }
    var devPassword by rememberSaveable { mutableStateOf("") }
    var devFullName by rememberSaveable { mutableStateOf("Interna Administrator") }
    var showDevPassword by rememberSaveable { mutableStateOf(false) }
    var emailVerifyCode by rememberSaveable { mutableStateOf("") }
    var provisionToken by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .widthIn(max = 620.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Developer Login & Console Access", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))

        if (isAlreadyVerifiedAdmin) {
            FuturisticCard(accentBorder = true) {
                StatusPillBadge(label = "VERIFIED ADMINISTRATOR", color = EmeraldSuccess)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Signed in as ${currentUser?.email}. Your administrator role is cryptographically verified.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenDeveloperConsole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_verified_console_button")
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Private Developer Console")
                }
            }
            return@Column
        }

        if (currentUser == null) {
            FuturisticCard(accentBorder = true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTab == "SIGN_IN",
                        onClick = { selectedTab = "SIGN_IN" },
                        label = { Text("Developer Sign In") },
                        modifier = Modifier.testTag("dev_mode_signin_chip")
                    )
                    if (!isProvisionTokenConsumed) {
                        FilterChip(
                            selected = selectedTab == "PROVISION",
                            onClick = { selectedTab = "PROVISION" },
                            label = { Text("Initial One-Time Setup") },
                            modifier = Modifier.testTag("dev_mode_provision_chip")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (selectedTab == "SIGN_IN" || isProvisionTokenConsumed) {
                    Text(
                        text = "Sign in with your provisioned administrator email and password to open the private Developer Console.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = devEmail,
                        onValueChange = { devEmail = it },
                        label = { Text("Developer Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dev_login_email_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = devPassword,
                        onValueChange = { devPassword = it },
                        label = { Text("Developer Password") },
                        singleLine = true,
                        visualTransformation = if (showDevPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showDevPassword = !showDevPassword }) {
                                Icon(
                                    imageVector = if (showDevPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dev_login_password_input")
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onNavigateToPasswordRecovery,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Reset Administrator Password")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onDeveloperSignIn(devEmail, devPassword) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("dev_login_submit_button")
                    ) {
                        Text("Sign In to Developer Console")
                    }
                } else {
                    Text(
                        text = "One-Time Initial Administrator Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Creates your administrator account directly (or upgrades your existing account without creating a duplicate) using the one-time ADMIN_PROVISION_TOKEN. Reuse is permanently blocked after provisioning succeeds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = devEmail,
                        onValueChange = { devEmail = it },
                        label = { Text("Authorized Developer Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dev_bootstrap_email_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = devFullName,
                        onValueChange = { devFullName = it },
                        label = { Text("Administrator Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = devPassword,
                        onValueChange = { devPassword = it },
                        label = { Text("Password (min 8 chars, or current password if account exists)") },
                        singleLine = true,
                        visualTransformation = if (showDevPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showDevPassword = !showDevPassword }) {
                                Icon(
                                    imageVector = if (showDevPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dev_bootstrap_password_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = provisionToken,
                        onValueChange = { provisionToken = it },
                        label = { Text("One-Time ADMIN_PROVISION_TOKEN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_provision_token_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onBootstrapOrUpgradeAdmin(devEmail, devPassword, devFullName, provisionToken)
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_provision_submit_button")
                    ) {
                        Text("Provision Administrator & Sign In")
                    }
                }
            }
        } else {
            FuturisticCard(accentBorder = true) {
                Text(
                    text = "Upgrade Signed-In Account to Administrator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                if (isProvisionTokenConsumed) {
                    StatusPillBadge(label = "ONE-TIME PROVISIONING LOCKED", color = AmberWarning)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Initial administrator provisioning has already been completed and cannot be reused.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (!currentUser.emailVerified) {
                        Text("Step 1: Verify Your Email", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = emailVerifyCode,
                            onValueChange = { emailVerifyCode = it },
                            label = { Text("6-Digit Email Verification Code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onVerifyEmailCode(emailVerifyCode) }) {
                                Text("Verify Email")
                            }
                            TextButton(onClick = onResendVerificationCode) {
                                Text("Generate New Code")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("Enter One-Time Provisioning Token", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = provisionToken,
                        onValueChange = { provisionToken = it },
                        label = { Text("ADMIN_PROVISION_TOKEN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_provision_token_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onProvisionSignedInAdmin(provisionToken) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_provision_submit_button")
                    ) {
                        Text("Verify & Provision Administrator Role")
                    }
                }
            }
        }
    }
}
