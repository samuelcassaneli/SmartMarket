package com.smartmarket.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.smartmarket.data.prefs.AppSettings
import com.smartmarket.data.prefs.ThemeMode
import com.smartmarket.ui.appViewModel
import com.smartmarket.ui.components.AppCard
import com.smartmarket.ui.components.AppTopBar
import com.smartmarket.ui.components.ConfirmDialog
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.components.IconBadge
import com.smartmarket.ui.components.LoadingBox
import com.smartmarket.ui.components.ScreenScaffold
import com.smartmarket.ui.components.SectionHeader
import com.smartmarket.ui.screens.product.DecimalField
import com.smartmarket.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.smartmarket.domain.ai.GeminiClient

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val vm = appViewModel { c -> SettingsViewModel(c.context, c.settings, c.db, c.gemini) }
    val aiChecking by vm.aiChecking.collectAsStateWithLifecycle()
    val settings by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmImport by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // ---- Launchers (Storage Access Framework e permissões)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.enableAutoBackup(uri)
    }
    var notificationDenied by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted
    }
    LaunchedEffect(notificationDenied) {
        if (notificationDenied) {
            snackbar.showSnackbar("Sem permissão de notificação os alertas não aparecem. Ative nas configurações do Android.")
            notificationDenied = false
        }
    }

    ScreenScaffold(
        topBar = { AppTopBar(title = "Configurações", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val s = settings
        if (s == null) {
            LoadingBox(Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (busy) {
                item(key = "busy") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }

            item(key = "loc-h") { SectionHeader("Localização") }
            item(key = "loc") { PostalCodeCard(s.postalCode, onSave = { vm.setPostalCode(it) }) }

            item(key = "budget-h") { SectionHeader("Orçamento mensal") }
            item(key = "budget") { BudgetCard(s.monthlyBudget, onSave = { vm.setMonthlyBudget(it) }) }

            item(key = "theme-h") { SectionHeader("Aparência") }
            item(key = "theme") { ThemeCard(s.themeMode, onChange = { vm.setTheme(it) }) }

            item(key = "check-h") { SectionHeader("Verificação de promoções") }
            item(key = "check") {
                PriceCheckCard(
                    intervalDays = s.checkIntervalDays,
                    onInterval = { days ->
                        vm.setCheckInterval(days)
                        if (days > 0 && Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(context)) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onCheckNow = { vm.checkNow() }
                )
            }

            item(key = "ai-h") { SectionHeader("Leitura de encartes com IA") }
            item(key = "ai") {
                AiCard(
                    settings = s,
                    checking = aiChecking,
                    onToggle = { vm.setAiEnabled(it) },
                    onSaveKey = { vm.saveGeminiKey(it) },
                    onOpenKeyPage = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(GeminiClient.KEY_PAGE_URL))
                            )
                        }
                    }
                )
            }

            item(key = "auto-h") { SectionHeader("Automação") }
            item(key = "auto") {
                AutomationCard(
                    settings = s,
                    onAlerts = { on ->
                        vm.setPriceAlerts(on)
                        if (on && Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(context)) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onAutoBackup = { on ->
                        if (on) folderLauncher.launch(null) else vm.disableAutoBackup()
                    },
                    onChangeFolder = { folderLauncher.launch(null) },
                    onBackupNow = { vm.backupNow() }
                )
            }

            item(key = "backup-h") { SectionHeader("Backup") }
            item(key = "backup") {
                BackupCard(
                    busy = busy,
                    onExport = { exportLauncher.launch(defaultBackupName()) },
                    onImport = { confirmImport = true }
                )
            }

            item(key = "about-h") { SectionHeader("Sobre") }
            item(key = "about") { AboutCard(appVersion(context)) }
        }
    }

    if (confirmImport) {
        ConfirmDialog(
            title = "Restaurar backup?",
            text = "Todos os dados atuais (listas, preços, notas, despensa) serão apagados e substituídos pelos do arquivo. Não dá para desfazer.",
            confirmLabel = "Escolher arquivo",
            destructive = true,
            onConfirm = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onDismiss = { confirmImport = false }
        )
    }
}

// ---------------------------------------------------------------- Seções

@Composable
private fun PostalCodeCard(current: String, onSave: (String) -> Unit) {
    var cep by rememberSaveable(current) { mutableStateOf(current.filter(Char::isDigit).take(8)) }
    val valid = cep.length == 8
    SettingsCard(Icons.Filled.LocationOn, "CEP", "Define a loja do Atacadão usada na busca online.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cep,
                onValueChange = { cep = it.filter(Char::isDigit).take(8) },
                label = { Text("CEP") },
                placeholder = { Text("00000-000") },
                singleLine = true,
                isError = cep.isNotEmpty() && !valid,
                visualTransformation = CepVisualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = { onSave(cep) }, enabled = valid && cep != current) { Text("Salvar") }
        }
    }
}

@Composable
private fun BudgetCard(current: Double, onSave: (Double) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf(if (current > 0) Fmt.decimal(current) else "") }
    val parsed = if (text.isBlank()) 0.0 else Fmt.parseMoney(text)
    SettingsCard(
        Icons.Filled.Savings,
        "Quanto quer gastar por mês",
        "Usado no painel inicial. Em branco, o app soma os orçamentos das listas do mês."
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DecimalField(
                value = text,
                onValueChange = { text = it },
                label = "Orçamento",
                prefix = "R$ ",
                isError = parsed == null,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null && parsed != current
            ) { Text("Salvar") }
        }
    }
}

