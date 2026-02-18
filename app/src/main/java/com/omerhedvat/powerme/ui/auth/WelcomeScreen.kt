package com.omerhedvat.powerme.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.ui.theme.DeepNavy
import com.omerhedvat.powerme.ui.theme.NavySurface
import com.omerhedvat.powerme.ui.theme.NeonBlue
import com.omerhedvat.powerme.ui.theme.SlateGrey

@Composable
fun WelcomeScreen(
    onSignedIn: () -> Unit,
    onNeedsProfile: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onSignedIn()
    }
    LaunchedEffect(uiState.needsProfileSetup) {
        if (uiState.needsProfileSetup) onNeedsProfile()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DeepNavy) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PowerME",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = NeonBlue
            )
            Text(
                text = "הוועדה — 8 יועצים מומחים",
                fontSize = 14.sp,
                color = NeonBlue.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (uiState.needsEmailVerification) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateGrey),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Check your email",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "A verification link has been sent to $email. Please verify your email then sign in.",
                            fontSize = 14.sp,
                            color = NeonBlue.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.signIn(email, password) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = NavySurface)
                        ) {
                            Text("I've verified — Sign In", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                return@Column
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", color = NeonBlue.copy(alpha = 0.7f)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonBlue.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonBlue
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = NeonBlue.copy(alpha = 0.7f)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonBlue.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonBlue
                ),
                singleLine = true
            )

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isSignUp) viewModel.signUp(email, password)
                    else viewModel.signIn(email, password)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = NavySurface)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavySurface, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (isSignUp) "Create Account" else "Sign In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { isSignUp = !isSignUp; viewModel.dismissError() }) {
                Text(
                    text = if (isSignUp) "Already have an account? Sign In"
                    else "New here? Create Account",
                    color = NeonBlue.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
