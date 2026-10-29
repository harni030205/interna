package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConversationSessionEntity
import com.example.data.local.LearningProgressEntity
import com.example.data.local.OfflineLessonEntity
import com.example.data.local.SavedVocabularyEntity
import com.example.data.local.UserEntity
import com.example.domain.model.LanguageCatalog
import com.example.ui.components.FuturisticCard
import com.example.ui.components.LanguageSelectorChips
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeAndQuizzesScreen(
    lessons: List<OfflineLessonEntity>,
    isOnline: Boolean,
    defaultLangCode: String,
    onToggleDownload: (OfflineLessonEntity) -> Unit,
    onSpeakPhrase: (phrase: String, bcp47Tag: String) -> Unit,
    onSubmitQuizAnswer: (OfflineLessonEntity, Int) -> Unit
) {
    val skillFilters = listOf("ALL", "VOCABULARY", "GRAMMAR", "LISTENING", "READING", "SPEAKING", "WRITING")
    var selectedLang by rememberSaveable { mutableStateOf("ALL") }
    var selectedSkill by rememberSaveable { mutableStateOf("ALL") }
    var activeLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedOptionIdx by rememberSaveable { mutableIntStateOf(-1) }
    var submittedForLessonId by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredLessons = remember(lessons, selectedLang, selectedSkill, isOnline) {
        lessons.filter { lesson ->
            val langOk = selectedLang == "ALL" || lesson.languageCode.equals(selectedLang, ignoreCase = true)
            val skillOk = selectedSkill == "ALL" || lesson.skillCategory.equals(selectedSkill, ignoreCase = true)
            val offlineOk = isOnline || lesson.isDownloadedOffline
            langOk && skillOk && offlineOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Practice & Offline Quizzes",
            subtitle = "Structured grammar, listening, reading & vocabulary modules — accessible offline",
            icon = Icons.Default.School
        )

        FuturisticCard {
            Text("Filter by Language", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedLang == "ALL",
                    onClick = { selectedLang = "ALL" },
                    label = { Text("All Languages") }
                )
                FilterChip(
                    selected = selectedLang == defaultLangCode,
                    onClick = { selectedLang = defaultLangCode },
                    label = { Text("My Target (${LanguageCatalog.byCode(defaultLangCode).englishName})") }
                )
            }
            Spacer(Modifier.height(6.dp))
            LanguageSelectorChips(
                selectedCode = if (selectedLang == "ALL") "" else selectedLang,
                onSelect = { selectedLang = it }
            )

            Spacer(Modifier.height(12.dp))
            Text("Skill Track", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                skillFilters.forEach { skill ->
                    FilterChip(
                        selected = selectedSkill == skill,
                        onClick = { selectedSkill = skill },
                        label = { Text(skill.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        if (filteredLessons.isEmpty()) {
            FuturisticCard {
                Text(
                    text = if (isOnline) {
                        "No lessons match the selected language and skill filter. Select 'All Languages' to browse all offline packs."
                    } else {
                        "No downloaded offline lessons match this filter while offline. Toggle 'All Languages' to access downloaded packs."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        filteredLessons.forEach { lesson ->
            val langInfo = LanguageCatalog.byCode(lesson.languageCode)
            val isExpanded = activeLessonId == lesson.id
            val options = remember(lesson.quizOptionsPipe) { lesson.quizOptionsPipe.split("|") }

            FuturisticCard(
                modifier = Modifier.testTag("lesson_card_${lesson.id}"),
                accentBorder = isExpanded
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPillBadge(label = langInfo.englishName, color = MaterialTheme.colorScheme.primary)
                            StatusPillBadge(label = lesson.level, color = MaterialTheme.colorScheme.secondary)
                            StatusPillBadge(label = lesson.skillCategory, color = EmeraldSuccess)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = lesson.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${lesson.summary} • ~${lesson.estimatedMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onToggleDownload(lesson) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_download_${lesson.id}")
                    ) {
                        Icon(
                            imageVector = if (lesson.isDownloadedOffline) Icons.Default.OfflinePin else Icons.Default.Download,
                            contentDescription = if (lesson.isDownloadedOffline) {
                                "Saved for offline study"
                            } else {
                                "Download lesson for offline study"
                            },
                            tint = if (lesson.isDownloadedOffline) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (isExpanded) {
                            activeLessonId = null
                        } else {
                            activeLessonId = lesson.id
                            selectedOptionIdx = -1
                            submittedForLessonId = null
                        }
                    },
                    modifier = Modifier.testTag("open_lesson_${lesson.id}")
                ) {
                    Text(if (isExpanded) "Hide Lesson & Quiz" else "Open Lesson & Interactive Quiz")
                }

                if (isExpanded) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(text = lesson.lessonBody, style = MaterialTheme.typography.bodyMedium)

                    if (lesson.targetPhraseForAudio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { onSpeakPhrase(lesson.targetPhraseForAudio, langInfo.bcp47Tag) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.cd_play_audio),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Key Phrase: \"${lesson.targetPhraseForAudio}\"",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Knowledge Check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = lesson.quizQuestion, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))

                    options.forEachIndexed { idx, opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedOptionIdx == idx,
                                onClick = { selectedOptionIdx = idx },
                                modifier = Modifier.testTag("quiz_option_${lesson.id}_$idx")
                            )
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (selectedOptionIdx >= 0) {
                                submittedForLessonId = lesson.id
                                onSubmitQuizAnswer(lesson, selectedOptionIdx)
                            }
                        },
                        enabled = selectedOptionIdx >= 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_quiz_${lesson.id}")
                    ) {
                        Text("Check Answer & Record Progress")
                    }

                    if (submittedForLessonId == lesson.id) {
                        val isCorrect = selectedOptionIdx == lesson.quizCorrectIndex
                        Spacer(Modifier.height(10.dp))
                        StatusPillBadge(
                            label = if (isCorrect) "CORRECT (+25 XP)" else "REVIEW (+10 XP)",
                            color = if (isCorrect) EmeraldSuccess else AmberWarning
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = lesson.quizExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressDashboardScreen(
    user: UserEntity?,
    progressList: List<LearningProgressEntity>,
    savedVocabulary: List<SavedVocabularyEntity>,
    pastSessions: List<ConversationSessionEntity>,
    isOnline: Boolean,
    onManualSync: () -> Unit
) {
    val totalXp = remember(progressList) { progressList.sumOf { it.xpEarned } }
    val totalStudyMinutes = remember(progressList, pastSessions) {
        progressList.sumOf { it.studyMinutes } + (pastSessions.sumOf { it.activeDurationSeconds } / 60L).toInt()
    }
    val distinctStudyDays = remember(progressList) { progressList.map { it.utcDate }.distinct().size }
    val unsyncedCount = remember(progressList) { progressList.count { !it.syncedToServer } }
    val avgQuizScore = remember(progressList) {
        if (progressList.isEmpty()) 0 else progressList.map { it.scorePercent }.average().toInt()
    }
    val byLanguage = remember(progressList) { progressList.groupBy { it.languageCode } }
    val bySkill = remember(progressList) { progressList.groupBy { it.skillCategory } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "My Learning Progress",
            subtitle = "Streaks, mastered vocabulary, skill breakdown & offline synchronization",
            icon = Icons.Default.Insights
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FuturisticCard(modifier = Modifier.weight(1f), accentBorder = true) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = AmberWarning)
                Spacer(Modifier.height(4.dp))
                Text("$distinctStudyDays days", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Active Study Days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FuturisticCard(modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = EmeraldSuccess)
                Spacer(Modifier.height(4.dp))
                Text("$totalXp XP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${totalStudyMinutes}m total study", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        FuturisticCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (unsyncedCount == 0) Icons.Default.CloudDone else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (unsyncedCount == 0) EmeraldSuccess else AmberWarning
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (unsyncedCount == 0) "All Study Records Synchronized" else "$unsyncedCount Offline Record(s) Queued for Sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isOnline) "Connected — records automatically synchronize." else "Offline — completed lessons & quizzes are safely stored on device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onManualSync,
                    modifier = Modifier.testTag("sync_progress_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Synchronize progress", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync")
                }
            }
        }

        FuturisticCard {
            Text("Milestone Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Current Target Level: ${user?.proficiencyLevel ?: "Intermediate"} (${user?.learningGoal ?: "Fluency"})")
            Text("Saved Vocabulary & Flashcards: ${savedVocabulary.size} words (${savedVocabulary.count { it.masteryLevel >= 4 }} mastered)")
            Text("Completed AI Conversations: ${pastSessions.size} sessions")
            Text("Average Lesson Quiz Accuracy: $avgQuizScore%")

            val dailyGoal = (user?.dailyStudyMinutesGoal ?: 20).coerceAtLeast(5)
            val goalRatio = (totalStudyMinutes.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
            Spacer(Modifier.height(10.dp))
            Text("Daily Study Target (${totalStudyMinutes}m / ${dailyGoal}m)", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { goalRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        FuturisticCard {
            Text("Progress by Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (byLanguage.isEmpty()) {
                Text(
                    "Complete an offline lesson quiz or AI conversation to build your per-language profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                byLanguage.forEach { (code, items) ->
                    val lang = LanguageCatalog.byCode(code)
                    val xp = items.sumOf { it.xpEarned }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${lang.englishName} (${lang.nativeName})", fontWeight = FontWeight.Medium)
                        Text("${items.size} modules • $xp XP", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        FuturisticCard {
            Text("Skill Areas & Weekly Focus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val trackedSkills = listOf("SPEAKING", "LISTENING", "VOCABULARY", "GRAMMAR", "READING", "WRITING")
            trackedSkills.forEach { skill ->
                val count = bySkill[skill]?.size ?: 0
                val ratio = (count / 5f).coerceIn(0.05f, 1f)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(skill.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                        Text("$count completed", style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }

            val leastPracticed = trackedSkills.minByOrNull { bySkill[it]?.size ?: 0 } ?: "SPEAKING"
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Recommendation: Focus your next 10-minute study block on ${leastPracticed.lowercase()} exercises and review difficult flashcards in Universal Dictionary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        FuturisticCard {
            Text(
                text = stringResource(R.string.disclaimer_level_non_certified),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
