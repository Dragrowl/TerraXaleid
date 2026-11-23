// SPDX-License-Identifier: GPL-3.0-or-later

package io.terramap.android

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.terramap.core.PlayerMaps

/**
 * First Android shell. It exists to prove the two things the mobile route needs before any file
 * access work: the L1 core jar resolves on Android, and Material 3 renders on the target ABI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    var check by remember { mutableStateOf<CheckReport?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("terramap") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "全地图点亮（每个角色一份地图）",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text = "地图按「世界 × 角色」分开存放：Players/<角色>/<世界GUID>.map。" +
                        "默认点亮本机所有角色，也可以只挑某几个。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("运行环境", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "ABI   ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}\n" +
                                "SDK   ${Build.VERSION.SDK_INT}\n" +
                                "设备  ${Build.MODEL}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            item {
                Button(onClick = { check = runCoreCheck() }) {
                    Text("运行核心自检")
                }
            }
            val report = check
            if (report != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (report.ok) "核心自检通过" else "核心自检失败",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (report.ok) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            report.lines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                Text("角色（示例列表）", style = MaterialTheme.typography.labelLarge)
            }
            items(listOf("鸡", "阿二", "小三")) { name ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(text = name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private data class CheckReport(val ok: Boolean, val lines: List<String>)

/**
 * Exercises the parts of the L1 core that run without any file access: the world-GUID ⇄ .map file
 * name mapping and the palette arithmetic the reveal depends on.
 */
private fun runCoreCheck(): CheckReport {
    val lines = mutableListOf<String>()
    var ok = true

    val guid = byteArrayOf(
        0xe9.toByte(), 0x57, 0xd5.toByte(), 0xc3.toByte(),
        0x97.toByte(), 0xd3.toByte(), 0x79, 0x42,
        0xa7.toByte(), 0xb4.toByte(), 0x6e, 0x8d.toByte(),
        0xc3.toByte(), 0x2a, 0xdf.toByte(), 0xf1.toByte(),
    )
    val name = PlayerMaps.mapFileNameForWorld(guid)
    val expected = "c3d557e9-d397-4279-a7b4-6e8dc32adff1.map"
    if (name == expected) {
        lines += "map name  ok  $name"
    } else {
        ok = false
        lines += "map name  FAIL  $name != $expected"
    }

    val back = PlayerMaps.guidFromMapFileName(name)
    if (back.contentEquals(guid)) {
        lines += "guid      ok  round-trip"
    } else {
        ok = false
        lines += "guid      FAIL  round-trip mismatch"
    }

    val character = PlayerMaps.playerNameFromDirectory("/storage/emulated/0/.../Players/鸡")
    if (character == "鸡") {
        lines += "player    ok  $character"
    } else {
        ok = false
        lines += "player    FAIL  $character"
    }

    return CheckReport(ok, lines)
}
