package com.image.resizer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.image.resizer.ui.theme.PrimaryBlue
import com.image.resizer.ui.theme.TextDim
import com.image.resizer.ui.theme.TextWhite

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun FormatTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) PrimaryBlue else Color(0x1A000000))
            .border(1.dp, if (active) PrimaryBlue else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (active) Color.White else TextDim, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DarkTextField(
    value: String, 
    onValueChange: (String) -> Unit, 
    placeholder: String, 
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextDim.copy(0.5f)) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0x1A0F172A),
            unfocusedContainerColor = Color(0x1A0F172A),
            focusedIndicatorColor = PrimaryBlue,
            unfocusedIndicatorColor = Color(0xFF334155),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
fun UnitTab(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) PrimaryBlue else Color(0x1A000000))
            .border(1.dp, if (active) PrimaryBlue else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (active) Color.White else TextDim, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ModeTab(title: String, subtitle: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) PrimaryBlue else Color(0x1A000000))
            .border(1.dp, if (active) PrimaryBlue else Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = if (active) Color.White else TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(text = "($subtitle)", color = if (active) Color.White.copy(0.7f) else TextDim.copy(0.7f), fontSize = 10.sp)
    }
}

@Composable
fun StatusSection(message: String, isBlinking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alphaValue by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(0.2f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.alpha(if (isBlinking) alphaValue else 1f),
            textAlign = TextAlign.Center
        )
    }
}
