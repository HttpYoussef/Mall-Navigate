package com.example.mallar.ui.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider

@Composable
fun OtpVerifyScreen(
    verificationId: String,
    onBackClick: () -> Unit,
    onSuccess: () -> Unit
) {

    var otpValue by remember { mutableStateOf("") }

    val context = LocalContext.current

    val auth = FirebaseAuth.getInstance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Teal)
    ) {

        // Background Image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Back Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp),

                contentAlignment = Alignment.TopStart
            ) {

                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = White
                ) {

                    Box(contentAlignment = Alignment.Center) {

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Teal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logo
            Image(
                painter = painterResource(id = R.drawable.logo_main),
                contentDescription = "logo",

                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(140.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Verify Code",
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We sent a 6-digit code to your phone.",
                color = White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,

                modifier = Modifier.padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // OTP Boxes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),

                horizontalArrangement = Arrangement.spacedBy(
                    8.dp,
                    Alignment.CenterHorizontally
                )
            ) {

                repeat(6) { index ->

                    val char = otpValue.getOrNull(index)

                    Surface(
                        modifier = Modifier.size(
                            width = 48.dp,
                            height = 64.dp
                        ),

                        shape = RoundedCornerShape(12.dp),

                        color = White
                    ) {

                        Box(contentAlignment = Alignment.Center) {

                            Text(
                                text = char?.toString() ?: "",
                                color = Teal,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Verify Button
            Button(

                onClick = {

                    val credential =
                        PhoneAuthProvider.getCredential(
                            verificationId,
                            otpValue
                        )

                    auth.signInWithCredential(credential)
                        .addOnCompleteListener {

                            if (it.isSuccessful) {

                                Toast.makeText(
                                    context,
                                    "Login Success",
                                    Toast.LENGTH_SHORT
                                ).show()

                                onSuccess()

                            } else {

                                Toast.makeText(
                                    context,
                                    "Wrong OTP",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                },

                enabled = otpValue.length == 6,

                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(56.dp)
                    .border(
                        1.5.dp,
                        White,
                        RoundedCornerShape(16.dp)
                    ),

                shape = RoundedCornerShape(16.dp),

                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = White.copy(alpha = 0.3f)
                )
            ) {

                Text(
                    text = "Verify",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                NumericKeypad(

                    onKeyPress = { key ->

                        if (otpValue.length < 6) {
                            otpValue += key
                        }
                    },

                    onBackspace = {

                        if (otpValue.isNotEmpty()) {
                            otpValue = otpValue.dropLast(1)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Haven't received it? Resend",
                    color = White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,

                    modifier = Modifier
                        .clickable { }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit
) {

    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        keys.forEach { row ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                row.forEach { key ->

                    if (key.isEmpty()) {

                        Spacer(modifier = Modifier.weight(1f))

                    } else {

                        KeyButton(
                            text = key,
                            modifier = Modifier.weight(1f),

                            onClick = {

                                if (key == "⌫")
                                    onBackspace()
                                else
                                    onKeyPress(key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),

        shape = RoundedCornerShape(16.dp),

        color = White.copy(alpha = 0.15f),

        shadowElevation = 0.dp
    ) {

        Box(contentAlignment = Alignment.Center) {

            if (text == "⌫") {

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = White,
                    modifier = Modifier.size(26.dp)
                )

            } else {

                Text(
                    text = text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = White
                )
            }
        }
    }
}