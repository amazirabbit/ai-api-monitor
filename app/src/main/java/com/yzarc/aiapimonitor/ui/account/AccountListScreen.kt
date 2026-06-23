package com.yzarc.aiapimonitor.ui.account

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(accounts: List<ApiAccount>, onAdd: () -> Unit, onEdit: (ApiAccount) -> Unit, onDelete: (ApiAccount) -> Unit, onCheckKey: (ApiAccount) -> Unit, onBack: () -> Unit) {
    var deleteTarget by remember { mutableStateOf<ApiAccount?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("账号管理") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }, actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "添加") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onSurface)) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts, key = { it.id }) { account ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(account.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.W600); Spacer(Modifier.width(8.dp)); Surface(color = statusBgColor(account.keyStatus), shape = MaterialTheme.shapes.extraSmall) { Text(account.statusText, color = statusColor(account.keyStatus), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) } }
                            val platformShow = ApiAccount.platformNames[account.platform] ?: account.platform
                            Text(platformShow, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            val context = LocalContext.current; val clipboard = LocalClipboardManager.current
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("sk-...${account.apiKey.takeLast(4)}", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall); IconButton(onClick = { clipboard.setText(AnnotatedString(account.apiKey)); Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.ContentCopy, "复制", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp)) } }
                        }
                        IconButton(onClick = { onCheckKey(account) }) { Icon(Icons.Default.VerifiedUser, "检测", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { onEdit(account) }) { Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { deleteTarget = account }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
    deleteTarget?.let { account -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("确认删除?", color = MaterialTheme.colorScheme.onSurface) }, text = { Text("将删除${account.displayName}的账号及所有余额记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }, confirmButton = { TextButton(onClick = { onDelete(account); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) } }, containerColor = MaterialTheme.colorScheme.surface) }
}