private val THEMES = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")

@Composable
private fun ThemeCard(current: String, onChange: (String) -> Unit) {
    SettingsCard(Icons.Filled.Palette, "Tema", null) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            THEMES.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = current == mode,
                    onClick = { onChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = THEMES.size)
                ) { Text(label) }
            }
        }
    }
}

private val INTERVALS = listOf(0 to "Desligada", 1 to "Diária", 3 to "3 dias", 7 to "Semanal", 15 to "15 dias")

@Composable
private fun PriceCheckCard(intervalDays: Int, onInterval: (Int) -> Unit, onCheckNow: () -> Unit) {
    SettingsCard(
        Icons.Outlined.Newspaper,
        "Frequência",
        "Baixa os encartes novos, consulta os sites dos mercados e avisa (com pop-up) quando algo que você " +
            "compra ficou mais barato que na última compra ou na última verificação."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            INTERVALS.forEach { (days, label) ->
                FilterChip(selected = intervalDays == days, onClick = { onInterval(days) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        FilledTonalButton(onClick = onCheckNow) { Text("Verificar agora") }
    }
}

@Composable
private fun AiCard(
    settings: AppSettings,
    checking: Boolean,
    onToggle: (Boolean) -> Unit,
    onSaveKey: (String) -> Unit,
    onOpenKeyPage: () -> Unit
) {
    var key by rememberSaveable(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var visible by rememberSaveable { mutableStateOf(false) }
    SettingsCard(
        Icons.Filled.AutoAwesome,
        "Gemini (Google), grátis",
        "A IA lê cada página do encarte como uma pessoa leria: nome completo, preço normal e as condições " +
            "(app, cartão, atacado). Use uma chave gratuita do Google AI Studio; na camada gratuita não há cobrança. " +
            "Se a cota do dia acabar, o app usa a leitura local."
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usar IA para ler encartes", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = settings.aiEnabled, onCheckedChange = onToggle, enabled = settings.geminiApiKey.isNotBlank())
        }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("Chave da API (AIza…)") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Mostrar chave")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (settings.geminiModel.isNotBlank() && key == settings.geminiApiKey) {
            Text(
                "Modelo em uso: ${settings.geminiModel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilledTonalButton(onClick = { onSaveKey(key) }, enabled = !checking) {
                Text(if (checking) "Validando…" else "Salvar e validar")
            }
            TextButton(onClick = onOpenKeyPage) { Text("Criar chave grátis") }
        }
    }
}

@Composable
private fun AutomationCard(
    settings: AppSettings,
    onAlerts: (Boolean) -> Unit,
    onAutoBackup: (Boolean) -> Unit,
    onChangeFolder: () -> Unit,
    onBackupNow: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
        ToggleRow(
            icon = Icons.Filled.NotificationsActive,
            title = "Alertas de preço",
            subtitle = "Avisa quando um produto acompanhado fica mais barato",
            checked = settings.priceAlertsEnabled,
            onChange = onAlerts
        )
        val enabledWithFolder = settings.autoBackupEnabled && settings.autoBackupTreeUri.isNotBlank()
        ToggleRow(
            icon = Icons.Filled.Backup,
            title = "Backup automático semanal",
            subtitle = if (enabledWithFolder) "Pasta: ${folderLabel(settings.autoBackupTreeUri)} · guarda os 4 mais recentes"
            else "Salva uma cópia por semana numa pasta que você escolher",
            checked = enabledWithFolder,
            onChange = onAutoBackup
        )
        if (enabledWithFolder) {
            Row(
                Modifier.fillMaxWidth().padding(start = 68.dp, end = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TextButton(onClick = onChangeFolder) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Trocar pasta")
                }
                TextButton(onClick = onBackupNow) { Text("Fazer agora") }
            }
        }
    }
}

@Composable
private fun BackupCard(busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    SettingsCard(
        Icons.Filled.CloudUpload,
        "Exportar e importar",
        "Um arquivo JSON com tudo: produtos, preços, listas, notas e despensa. Dá para salvar no Drive pelo seletor."
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilledTonalButton(onClick = onExport, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Exportar")
            }
            FilledTonalButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Importar")
            }
        }
    }
}

@Composable
private fun AboutCard(version: String) {
    SettingsCard(Icons.Filled.Info, "SmartMarket", "Versão $version") {
        Text(
            "Organize compras, compare preços dos mercados de Resende e acompanhe seus gastos. Seus dados ficam só no seu celular.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- Blocos

@Composable
private fun SettingsCard(icon: ImageVector, title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 36.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        content()
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- Utilidades

/** Máscara 00000-000 sobre um texto só com dígitos. */
private object CepVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = if (digits.length > 5) digits.substring(0, 5) + "-" + digits.substring(5) else digits
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (digits.length > 5 && offset > 5) offset + 1 else offset

            override fun transformedToOriginal(offset: Int): Int =
                (if (digits.length > 5 && offset > 5) offset - 1 else offset).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun appVersion(context: Context): String = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "—"

private fun defaultBackupName(): String =
    "smartmarket-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".json"

/** "content://.../tree/primary%3ADocuments%2FBackups" -> "Documents/Backups" */
private fun folderLabel(treeUri: String): String = runCatching {
    Uri.parse(treeUri).lastPathSegment?.substringAfter(':')?.ifBlank { null }
}.getOrNull() ?: "escolhida"
