import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.Alarm
import liveplugin.show

val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, pluginDisposable)

val idleDelayMilliseconds = 1500
val maxTypingIntervalMilliseconds = 5000L

var isTypingSessionActive = false
var lastCompileTime = 0L

fun compileLaTeX(project: Project) {
    ApplicationManager.getApplication().invokeLater {
        // Flushes in-memory editor changes to disk before running LaTeX
        FileDocumentManager.getInstance().saveAllDocuments()

        val runManager = RunManager.getInstance(project)
        val config = runManager.allSettings.firstOrNull {
            it.factory.type.id.contains("LATEX", ignoreCase = true)
        } ?: runManager.selectedConfiguration

        val defaultConfig = runManager.allSettings.firstOrNull() ?: runManager.selectedConfiguration


        if (config != null && defaultConfig != null) {
            ProgramRunnerUtil.executeConfiguration(
                config,
                DefaultRunExecutor.getRunExecutorInstance()
            )
        } else if (defaultConfig != null) {
            ProgramRunnerUtil.executeConfiguration(
                defaultConfig,
                DefaultRunExecutor.getRunExecutorInstance()
            )
        } else {
            show("No TeXiFy LaTeX Run Configuration found.")
        }
    }
}

val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        if (file.extension !in listOf("tex", "bib", "sty")) return

        val project = ProjectLocator.getInstance().guessProjectForFile(file)
            ?: ProjectManager.getInstance().openProjects.firstOrNull()
            ?: return

        val now = System.currentTimeMillis()

        if (!isTypingSessionActive) {
            isTypingSessionActive = true
            lastCompileTime = now
        }

        if (now - lastCompileTime >= maxTypingIntervalMilliseconds) {
            lastCompileTime = now
            compileLaTeX(project)
        }

        alarm.cancelAllRequests()
        alarm.addRequest({
            isTypingSessionActive = false
            compileLaTeX(project)
        }, idleDelayMilliseconds)
    }
}

EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, pluginDisposable)

show("TeXiFy live update")