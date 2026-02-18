package com.omerhedvat.powerme.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.ui.theme.NavySurface
import com.omerhedvat.powerme.ui.theme.NeonBlue
import com.omerhedvat.powerme.ui.theme.SlateGrey

@Composable
fun ProfileSetupScreen(
    onProfileSaved: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var occupationType by remember { mutableStateOf("SEDENTARY") }
    var parentalLoad by remember { mutableStateOf("0") }
    var chronotype by remember { mutableStateOf("NEUTRAL") }
    var averageSleepHours by remember { mutableStateOf("7") }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onProfileSaved()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = NavySurface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Profile Setup",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = NeonBlue
            )
            Text(
                text = "All fields are optional — skip what you don't know",
                fontSize = 13.sp,
                color = NeonBlue.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            ProfileTextField(value = name, onValueChange = { name = it }, label = "Name")
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(value = age, onValueChange = { age = it }, label = "Age")
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(value = heightCm, onValueChange = { heightCm = it }, label = "Height (cm)")
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(value = averageSleepHours, onValueChange = { averageSleepHours = it }, label = "Avg Sleep Hours")
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(value = parentalLoad, onValueChange = { parentalLoad = it }, label = "Number of Children")

            Spacer(modifier = Modifier.height(20.dp))

            Text("Occupation Type", fontSize = 14.sp, color = NeonBlue, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmented(
                options = listOf("SEDENTARY", "ACTIVE", "PHYSICAL"),
                selected = occupationType,
                onSelect = { occupationType = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Chronotype", fontSize = 14.sp, color = NeonBlue, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmented(
                options = listOf("MORNING", "NEUTRAL", "NIGHT"),
                selected = chronotype,
                onSelect = { chronotype = it }
            )

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.saveProfile(
                        name = name.trim().takeIf { it.isNotBlank() },
                        age = age.toIntOrNull(),
                        heightCm = heightCm.toFloatOrNull(),
                        occupationType = occupationType.takeIf { it.isNotBlank() },
                        parentalLoad = parentalLoad.toIntOrNull(),
                        chronotype = chronotype.takeIf { it.isNotBlank() },
                        averageSleepHours = averageSleepHours.toFloatOrNull()
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = NavySurface)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavySurface, strokeWidth = 2.dp)
                } else {
                    Text("Enter the War Room", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = NeonBlue.copy(alpha = 0.7f)) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonBlue,
            unfocusedBorderColor = NeonBlue.copy(alpha = 0.5f),
            focusedTextColor = androidx.compose.ui.graphics.Color.White,
            unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
            cursorColor = NeonBlue
        )
    )
}

@Composable
private fun SingleChoiceSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonBlue,
                    selectedLabelColor = NavySurface,
                    containerColor = SlateGrey,
                    labelColor = NeonBlue.copy(alpha = 0.8f)
                )
            )
        }
    }
}
