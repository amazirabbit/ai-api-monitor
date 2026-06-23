package com.yzarc.aiapimonitor.ui.account

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(existingAccount: ApiAccount? = null, onSave: (ApiAccount) -> Unit, onBack: () -> Unit) {
    var platform by remember { mutableStateOf(existingAccount?.platform ?: "openai") }
    var name by remember { mutableStateOf(existingAccount?.name ?: "") }
    var apiKey by remember { mutableStateOf(existingAccount?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text(if (existingAccount != null) "编辑账号" else "添加账号") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onSurface)) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = ApiAccount.platformNames[platform] ?: platform, onValueChange = {}, readOnly = true, label = { Text("平台", color = MaterialTheme.colorScheme.outline) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { ApiAccount.platforms.forEach { p -> DropdownMenuItem(text = { Text(ApiAccount.platformNames[p] ?: p, color = MaterialTheme.colorScheme.onSurface) }, onClick = { platform = p; expanded = false }) } }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("备注", color = MaterialTheme.colorScheme.outline) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key", color = MaterialTheme.colorScheme.outline) }, modifier = Modifier.fillMaxWidth(), visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "显示/隐藏", tint = MaterialTheme.colorScheme.outline) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (name.isBlank()) { Toast.makeText(context, "请输入备注", Toast.LENGTH_SHORT).show(); return@Button }; if (apiKey.isBlank()) { Toast.makeText(context, "请输入 API Key", Toast.LENGTH_SHORT).show(); return@Button }; val saved = if (existingAccount != null) existingAccount.copy(platform = platform, name = name, apiKey = apiKey) else ApiAccount(platform = platform, name = name, apiKey = apiKey); onSave(saved) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(if (existingAccount != null) "保存" else "添加", fontWeight = FontWeight.W600) }
        }
    }
}
