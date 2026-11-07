package app.yadavar.shamsi.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.CheckItem
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Section
import app.yadavar.shamsi.model.formatDayMonth
import app.yadavar.shamsi.model.occurrenceEpoch
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf
import app.yadavar.shamsi.ui.MainActivity
import java.util.Date

private val BgColor = Color(0xFF16181D)
private val TextColor = Color(0xFFF4F6F8)
private val MutedColor = Color(0xFF9AA3AF)
private val AccentColor = Color(0xFF4C8DFF)

object WidgetKeys {
    val id = ActionParameters.Key<String>("id")
    val item = ActionParameters.Key<String>("item")
}

sealed interface WidgetRow {
    val key: Long

    data class Main(val reminder: Reminder) : WidgetRow {
        override val key: Long get() = reminder.id.hashCode().toLong()
    }

    data class Sub(val reminder: Reminder, val item: CheckItem) : WidgetRow {
        override val key: Long get() = (reminder.id + item.id).hashCode().toLong()
    }
}

/** Title and meta text sizes (sp) for the three widget font levels. */
private fun widgetSizes(font: String): Pair<Int, Int> = when (font) {
    "sm" -> 12 to 10
    "lg" -> 18 to 14
    else -> 15 to 12
}

class YadavarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = Date()
        val reminders = Graph.repo.all()
            .filter { !it.completed }
            .filter {
                val section = sectionOf(it, now)
                section == Section.Today || section == Section.Overdue
            }
            .sortedBy { occurrenceEpoch(it) ?: Long.MAX_VALUE }
        val rows = buildList {
            reminders.forEach { reminder ->
                add(WidgetRow.Main(reminder))
                if (reminder.expanded) reminder.items.forEach { add(WidgetRow.Sub(reminder, it)) }
            }
        }
        val (titleSp, metaSp) = widgetSizes(Graph.settings.current().widgetFont)
        val header = "یادآورهای امروز · " + formatDayMonth(toJalali(now))
        provideContent { WidgetContent(rows, header, titleSp, metaSp) }
    }
}

@Composable
private fun WidgetContent(rows: List<WidgetRow>, header: String, titleSp: Int, metaSp: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgColor))
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Text(
            text = header,
            style = TextStyle(
                color = ColorProvider(TextColor),
                fontSize = (titleSp + 1).sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clickable(actionRunCallback<OpenAppAction>()),
        )
        if (rows.isEmpty()) {
            Text(
                text = "برای امروز کاری باقی نمانده است",
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = metaSp.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.key }) { row ->
                    when (row) {
                        is WidgetRow.Main -> MainRow(row.reminder, titleSp, metaSp)
                        is WidgetRow.Sub -> SubRow(row.reminder, row.item, titleSp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainRow(reminder: Reminder, titleSp: Int, metaSp: Int) {
    val meta = rowMeta(reminder, null, null)
    val mark = if (reminder.starred) "★ " else ""
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<CompleteAction>(actionParametersOf(WidgetKeys.id to reminder.id)),
                text = mark + reminder.title.ifBlank { "بدون عنوان" },
                style = TextStyle(color = ColorProvider(TextColor), fontSize = titleSp.sp),
                colors = CheckboxDefaults.colors(
                    checkedColor = ColorProvider(AccentColor),
                    uncheckedColor = ColorProvider(MutedColor),
                ),
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = TextStyle(color = ColorProvider(MutedColor), fontSize = metaSp.sp),
                    modifier = GlanceModifier.padding(start = 32.dp),
                )
            }
        }
        if (reminder.items.isNotEmpty()) {
            Text(
                text = if (reminder.expanded) "▲" else "▼",
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = titleSp.sp),
                modifier = GlanceModifier
                    .padding(8.dp)
                    .clickable(actionRunCallback<ToggleExpandAction>(actionParametersOf(WidgetKeys.id to reminder.id))),
            )
        }
    }
}

@Composable
private fun SubRow(reminder: Reminder, item: CheckItem, titleSp: Int) {
    val style = if (item.done) {
        TextStyle(
            color = ColorProvider(MutedColor),
            fontSize = (titleSp - 1).sp,
            textDecoration = TextDecoration.LineThrough,
        )
    } else {
        TextStyle(color = ColorProvider(TextColor), fontSize = (titleSp - 1).sp)
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = item.done,
            onCheckedChange = actionRunCallback<ToggleItemAction>(
                actionParametersOf(WidgetKeys.id to reminder.id, WidgetKeys.item to item.id),
            ),
            text = item.title,
            style = style,
            colors = CheckboxDefaults.colors(
                checkedColor = ColorProvider(MutedColor),
                uncheckedColor = ColorProvider(MutedColor),
            ),
        )
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        Actions.complete(id)
    }
}

class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        val item = parameters[WidgetKeys.item] ?: return
        Actions.toggleItem(id, item)
    }
}

class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        Actions.toggleExpanded(id)
    }
}

class YadavarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YadavarWidget()
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AlarmScheduler.reschedule(applicationContext)
        YadavarWidget().updateAll(applicationContext)
        return Result.success()
    }
}
