package com.powerme.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.ui.theme.PowerMETheme
import com.powerme.app.data.ThemeMode

/**
 * Required by Health Connect SDK. Launched when user taps "More info"
 * in the system Health Connect permission dialog.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PowerMETheme(themeMode = ThemeMode.DARK) {
                PermissionsRationaleScreen(onDone = { finish() })
            }
        }
    }
}

@Composable
private fun PermissionsRationaleScreen(onDone: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Why PowerME uses Health Connect",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                "PowerME reads health data from Health Connect to personalize your training recommendations and track your recovery. Here is what we read and why:",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            PermissionItem("Weight & Body Fat", "Used to track body composition over time and correlate with your training progress.")
            PermissionItem("Height", "Used to calculate BMI and calibrate volume recommendations.")
            PermissionItem("Sleep", "Sleep duration is used to flag high-fatigue days and adjust recommended workout intensity.")
            PermissionItem("Heart Rate Variability (HRV)", "HRV drops indicate recovery debt. PowerME uses this to detect anomalous recovery patterns.")
            PermissionItem("Resting Heart Rate", "Elevated RHR can signal overtraining or illness. Used alongside HRV for recovery scoring.")
            PermissionItem("Steps", "Daily step count contributes to total activity load tracking.")

            Text(
                "PowerME never sells or shares your health data. All data remains on your device and your personal cloud backup.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text("Got it")
            }
        }
    }
}

@Composable
private fun PermissionItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}
