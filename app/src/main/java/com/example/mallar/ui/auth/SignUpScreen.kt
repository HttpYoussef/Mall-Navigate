package com.example.mallar.ui.auth

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.ui.theme.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import java.util.concurrent.TimeUnit

private val GradientColors = listOf(
    Color(0xFF0F6B6B),
    Color(0xFF1A8C8C),
    Color(0xFF2FA3B8),
    Color(0xFF1A8C8C)
)

private val DarkGradientColors = listOf(
    Color(0xFF051717),
    Color(0xFF0A2D2D),
    Color(0xFF0E3A42),
    Color(0xFF0A2D2D)
)

@Composable
fun SignUpScreen(
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    onSkipClick: () -> Unit
) {
    // Phase: 1 = name entry, 2 = phone entry, 3 = OTP verify
    var phase by remember { mutableIntStateOf(1) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpValue by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as Activity
    val auth = FirebaseAuth.getInstance()

    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(if (isDarkMode) DarkGradientColors else GradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        if (phase > 1) phase-- else onBackClick()
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = White.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.skip),
                    color = White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSkipClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Phase indicator dots ─────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { idx ->
                    val isActive = idx < phase
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (idx + 1 == phase) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .background(
                                if (isActive) White else White.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Animated Content Per Phase ───────────────────────────────────
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "phase_transition"
            ) { currentPhase ->
                when (currentPhase) {
                    1 -> NameEntryPhase(
                        firstName = firstName,
                        lastName = lastName,
                        onFirstNameChange = { firstName = it },
                        onLastNameChange = { lastName = it },
                        onContinue = { phase = 2 }
                    )
                    2 -> PhoneEntryPhase(
                        phoneNumber = phoneNumber,
                        isLoading = isLoading,
                        onPhoneChange = { if (it.length <= 11) phoneNumber = it },
                        onBackspace = {
                            if (phoneNumber.isNotEmpty())
                                phoneNumber = phoneNumber.dropLast(1)
                        },
                        onSend = {
                            val rawPhone = phoneNumber.trim()
                            val formattedPhone = if (rawPhone.startsWith("0")) rawPhone.drop(1) else rawPhone
                            if (formattedPhone.length != 10) {
                                Toast.makeText(context, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
                                return@PhoneEntryPhase
                            }
                            isLoading = true
                            val fullPhone = "+20$formattedPhone"
                            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(
                                    credential: com.google.firebase.auth.PhoneAuthCredential
                                ) {
                                    isLoading = false
                                    Toast.makeText(context, "Verification Completed", Toast.LENGTH_SHORT).show()
                                }
                                override fun onVerificationFailed(e: FirebaseException) {
                                    isLoading = false
                                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                }
                                override fun onCodeSent(
                                    id: String,
                                    token: PhoneAuthProvider.ForceResendingToken
                                ) {
                                    isLoading = false
                                    verificationId = id
                                    phase = 3
                                    Toast.makeText(context, "Code Sent", Toast.LENGTH_SHORT).show()
                                }
                            }
                            val options = PhoneAuthOptions.newBuilder(auth)
                                .setPhoneNumber(fullPhone)
                                .setTimeout(60L, TimeUnit.SECONDS)
                                .setActivity(activity)
                                .setCallbacks(callbacks)
                                .build()
                            PhoneAuthProvider.verifyPhoneNumber(options)
                        }
                    )
                    3 -> OtpPhase(
                        otpValue = otpValue,
                        isLoading = isLoading,
                        onOtpChange = { if (it.length <= 6) otpValue = it },
                        onBackspace = {
                            if (otpValue.isNotEmpty())
                                otpValue = otpValue.dropLast(1)
                        },
                        onVerify = {
                            if (otpValue.length != 6) return@OtpPhase
                            isLoading = true
                            val credential = PhoneAuthProvider.getCredential(verificationId, otpValue)
                            auth.signInWithCredential(credential)
                                .addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) {
                                        // Save display name
                                        val profileUpdates = userProfileChangeRequest {
                                            displayName = "$firstName $lastName".trim()
                                        }
                                        auth.currentUser?.updateProfile(profileUpdates)
                                            ?.addOnCompleteListener {
                                                Toast.makeText(context, "Welcome, $firstName!", Toast.LENGTH_SHORT).show()
                                                onSuccess()
                                            }
                                    } else {
                                        Toast.makeText(context, "Wrong OTP", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        },
                        onResend = { /* TODO: resend logic */ }
                    )
                }
            }
        }

        // ── Loading overlay ──────────────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = White, strokeWidth = 3.dp)
            }
        }
    }
}

// ── Phase 1: Name Entry ──────────────────────────────────────────────────────
@Composable
private fun NameEntryPhase(
    firstName: String,
    lastName: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = White.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.create_account),
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.enter_name),
            color = White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // First Name Field
        AuthTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            placeholder = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.first_name)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Last Name Field
        AuthTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            placeholder = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.last_name)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Continue Button
        Button(
            onClick = onContinue,
            enabled = firstName.isNotBlank() && lastName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = White,
                contentColor = Teal,
                disabledContainerColor = White.copy(alpha = 0.5f),
                disabledContentColor = Teal.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.continue_btn),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
    }
}

// ── Phase 2: Phone Entry ─────────────────────────────────────────────────────
@Composable
private fun PhoneEntryPhase(
    phoneNumber: String,
    isLoading: Boolean,
    onPhoneChange: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.enter_phone_nl),
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.receive_6_digit),
            color = White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Phone Display
        AuthTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            placeholder = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.phone_placeholder),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        )

        Spacer(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(24.dp))

        // Send Button
        Button(
            onClick = onSend,
            enabled = phoneNumber.length >= 10 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = White,
                contentColor = Teal,
                disabledContainerColor = White.copy(alpha = 0.5f),
                disabledContentColor = Teal.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Text(text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.send_code), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
    }
}

// ── Phase 3: OTP Verification ────────────────────────────────────────────────
@Composable
private fun OtpPhase(
    otpValue: String,
    isLoading: Boolean,
    onOtpChange: (String) -> Unit,
    onBackspace: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.verify_code),
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.we_sent_code),
            color = White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Spacer(modifier = Modifier.height(16.dp))

        AuthTextField(
            value = otpValue,
            onValueChange = onOtpChange,
            placeholder = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.enter_otp),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Verify Button
        Button(
            onClick = onVerify,
            enabled = otpValue.length == 6 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = White,
                contentColor = Teal,
                disabledContainerColor = White.copy(alpha = 0.5f),
                disabledContentColor = Teal.copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Text(text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.verify), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.did_not_receive),
            color = White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable { onResend() }
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
    }
}

// ── Reusable text field for auth screens ─────────────────────────────────────
@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(16.dp),
        color = White.copy(alpha = 0.15f)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = White.copy(alpha = 0.4f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

// ── Numpad for sign-up ───────────────────────────────────────────────────────


private fun formatPhoneDisplay(number: String): String {
    val sb = StringBuilder()
    number.forEachIndexed { index, c ->
        sb.append(c)
        if (index == 1 || index == 4 || index == 7) sb.append(" ")
    }
    return sb.toString()
}
