package ca.gpsprobuild.app.domain.template

import android.content.Context
import ca.gpsprobuild.app.domain.model.JobPhase
import ca.gpsprobuild.app.domain.model.JobType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class TemplateTask(
    val phase: JobPhase,
    val title: String,
    val hours: Double?,
    val milestone: Boolean,
    val inspection: Boolean
)

data class JobTemplate(
    val jobType: JobType,
    val label: String,
    val tasks: List<TemplateTask>
)

/**
 * Reads `assets/task_templates.json` — 23 job types, 564 tasks.
 *
 * Parsed with org.json rather than kotlinx.serialization on purpose: the file is
 * loaded once, lazily, and hand-parsing keeps unknown or malformed entries from
 * taking the whole file down. A checklist that is missing three lines is a
 * nuisance; a checklist that fails to load at all stops someone working.
 */
@Singleton
class TaskTemplateLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache: Map<JobType, JobTemplate> by lazy { load() }

    fun forJobType(jobType: JobType): JobTemplate? = cache[jobType]

    fun taskCount(jobType: JobType): Int = cache[jobType]?.tasks?.size ?: 0

    private fun load(): Map<JobType, JobTemplate> = runCatching {
        val raw = context.assets.open("task_templates.json")
            .bufferedReader()
            .use { it.readText() }

        val templates = JSONObject(raw).getJSONArray("templates")
        buildMap {
            for (i in 0 until templates.length()) {
                val node = templates.getJSONObject(i)
                val jobType = runCatching { JobType.valueOf(node.getString("jobType")) }.getOrNull()
                    ?: continue

                val taskArray = node.getJSONArray("tasks")
                val tasks = buildList {
                    for (j in 0 until taskArray.length()) {
                        val t = taskArray.getJSONObject(j)
                        val phase = runCatching { JobPhase.valueOf(t.getString("phase")) }
                            .getOrNull() ?: continue
                        add(
                            TemplateTask(
                                phase = phase,
                                title = t.getString("title"),
                                hours = if (t.has("hours")) t.getDouble("hours") else null,
                                milestone = t.optBoolean("milestone", false),
                                inspection = t.optBoolean("inspection", false)
                            )
                        )
                    }
                }

                put(
                    jobType,
                    JobTemplate(
                        jobType = jobType,
                        label = node.optString("label", jobType.label),
                        tasks = tasks
                    )
                )
            }
        }
    }.getOrElse { emptyMap() }
}
