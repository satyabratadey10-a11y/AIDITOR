package com.aiditor.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.MainActivity
import com.aiditor.app.R
import com.aiditor.app.ui.components.BwButton
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.CrashHandler

class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashLog = intent.getStringExtra("crash_log")
            ?: CrashHandler.lastCrashReport.ifBlank { "No crash log available." }

        setContent {
            AiditorTheme {
                CrashReportScreen(
                    crashLog = crashLog,
                    onCopyLog = {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AIDITOR Crash Log", crashLog)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Crash log copied to clipboard!", Toast.LENGTH_LONG).show()
                    },
                    onRestartApp = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onExit = {
                        finishAffinity()
                    }
                )
            }
        }
    }
}

@Composable
fun CrashReportScreen(
    crashLog: String,
    onCopyLog: () -> Unit,
    onRestartApp: () -> Unit,
    onExit: () -> Unit
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F11)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFFF3B30))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AIDITOR CRASH REPORT",
                        color = BwWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "V1.0.0",
                    color = BwGreyLight,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF261416))
                    .border(1.dp, Color(0xFF5C1C24), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "The application encountered an unexpected error. You can copy the complete diagnostics log below.",
                    color = Color(0xFFFFB3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Log Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070708))
                    .border(1.dp, Color(0xFF222226), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = crashLog,
                    color = Color(0xFFECECF0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Copy Button (Prominent white button)
                BwButton(
                    text = "COPY COMPLETE LOG",
                    iconRes = R.drawable.ic_duplicate,
                    onClick = onCopyLog,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BwGreyLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333338)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("CLOSE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onRestartApp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222228), contentColor = BwWhite),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("RESTART APP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
