package app.yadavar.shamsi.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.model.Group
import app.yadavar.shamsi.model.GroupColor
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf

@Composable
fun WidgetTab(padding: PaddingValues, reminders: List<Reminder>, font: String, onFont: (String) -> Unit) {
    val open = reminders.filter { !it.completed }.take(8)
    val titleSize = when (font) {
        "sm" -> 14.sp
        "lg" -> 22.sp
        else -> 18.sp
    }
    val metaSize = when (font) {
        "sm" -> 12.sp
        "lg" -> 16.sp
        else -> 14.sp
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ویجت صفحهٔ اصلی", style = MaterialTheme.typography.headlineSmall)
        Text("روی صفحهٔ اصلی انگشت را نگه دار، ویجت‌ها را باز کن و «یادآور شمسی» را بگذار. تیک عنوان، یادآور را تمام می‌کند. اندازهٔ نوشته همین‌جا عوض می‌شود.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ").forEach { (id, label) ->
                FilterChip(selected = font == id, onClick = { onFont(id) }, label = { Text(label) })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .padding(4.dp),
        ) {
            Text("یادآور شمسی", fontSize = titleSize, color = MaterialTheme.colorScheme.primary)
            if (open.isEmpty()) Text("یادآوری نیست", fontSize = metaSize)
            open.forEach { reminder ->
                Text(reminder.title.ifBlank { "بدون عنوان" }, fontSize = titleSize)
                val meta = rowMeta(reminder, sectionOf(reminder), null)
                if (meta.isNotBlank()) Text(meta, fontSize = metaSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (reminder.expanded) {
                    reminder.items.take(3).forEach { item ->
                        Text(
                            "${if (item.done) "☑" else "☐"} ${item.title}",
                            fontSize = metaSize,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupsTab(padding: PaddingValues, groups: List<Group>, vm: HomeViewModel) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(GroupColor.Blue) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("گروه‌ها", style = MaterialTheme.typography.headlineSmall) }
        items(groups, key = { it.id }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group.name, color = Color(group.color.hex), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupColor.entries.forEach { entry ->
                        FilterChip(
                            selected = group.color == entry,
                            onClick = { vm.recolorGroup(group.id, entry) },
                            label = { Text(entry.label) },
                        )
                    }
                }
                var rename by remember(group.id) { mutableStateOf(group.name) }
                OutlinedTextField(value = rename, onValueChange = { rename = it }, label = { Text("نام") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.renameGroup(group.id, rename) }) { Text("ذخیرهٔ نام") }
                    if (groups.size > 1) {
                        OutlinedButton(onClick = { vm.deleteGroup(group.id) }) { Text("حذف") }
                    }
                }
            }
        }
        item {
            Text("گروه جدید", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupColor.entries.take(4).forEach { entry ->
                    FilterChip(selected = color == entry, onClick = { color = entry }, label = { Text(entry.label) })
                }
            }
            Button(onClick = {
                vm.addGroup(name, color)
                name = ""
            }) { Text("افزودن") }
        }
    }
}

@Composable
fun SettingsTab(padding: PaddingValues, settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    val exact = AlarmScheduler.canExact(context)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("تنظیمات", style = MaterialTheme.typography.headlineSmall)
        Text("ظاهر")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "خودکار", "light" to "روشن", "dark" to "تیره").forEach { (id, label) ->
                FilterChip(selected = settings.theme == id, onClick = { vm.setTheme(id) }, label = { Text(label) })
            }
        }
        Text("رنگ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("blue" to "آبی", "teal" to "سبزآبی", "amber" to "کهربایی", "rose" to "صورتی").forEach { (id, label) ->
                FilterChip(selected = settings.accent == id, onClick = { vm.setAccent(id) }, label = { Text(label) })
            }
        }
        Text("اندازهٔ نوشتهٔ ویجت")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ").forEach { (id, label) ->
                FilterChip(selected = settings.widgetFont == id, onClick = { vm.setFont(id) }, label = { Text(label) })
            }
        }
        Text(if (exact) "زنگ دقیق روشن است." else "برای زنگ سر وقت دقیق، اجازهٔ هشدار دقیق را بده.")
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                )
            }
        }) { Text("اجازهٔ زنگ دقیق") }
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            }
        }) { Text("اعلان‌ها") }
        Text(
            "یادآورها روی همین گوشی می‌مانند. زنگ با AlarmManager است و بعد از خاموش و روشن شدن دوباره تنظیم می‌شود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
