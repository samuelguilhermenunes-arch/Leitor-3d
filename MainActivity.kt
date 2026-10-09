package com.modscout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Observar jogo", "Verificar mod", "Ajuda")
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        when (tab) {
            0 -> WatchScreen()
            1 -> CheckScreen()
            else -> HelpScreen()
        }
    }
}

private data class AppInfo(val label: String, val pkg: String)

@Composable
fun WatchScreen() {
    val ctx = LocalContext.current
    val apps = remember { installedApps(ctx) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    var running by remember { mutableStateOf(false) }
    val lines by LogStore.lines.collectAsState()

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            treeUri = uri
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("1) Permissões (só na primeira vez)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Acesso ao uso", fontSize = 12.sp) }
                OutlinedButton(onClick = {
                    val i = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }) { Text("Acesso a arquivos", fontSize = 12.sp) }
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Notificação", fontSize = 12.sp) }
            }
            val hasFiles = Environment.isExternalStorageManager()
            Text(
                if (hasFiles) "Acesso a arquivos: liberado" else "Acesso a arquivos: ainda não liberado",
                fontSize = 12.sp, color = if (hasFiles) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
        item {
            Text("2) Pasta do jogo pelo MT Manager", style = MaterialTheme.typography.titleSmall)
            Text(
                "Toque no botão, abra o menu do seletor (☰), escolha \"MT Manager\", entre em Android > data > pasta do jogo e toque em \"Usar esta pasta\".",
                fontSize = 12.sp, color = Color.Gray
            )
            OutlinedButton(onClick = { treePicker.launch(null) }) { Text("Escolher pasta (MT Manager)") }
            Text(
                treeUri?.let { "Pasta: ${Uri.decode(it.lastPathSegment ?: it.toString())}" } ?: "Nenhuma pasta escolhida",
                fontSize = 12.sp
            )
        }
        item {
            Text("3) Escolha o jogo (para abrir e detectar)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Buscar (ex: farming)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        val filtered = apps.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }.take(8)
        items(filtered) { a ->
            val sel = selected?.pkg == a.pkg
            Surface(
                tonalElevation = if (sel) 6.dp else 0.dp,
                onClick = { selected = a },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(a.label + if (sel) "  ✔" else "")
                    Text(a.pkg, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
        item {
            Text("4) Comece", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = (selected != null || treeUri != null) && !running, onClick = {
                    LogStore.clear()
                    val svc = Intent(ctx, WatchService::class.java)
                    selected?.let { svc.putExtra(WatchService.EXTRA_PKG, it.pkg) }
                    treeUri?.let { svc.putExtra(WatchService.EXTRA_TREE, it.toString()) }
                    ctx.startForegroundService(svc)
                    running = true
                    selected?.let { a ->
                        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let {
                            ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }) { Text("Observar e abrir jogo") }
                OutlinedButton(enabled = running, onClick = {
                    ctx.startService(Intent(ctx, WatchService::class.java).setAction(WatchService.ACTION_STOP))
                    running = false
                }) { Text("Parar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, LogStore.report())
                    ctx.startActivity(Intent.createChooser(send, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Compartilhar relatório", fontSize = 12.sp) }
                OutlinedButton(onClick = { LogStore.clear() }) { Text("Limpar", fontSize = 12.sp) }
            }
            Text("Atividade (${lines.size})", style = MaterialTheme.typography.titleSmall)
        }
        items(lines.takeLast(200).reversed()) { Text(it, fontSize = 11.sp) }
    }
}

@Composable
fun CheckScreen() {
    val ctx = LocalContext.current
    var results by remember { mutableStateOf<List<CheckItem>?>(null) }
    var fileName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileName = uri.lastPathSegment ?: ""
            results = try { ModChecker.check(ctx.contentResolver, uri) }
            catch (e: Exception) { listOf(CheckItem(false, "Erro ao ler: ${e.message}")) }
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Escolha o .zip do mod e eu digo o que está faltando.")
        Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
            Text("Escolher mod (.zip)")
        }
        if (fileName.isNotEmpty()) Text(fileName, fontSize = 12.sp, color = Color.Gray)
        val r = results
        if (r != null) {
            val bad = r.count { !it.ok }
            Text(
                if (bad == 0) "Nenhum problema encontrado" else "$bad problema(s) encontrado(s)",
                style = MaterialTheme.typography.titleMedium,
                color = if (bad == 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(r) { Text((if (it.ok) "✅ " else "❌ ") + it.text, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Como usar", style = MaterialTheme.typography.titleMedium)
        Text("• Aba Observar: libere as 3 permissões, escolha o jogo e toque em \"Observar e abrir jogo\". Use o jogo normalmente (ex.: carregar um mod ou usar a função que quer entender). Volte ao ModScout para ver quais arquivos foram abertos, criados ou alterados.")
        Text("• Aba Verificar: escolha o .zip do mod antes de colocá-lo no jogo. O app aponta arquivos que faltam, caminhos errados e diferenças de maiúscula/minúscula.")
        Text("Limitação importante", style = MaterialTheme.typography.titleMedium)
        Text("A partir do Android 11, a pasta Android/data de outros apps é bloqueada para apps comuns. Por isso o ModScout lê a pasta pelo MT Manager: ele aparece como opção no seletor de pastas do sistema. O MT Manager precisa estar instalado e com acesso a Android/data liberado nele. Pelo MT Manager o app vê o que foi criado, alterado ou apagado (a leitura é refeita a cada 2 segundos), mas não consegue saber quando um arquivo é apenas aberto. A aba Verificar funciona sempre.")
        Text("O app não vê o que o jogo lê dentro da memória ou de pastas privadas. Ele mostra só arquivos nas pastas observadas.", fontSize = 12.sp, color = Color.Gray)
    }
}

private fun installedApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != ctx.packageName }
        .sortedBy { it.label.lowercase() }
}
