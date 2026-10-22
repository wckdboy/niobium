package io.sovware.niobium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(state: NbState, onConnect: (String, String, String) -> Unit, onDemo: () -> Unit, onLocal: () -> Unit) {
    var gateway by remember { mutableStateOf("http://127.0.0.1:9119") }
    var token by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("My Hermes") }
    var showToken by remember { mutableStateOf(false) }

    NbBackground(intensity = 1.25f) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AtomicMark(42.dp, animated = true)
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(Nb.FillSoft).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Text("Nb · 41", color = Nb.Tertiary, style = NbMono, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(38.dp))
            Text("HERMES, PIXEL-SIZED", color = Nb.Ion, style = NbLabel, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            Text("Your pixels.\nIn your pocket.", color = Nb.Text, style = NbDisplay, fontSize = 46.sp, lineHeight = 47.sp, letterSpacing = (-2).sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Connect to Hermes, or give your Pixels a home right on this device.",
                color = Nb.Secondary, fontSize = 15.sp, lineHeight = 22.sp
            )
            Spacer(Modifier.height(28.dp))

            Column(Modifier.fillMaxWidth().pixelPanel(20.dp, accented = true).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(PixelGlyph.TERMINAL)
                    Spacer(Modifier.width(10.dp))
                    Text("No server? No problem.", color = Nb.Text, style = NbTitle, fontSize = 17.sp)
                }
                Text("Alpine Linux + DSH, on your Android. Bring your provider API keys and start a local Pixel.",
                    color = Nb.Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                PixelButton("Run Pixels locally", PixelGlyph.PLAY, Modifier.fillMaxWidth(), onClick = onLocal)
            }
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Nb.Ink.copy(alpha = 0.78f))
                    .border(1.dp, Nb.LineStrong, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connect", color = Nb.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    LiveDot(if (state.connecting) Nb.Amber else Nb.Tertiary, pulse = state.connecting)
                    Spacer(Modifier.width(5.dp))
                    Text(if (state.connecting) "connecting" else "gateway", color = Nb.Tertiary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(16.dp))
                FieldLabel("GATEWAY URL")
                OutlinedTextField(
                    value = gateway, onValueChange = { gateway = it },
                    placeholder = { Text("http://192.168.1.20:9119", color = Nb.Tertiary, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Nb.Text, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(11.dp),
                    colors = nbFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel("ACCESS TOKEN")
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    placeholder = { Text("Hermes session token", color = Nb.Tertiary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Nb.Text, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(if (showToken) "HIDE" else "SHOW", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp,
                            modifier = Modifier.padding(end = 10.dp).clickable { showToken = !showToken })
                    },
                    shape = RoundedCornerShape(11.dp),
                    colors = nbFieldColors()
                )
                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Nb.Rose, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Nb.Rose.copy(alpha = 0.08f)).padding(11.dp))
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onConnect(gateway, token, name) },
                    enabled = !state.connecting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Nb.Ion, contentColor = Color.White)
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text("Connecting…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Continue  →", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(Nb.Line))
                    Text("OR", color = Nb.Tertiary, style = NbMono, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Nb.Line))
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(Nb.Fill)
                        .border(1.dp, Nb.Line, CircleShape).clickable { onDemo() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("or explore the demo", color = Nb.Secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Token stays in the Android Keystore. Niobium has no account, no cloud, no analytics.",
                color = Nb.Tertiary, fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Nb.Tertiary, style = NbMono, fontSize = 8.5.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp))
}


@Composable
private fun nbFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Nb.Ion.copy(alpha = 0.45f),
    unfocusedBorderColor = Nb.LineStrong,
    focusedContainerColor = Nb.FillSoft,
    unfocusedContainerColor = Nb.FillSoft,
    cursorColor = Nb.Ion
)
