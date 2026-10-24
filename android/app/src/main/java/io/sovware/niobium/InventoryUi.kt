package io.sovware.niobium

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun InventoryFeedback(load: InventoryLoad, empty: Boolean, emptyText: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (load.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).semantics { contentDescription = "Loading inventory" }, color = Nb.Ion)
            if (empty) Text("Loading…", color = Nb.Secondary, fontSize = 13.sp)
        }
        load.error?.let {
            Text(it, color = Nb.Rose, fontSize = 13.sp)
            PixelButton("Retry", PixelGlyph.REFRESH, secondary = true, enabled = !load.loading, onClick = onRetry)
        }
        load.warning?.let { Text(it, color = Nb.Amber, fontSize = 12.sp) }
        if (empty && !load.loading && load.error == null) Text(emptyText, color = Nb.Secondary, fontSize = 13.sp)
    }
}
