package app.yadavar.shamsi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.model.Group
import app.yadavar.shamsi.model.GroupColor
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.voice.ParsedSpeech
import app.yadavar.shamsi.widget.YadavarWidget
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { Home, Widget, Groups, Settings }

class HomeViewModel : ViewModel() {
    val reminders = Graph.repo.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val groups = Graph.repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = Graph.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.yadavar.shamsi.model.Settings())

    var editing by mutableStateOf<Reminder?>(null)
    var tab by mutableStateOf(Tab.Home)
    var filter by mutableStateOf("all")
    var notice by mutableStateOf<String?>(null)

    fun openNew() {
        editing = blankReminder(groups.value.firstOrNull()?.id ?: "personal")
    }

    fun open(reminder: Reminder) {
        editing = reminder
    }

    fun openParsed(parsed: ParsedSpeech) {
        val base = blankReminder(groups.value.firstOrNull()?.id ?: "personal")
        editing = base.copy(
            title = parsed.title,
            jy = parsed.jy,
            jm = parsed.jm,
            jd = parsed.jd,
            hour = parsed.hour,
            minute = parsed.minute,
            alert = parsed.hour != null && parsed.jy != null,
        )
        if (parsed.title.isBlank() && parsed.jy == null && parsed.hour == null) {
            notice = "از این جمله تاریخ یا عنوان درنیامد"
        }
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { notice = "انجام نشد" }
        }
    }

    fun save(reminder: Reminder) = run {
        Actions.save(reminder)
        editing = null
    }

    fun setTheme(value: String) = run { Graph.settings.setTheme(value) }
    fun setAccent(value: String) = run { Graph.settings.setAccent(value) }
    fun setFont(value: String) = run {
        Graph.settings.setFont(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun addGroup(name: String, color: GroupColor) = run {
        val clean = name.trim()
        if (clean.isEmpty()) return@run
        Graph.repo.addGroup(Group(UUID.randomUUID().toString(), clean, color))
    }

    fun renameGroup(id: String, name: String) = run {
        val current = Graph.repo.groupsNow().firstOrNull { it.id == id } ?: return@run
        val clean = name.trim().ifBlank { return@run }
        Graph.repo.addGroup(current.copy(name = clean))
    }

    fun recolorGroup(id: String, color: GroupColor) = run {
        val current = Graph.repo.groupsNow().firstOrNull { it.id == id } ?: return@run
        Graph.repo.addGroup(current.copy(color = color))
    }

    fun deleteGroup(id: String) = run { Graph.repo.deleteGroup(id) }
}
