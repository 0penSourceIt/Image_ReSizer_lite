package com.image.resizer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.image.resizer.ui.theme.DarkBg
import com.image.resizer.ui.theme.BlueGradient

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var feedbackSubject by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "About & Feedback",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(BlueGradient)
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Image ReSizer",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Version 1.0.0",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "A high-precision image processing utility designed for efficiency and privacy.",
                        textAlign = TextAlign.Center,
                        color = Color.LightGray,
                        fontSize = 15.sp
                    )
                }
            }

            // --- Feedback Form Section ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0DFFFFFF), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SEND FEEDBACK (Under Maintainance)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3B82F6),
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = feedbackSubject,
                    onValueChange = { feedbackSubject = it },
                    label = { Text("Subject (e.g. Bug, Suggestion)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF3B82F6)
                    )
                )

                OutlinedTextField(
                    value = feedbackMessage,
                    onValueChange = { feedbackMessage = it },
                    label = { Text("Your Message") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF3B82F6)
                    )
                )

                Button(
                    onClick = {
                        if (feedbackMessage.isNotBlank()) {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:feedback.opensourceit@proton.me")
                                putExtra(Intent.EXTRA_SUBJECT, feedbackSubject.ifBlank { "Image ReSizer Feedback" })
                                putExtra(Intent.EXTRA_TEXT, feedbackMessage)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send Feedback via Email"))
                            } catch (e: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = feedbackMessage.isNotBlank()
                ) {
                    Text("Submit Feedback", fontWeight = FontWeight.Bold)
                }
            }

            // Developer Section
            SectionHeader("Developer")
            Text(
                text = "0penSourceIt",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3B82F6),
                textAlign = TextAlign.Center,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/0penSourceIt"))
                    context.startActivity(intent)
                }
            )

            // Mission Section
            SectionHeader("Mission")
            Text(
                text = "To provide powerful, professional-grade digital tools freely to the community, ensuring high performance without compromising user data privacy.",
                textAlign = TextAlign.Center,
                color = Color.LightGray,
                fontSize = 14.sp
            )

            // Acknowledgments
            SectionHeader("Powered By")
            Text(
                text = "Advanced AI (Gemini Pro & GPT 5.2)\nJetpack Compose & Kotlin",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Made with ❤️ by 0penSourceIt",
                color = Color.DarkGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
