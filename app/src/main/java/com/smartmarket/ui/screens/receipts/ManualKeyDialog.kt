package com.smartmarket.ui.screens.receipts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smartmarket.domain.nfce.NfceKey
import com.smartmarket.domain.nfce.SefazDirectory

/**
 * Digitar ou colar a chave de acesso (44 dígitos), validada pelo dígito verificador.
 * [onConfirm] recebe só os dígitos.
 */
@Composable
fun ManualKeyDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    val digits = text.filter { it.isDigit() }.take(NfceKey.LENGTH)
    val complete = digits.length == NfceKey.LENGTH
    val key = if (complete) NfceKey.parse(digits) else null
    val error: String? = when {
        !complete -> null
        key == null -> "Chave inválida: confira os dígitos."
        key.isNfe -> "Esta é uma NF-e (modelo 55). Só notas de consumidor (NFC-e) podem ser importadas."
        !key.isNfce -> "Esta chave não é de uma nota de consumidor."
        else -> null
    }
    val valid = key != null && key.isNfce

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Digitar chave de acesso") },
        text = {
            Column {
                Text(
                    "A chave tem 44 números e fica abaixo do QR Code da nota.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = digits,
                    visualTransformation = KeyGroupingTransformation,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(NfceKey.LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chave de acesso") },
                    placeholder = { Text("0000 0000 0000 ...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null,
                    supportingText = {
                        Text(
                            error ?: if (valid) "Chave válida · ${SefazDirectory.state(key!!.cUF)?.name ?: "UF ${key.cUF}"}"
                            else "${digits.length} de 44 dígitos"
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { pasted -> text = pasted.filter { it.isDigit() }.take(NfceKey.LENGTH) }
                        }) { Icon(Icons.Default.ContentPaste, contentDescription = "Colar") }
                    },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(digits) }, enabled = valid) { Text("Consultar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Mostra a chave em grupos de 4 ("3325 0500 ...") sem alterar o valor digitado. */
private object KeyGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = raw.chunked(4).joinToString(" ")
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 0) 0 else (offset + (offset - 1) / 4).coerceAtMost(out.length)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / 5).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
