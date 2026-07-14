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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.ui.theme.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

private val SignInGradient = listOf(
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

/**
 * Unified Sign-In screen with inline OTP verification.
 * Phase 1: Enter phone number → Phase 2: Enter OTP (no navigation, just animation).
 */
@Composable
fun SignInScreen(
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    onSkipClick: () -> Unit
) {
    var phase by remember { mutableIntStateOf(1) } // 1 = phone, 2 = OTP
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
            .background(Brush.verticalGradient(if (isDarkMode) DarkGradientColors else SignInGradient))
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
                        if (phase > 1) phase = 1 else onBackClick()
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

            // ── Phase indicator ──────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { idx ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (idx + 1 == phase) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .background(
                                if (idx < phase) White else White.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Content ──────────────────────────────────────────────────────
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "signin_phase"
            ) { currentPhase ->
                when (currentPhase) {
                    1 -> SignInPhonePhase(
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
                                return@SignInPhonePhase
                            }
                            isLoading = true
                            val fullPhone = "+20$formattedPhone"
                            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(
                                    credential: com.google.firebase.auth.PhoneAuthCredential
                                ) {
                                    isLoading = false
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
                                    phase = 2
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
                    2 -> SignInOtpPhase(
                        otpValue = otpValue,
                        isLoading = isLoading,
                        phoneDisplay = "+20 ${formatSignInPhone(phoneNumber)}",
                        onOtpChange = { if (it.length <= 6) otpValue = it },
                        onBackspace = {
                            if (otpValue.isNotEmpty())
                                otpValue = otpValue.dropLast(1)
                        },
                        onVerify = {
                            if (otpValue.length != 6) return@SignInOtpPhase
                            isLoading = true
                            val credential = PhoneAuthProvider.getCredential(verificationId, otpValue)
                            auth.signInWithCredential(credential)
                                .addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) {
                                        Toast.makeText(context, "Login Success", Toast.LENGTH_SHORT).show()
                                        onSuccess()
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

// ── Phone Entry Phase ────────────────────────────────────────────────────────
@Composable
private fun SignInPhonePhase(
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
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = null,
            tint = White.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.welcome_back),
            color = White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.enter_phone_signin),
            color = White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Phone Display
        AuthTextField(
            value = phoneNumber,
            onValueChange = onPhoneChange,
            placeholder = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.phone_placeholder),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
        )

        Spacer(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(24.dp))

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

// ── OTP Phase ────────────────────────────────────────────────────────────────
@Composable
private fun SignInOtpPhase(
    otpValue: String,
    isLoading: Boolean,
    phoneDisplay: String,
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
            text = androidx.compose.ui.res.stringResource(com.example.mallar.R.string.otp_subtitle),
            color = White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
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
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
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

private fun formatSignInPhone(number: String): String {
    val sb = StringBuilder()
    number.forEachIndexed { index, c ->
        sb.append(c)
        if (index == 1 || index == 4 || index == 7) sb.append(" ")
    }
    return sb.toString()
}
