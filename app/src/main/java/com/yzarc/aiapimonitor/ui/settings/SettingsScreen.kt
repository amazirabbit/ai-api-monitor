package com.yzarc.aiapimonitor.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yzarc.aiapimonitor.BuildConfig
import com.yzarc.aiapimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(baseConsumption: Double, onSaveBaseConsumption: (Double) -> Unit, onBack: () -> Unit, onDeleteAll: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var baseInput by remember { mutableStateOf("%.2f".format(baseConsumption)) }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onSurface)) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("初始累计消费", fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("记录使用本 App 之前已花费的金额", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = baseInput, onValueChange = { baseInput = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), suffix = { Text("元", color = MaterialTheme.colorScheme.onSurfaceVariant) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { val v = baseInput.toDoubleOrNull() ?: return@Button; onSaveBaseConsumption(v) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("保存") }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("支持平台", fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    listOf("OpenAI", "DeepSeek", "OpenRouter", "Kimi").forEach { Row(modifier = Modifier.padding(vertical = 4.dp)) { Text("• ", color = MaterialTheme.colorScheme.primary); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("清除所有数据") }
            Text("AI API Monitor v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (showDialog) { AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("确认清除?", color = MaterialTheme.colorScheme.onSurface) }, text = { Text("所有账号和余额数据将被删除", color = MaterialTheme.colorScheme.onSurfaceVariant) }, confirmButton = { TextButton(onClick = { onDeleteAll(); showDialog = false }) { Text("确认清除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) } }, containerColor = MaterialTheme.colorScheme.surface) }
    }
}
