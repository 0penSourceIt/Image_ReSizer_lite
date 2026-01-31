package com.image.resizer.ui.screens

// --- IMPORTS: Zaruri libraries aur components ---
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.image.resizer.R
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun HomeScreen(
    onNavigateToCompress: () -> Unit, // Compress screen par jane ka function
    onNavigateToConvert: () -> Unit,  // Convert screen par jane ka function
    onNavigateToAbout: () -> Unit     // About screen par jane ka function
) {
    // LocalContext: Intents aur links open karne ke liye zaruri hai
    val context = LocalContext.current
    // LocalConfiguration: Screen size (Mobile/Tablet) check karne ke liye
    val configuration = LocalConfiguration.current
    
    // Width aur Tablet check logic
    val screenWidth = configuration.screenWidthDp
    val isTablet = screenWidth > 600
    
    // --- RESPONSIVE SCALING: Screen ke mutabiq dimensions set ho rahe hain ---
    val cardWidth = (screenWidth * 0.30f).coerceIn(320f, 500f).dp // Beech wale card ki choudai
    val buttonSize = (screenWidth * 0.50f).coerceIn(100f, 250f).dp // Icons ka size
    val titleFontSize = (screenWidth * 0.1f).coerceIn(28f, 48f).sp // Main title ka font size
    val verticalGap = if (isTablet) 60.dp else 50.dp // Display Screen ka Top aur bottom se kitna margin chhodna hain

    // Root Container: Poori screen ka background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Dark Gradient Background: Aesthetic look ke liye
                Brush.radialGradient(
                    colors = listOf(Color(0xFF000000), Color(0xFF000000)),
                    center = Offset.Infinite
                )
            )
            .statusBarsPadding() // Content ko status bar ke niche rakhta hai
    ) {
        // --- 1. TOP SECTION: TITLE (FIXED) ---
        Text(
            text = "IMAGE RESIZER",
            fontSize = titleFontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            textAlign = TextAlign.Center,
            color = Color(0xFF3B82F6), // Blue colored header
            modifier = Modifier
                .align(Alignment.TopCenter) // Title ko screen ke upar fix rakho
                .padding(top = verticalGap)
        )

        // --- 2. MIDDLE SECTION: ACTION CARD (CENTRIC) ---
        Box(
            modifier = Modifier
                .align(Alignment.Center) // Is card ko hamesha screen ke beech mein rakho
                .width(cardWidth)
                .wrapContentHeight()
                .shadow(62.dp, RoundedCornerShape(52.dp)) // Shadow aur corner curve
                .clip(RoundedCornerShape(52.dp)) // Squircle Buttons ka Background ka curve
                .background(Color(0xFF999999)) // Card ka base color
                .padding(vertical = if (isTablet) 48.dp else 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isTablet) 52.dp else 30.dp) // Buttons ke beech ka distance
            ) {
                // Image Button call: Compress
                HomeIconButton(
                    resId = R.drawable.btn_compress, 
                    contentDescription = "Compress Images", 
                    size = buttonSize,
                    onClick = onNavigateToCompress
                )
                
                // Image Button call: Convert
                HomeIconButton(
                    resId = R.drawable.btn_convert, 
                    contentDescription = "Convert Formats", 
                    size = buttonSize,
                    onClick = onNavigateToConvert
                )
            }
        }

        // --- 3. BOTTOM SECTION: FOOTER & BRANDING (FIXED) ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter) // Credits ko screen ke niche fix rakho
                .padding(bottom = verticalGap)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Attribution Text
                Text(
                    text = "Made with ❤️ by 0penSourceIt",
                    fontSize = if (isTablet) 16.sp else 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                // About Screen Link
                Text(
                    text = "About",
                    fontSize = if (isTablet) 16.sp else 14.sp,
                    color = Color(0xFF3B82F6),
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onNavigateToAbout() }
                )
            }

            // Support/Donation Button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/opensourceit"))
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFfaa800)),
                shape = RoundedCornerShape(16.dp), // Styled squircle button
                modifier = Modifier.height(if (isTablet) 52.dp else 44.dp)
            ) {
                Text(
                    text = "🥛 Buy me a Water",
                    color = Color(0xFF20124d),
                    fontSize = if (isTablet) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// COMPONENT: Image Button reusable design
@Composable
fun HomeIconButton(resId: Int, contentDescription: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    // shape: Professional curve size (approx 20-25% of button size)
    val shape = RoundedCornerShape(42.dp) 
    // scale: Click effect ke liye state manage ho rahi hai
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(size) // Dynamic height and width
            .graphicsLayer {
                // Yahan se button ke chota-bada hone (animation) ka control hota hai
                scaleX = scale.value
                scaleY = scale.value
            }
            .shadow(elevation = 12.dp, shape = shape, clip = false) // Shadow for depth
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape) // Edge detail
            .clip(shape) // Button ko squircle mein kaatna
            .background(Color.Black) // Solid color piche taaki transparency na dikhe
            .pointerInput(Unit) {
                // Press logic for industrial tactile feel
                detectTapGestures(
                    onPress = {
                        scope.launch { scale.animateTo(0.94f) } // Dabne par chota
                        tryAwaitRelease() // Ungli uthane ka wait
                        scope.launch { scale.animateTo(1f) }   // Wapas normal
                        onClick() // Logic execute
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // IMAGE DISPLAY: Main icon ko yahan dikhaya jata hai
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
           // modifier = Modifier.matchParentSize(), // Box ko pura cover karegi
            contentScale = ContentScale.Crop // Icon center mein crop hokar settle hoga
        )
    }
}
