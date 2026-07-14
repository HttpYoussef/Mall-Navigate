package com.example.mallar.ui.auth

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

@Composable
fun PhoneAuthScreen(
    onBackClick: () -> Unit,
    onCodeSent: (String) -> Unit,
    onSkipClick: () -> Unit
) {

    var phoneNumber by remember { mutableStateOf("") }

    val context = LocalContext.current
    val activity = context as Activity

    val auth = FirebaseAuth.getInstance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Teal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                Text(
                    text = "Skip",
                    color = White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSkipClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Enter your\nPhone Number",
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You will receive a 6 digit code",
                color = White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Phone Input
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(64.dp),

                shape = RoundedCornerShape(16.dp),
                color = White
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "+20",
                            color = Teal,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Teal
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = if (phoneNumber.isEmpty())
                            "11 123 456 78"
                        else
                            formatPhoneNumberDisplay(phoneNumber),

                        color = if (phoneNumber.isEmpty())
                            Teal.copy(alpha = 0.3f)
                        else
                            Teal,

                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
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

                        if (phoneNumber.length < 10) {
                            phoneNumber += key
                        }
                    },

                    onBackspace = {

                        if (phoneNumber.isNotEmpty()) {
                            phoneNumber = phoneNumber.dropLast(1)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Send Button
                Button(

                    onClick = {

                        val fullPhone = "+20$phoneNumber"

                        val callbacks =
                            object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                                override fun onVerificationCompleted(
                                    credential: com.google.firebase.auth.PhoneAuthCredential
                                ) {

                                    Toast.makeText(
                                        context,
                                        "Verification Completed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                override fun onVerificationFailed(
                                    e: FirebaseException
                                ) {

                                    Toast.makeText(
                                        context,
                                        e.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                override fun onCodeSent(
                                    verificationId: String,
                                    token: PhoneAuthProvider.ForceResendingToken
                                ) {

                                    Toast.makeText(
                                        context,
                                        "Code Sent",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    onCodeSent(verificationId)
                                }
                            }

                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(fullPhone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(callbacks)
                            .build()

                        PhoneAuthProvider.verifyPhoneNumber(options)
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp)),

                    shape = RoundedCornerShape(16.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor = White,
                        contentColor = Teal
                    ),

                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp
                    )
                ) {

                    Text(
                        text = "Send",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
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
                    imageVector = Icons.Default.Backspace,
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

private fun formatPhoneNumberDisplay(number: String): String {

    val sb = StringBuilder()

    number.forEachIndexed { index, c ->

        sb.append(c)

        if (index == 1 || index == 4 || index == 7) {
            sb.append(" ")
        }
    }

    return sb.toString()
}