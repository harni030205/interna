package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.DictionaryCacheEntity
import com.example.data.local.SavedVocabularyEntity
import com.example.domain.model.DictionaryResultModel
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.PronunciationFeedbackModel
import com.example.domain.model.TranslationResultModel
import com.example.ui.components.FuturisticCard
import com.example.ui.components.LanguageSelectorChips
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalDictionaryScreen(
    result: DictionaryResultModel?,
    savedVocabulary: List<SavedVocabularyEntity>,
    recentCached: List<DictionaryCacheEntity>,
    isBusy: Boolean,
    onSearch: (query: String, sourceLang: String, targetLang: String) -> Unit,
    onSaveWord: (markDifficult: Boolean) -> Unit,
    onPlayVerifiedAudio: (url: String) -> Unit,
    onSpeakTts: (text: String, langCode: String) -> Unit,
    onReviewFlashcard: (SavedVocabularyEntity, rememberedWell: Boolean) -> Unit,
    onDeleteVocab: (String) -> Unit
) {
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceLang by rememberSaveable { mutableStateOf("en") }
    var targetLang by rememberSaveable { mutableStateOf("fr") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Universal Dictionary",
            subtitle = "Verified lexicon entries, genuine IPA, flashcards & offline cache",
            icon = Icons.AutoMirrored.Filled.MenuBook
        )

        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Search") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Vocab (${savedVocabulary.size})") })
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Flashcards & Spelling") })
        }

        Spacer(Modifier.height(12.dp))

        when (activeTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FuturisticCard {
                        Text("From Language", style = MaterialTheme.typography.labelMedium)
                        LanguageSelectorChips(selectedCode = sourceLang, onSelect = { sourceLang = it })
                        Spacer(Modifier.height(8.dp))
                        Text("Explain / Translate Into", style = MaterialTheme.typography.labelMedium)
                        LanguageSelectorChips(selectedCode = targetLang, onSelect = { targetLang = it })

                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Word, phrase, verb, or root") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dictionary_search_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onSearch(query, sourceLang, targetLang) },
                            enabled = query.isNotBlank() && !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dictionary_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBusy) "Searching..." else "Search Dictionary")
                        }
                    }

                    if (result != null) {
                        FuturisticCard(accentBorder = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = result.verifiedIpa ?: "Verified IPA unavailable for this entry",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    if (result.verifiedAudioUrl != null) {
                                        IconButton(onClick = { onPlayVerifiedAudio(result.verifiedAudioUrl) }) {
                                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play verified human audio", tint = EmeraldSuccess)
                                        }
                                    } else {
                                        IconButton(onClick = { onSpeakTts(result.word, LanguageCatalog.byCode(result.sourceLang).bcp47Tag) }) {
                                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak with device TTS", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(onClick = { onSaveWord(false) }) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save word to vocabulary")
                                    }
                                }
                            }

                            if (result.isFromOfflineCache) {
                                Spacer(Modifier.height(4.dp))
                                StatusPillBadge(label = "SERVED FROM OFFLINE CACHE", color = AmberWarning)
                            }

                            if (result.hasVerifiedEntry) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verified Lexicon Data (${result.verifiedPartOfSpeech})", fontWeight = FontWeight.Bold)
                                }
                                result.verifiedDefinitions.forEachIndexed { idx, def ->
                                    Text("${idx + 1}. $def", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (result.verifiedExamples.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Examples:", style = MaterialTheme.typography.labelMedium)
                                    result.verifiedExamples.forEach { ex ->
                                        Text("“$ex”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (result.verifiedSynonyms.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Synonyms: ${result.verifiedSynonyms.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (result.hasAiExplanation) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(10.dp))
                                StatusPillBadge(label = "AI-GENERATED CONTEXTUAL USAGE", color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(6.dp))
                                if (result.aiTranslation.isNotBlank()) {
                                    Text("Translation: ${result.aiTranslation}", fontWeight = FontWeight.SemiBold)
                                }
                                if (result.aiContextualMeaning.isNotBlank()) {
                                    Text(result.aiContextualMeaning, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (result.aiFormalVsInformal.isNotBlank()) {
                                    Text("Register: ${result.aiFormalVsInformal}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.aiCollocations.isNotEmpty()) {
                                    Text("Collocations: ${result.aiCollocations.joinToString(" • ")}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.aiVerbConjugations.isNotEmpty()) {
                                    Text("Conjugation: ${result.aiVerbConjugations.joinToString(" | ")}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.aiNounGenderAndPlural.isNotBlank()) {
                                    Text("Grammar: ${result.aiNounGenderAndPlural}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.aiEtymologyNote.isNotBlank()) {
                                    Text("Etymology: ${result.aiEtymologyNote}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (recentCached.isNotEmpty()) {
                        FuturisticCard {
                            Text("Offline Cached Lookups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recentCached.take(10).forEach { c ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            query = c.word
                                            sourceLang = c.sourceLanguage
                                            targetLang = c.targetLanguage
                                            onSearch(c.word, c.sourceLanguage, c.targetLanguage)
                                        },
                                        label = { Text("${c.word} (${c.sourceLanguage}→${c.targetLanguage})") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (savedVocabulary.isEmpty()) {
                        FuturisticCard { Text("Save words from Dictionary searches or AI Conversation summaries to build your offline personal vocabulary.") }
                    } else {
                        savedVocabulary.forEach { item ->
                            FuturisticCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${item.word} → ${item.translation}", fontWeight = FontWeight.Bold)
                                        Text(item.definition, style = MaterialTheme.typography.bodySmall)
                                        Text("Mastery: ${item.masteryLevel}/5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onDeleteVocab(item.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove word", tint = RoseError)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                var cardIdx by rememberSaveable { mutableIntStateOf(0) }
                var revealed by rememberSaveable { mutableStateOf(false) }
                var spellingInput by rememberSaveable { mutableStateOf("") }
                var spellingChecked by rememberSaveable { mutableStateOf(false) }

                if (savedVocabulary.isEmpty()) {
                    FuturisticCard { Text("Add at least one saved vocabulary word to practice Flashcards & Spelling.") }
                } else {
                    val safeIdx = cardIdx % savedVocabulary.size
                    val card = savedVocabulary[safeIdx]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FuturisticCard(accentBorder = true) {
                            Text("Flashcard ${safeIdx + 1} of ${savedVocabulary.size}", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            Text("Prompt Meaning: ${card.translation}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (revealed) {
                                Spacer(Modifier.height(10.dp))
                                Text("Target Term: ${card.word}", style = MaterialTheme.typography.titleLarge, color = EmeraldSuccess)
                                if (card.exampleSentence.isNotBlank()) {
                                    Text("Example: ${card.exampleSentence}", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            onReviewFlashcard(card, false)
                                            revealed = false
                                            cardIdx += 1
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Needs Review")
                                    }
                                    Button(
                                        onClick = {
                                            onReviewFlashcard(card, true)
                                            revealed = false
                                            cardIdx += 1
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Remembered Well")
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Reveal Flashcard")
                                }
                            }
                        }

                        FuturisticCard {
                            Text("Spelling Drill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Type the exact word in ${LanguageCatalog.byCode(card.sourceLanguage).englishName} for: \"${card.translation}\"", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = spellingInput,
                                onValueChange = {
                                    spellingInput = it
                                    spellingChecked = false
                                },
                                label = { Text("Your Spelling") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { spellingChecked = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Check Spelling")
                            }
                            if (spellingChecked) {
                                val exact = spellingInput.trim().equals(card.word.trim(), ignoreCase = true)
                                Spacer(Modifier.height(6.dp))
                                StatusPillBadge(
                                    label = if (exact) "EXACT MATCH!" else "EXPECTED: ${card.word}",
                                    color = if (exact) EmeraldSuccess else AmberWarning
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationWorkspaceScreen(
    result: TranslationResultModel?,
    isOnline: Boolean,
    isBusy: Boolean,
    onTranslate: (text: String, sourceLang: String, targetLang: String, tone: String, contextHint: String) -> Unit,
    onSpeak: (text: String, bcp47: String) -> Unit
) {
    var sourceLang by rememberSaveable { mutableStateOf("en") }
    var targetLang by rememberSaveable { mutableStateOf("fr") }
    var tone by rememberSaveable { mutableStateOf("Neutral") }
    var contextHint by rememberSaveable { mutableStateOf("") }
    var inputText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Translation Workspace",
            subtitle = "Context-sensitive translations with register control, transliteration & grammar notes",
            icon = Icons.Default.GTranslate
        )

        FuturisticCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Source (${LanguageCatalog.byCode(sourceLang).englishName})", fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = {
                        val tmp = sourceLang
                        sourceLang = targetLang
                        targetLang = tmp
                    }
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap languages")
                }
                Text("Target (${LanguageCatalog.byCode(targetLang).englishName})", fontWeight = FontWeight.SemiBold)
            }
            LanguageSelectorChips(selectedCode = sourceLang, onSelect = { sourceLang = it })
            Spacer(Modifier.height(8.dp))
            Text("Target Language", style = MaterialTheme.typography.labelMedium)
            LanguageSelectorChips(selectedCode = targetLang, onSelect = { targetLang = it })

            Spacer(Modifier.height(10.dp))
            Text("Register / Tone", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Neutral", "Formal", "Casual", "Academic", "Business").forEach { t ->
                    FilterChip(selected = tone == t, onClick = { tone = t }, label = { Text(t) })
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = contextHint,
                onValueChange = { contextHint = it },
                label = { Text("Optional Context (e.g., polite email to professor)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text to Translate") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("translation_input")
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onTranslate(inputText, sourceLang, targetLang, tone, contextHint) },
                enabled = inputText.isNotBlank() && !isBusy && isOnline,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("translation_submit_button")
            ) {
                Text(if (!isOnline) "Requires Internet Connection" else if (isBusy) "Translating..." else "Translate with Nuance")
            }
        }

        if (result != null) {
            FuturisticCard(accentBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPillBadge(label = "${result.tone.uppercase()} REGISTER", color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onSpeak(result.primaryTranslation, LanguageCatalog.byCode(targetLang).bcp47Tag) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.cd_play_audio))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(result.primaryTranslation, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (result.transliteration.isNotBlank()) {
                    Text("Reading / Transliteration: ${result.transliteration}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                if (result.alternativeTranslations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Alternatives:", fontWeight = FontWeight.SemiBold)
                    result.alternativeTranslations.forEach { alt -> Text("• $alt", style = MaterialTheme.typography.bodySmall) }
                }
                if (result.idiomaticNotes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Idiomatic & Cultural Note: ${result.idiomaticNotes}", style = MaterialTheme.typography.bodySmall)
                }
                if (result.grammarExplanation.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Grammar Structure: ${result.grammarExplanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PronunciationCoachScreen(
    feedback: PronunciationFeedbackModel?,
    isListening: Boolean,
    isBusy: Boolean,
    defaultLangCode: String,
    onSpeakReference: (phrase: String, bcp47Tag: String) -> Unit,
    onCaptureSpeech: (bcp47Tag: String, onRecognized: (String) -> Unit) -> Unit,
    onEvaluate: (langCode: String, targetPhrase: String, spokenTranscript: String) -> Unit
) {
    val context = LocalContext.current
    var langCode by rememberSaveable { mutableStateOf(defaultLangCode) }
    val langInfo = LanguageCatalog.byCode(langCode)
    var targetPhrase by rememberSaveable(langCode) {
        mutableStateOf(
            when (langCode) {
                "fr" -> "Je voudrais réserver une table pour deux personnes ce soir."
                "es" -> "Me gustaría aprender más sobre la cultura local."
                "de" -> "Könnten Sie das bitte etwas langsamer wiederholen?"
                "ja" -> "お会いできてとても嬉しいです。"
                else -> "Language connects every corner of our shared world."
            }
        )
    }
    var spokenTranscript by rememberSaveable { mutableStateOf("") }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onCaptureSpeech(langInfo.bcp47Tag) { recognized ->
                spokenTranscript = recognized
                onEvaluate(langCode, targetPhrase, recognized)
            }
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
            title = "Pronunciation Coach",
            subtitle = "Listen, record your voice & receive word-level acoustic intelligibility feedback",
            icon = Icons.Default.RecordVoiceOver
        )

        FuturisticCard {
            Text("Target Language", style = MaterialTheme.typography.labelLarge)
            LanguageSelectorChips(selectedCode = langCode, onSelect = { langCode = it })

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = targetPhrase,
                onValueChange = { targetPhrase = it },
                label = { Text("Target Phrase to Practice") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onSpeakReference(targetPhrase, langInfo.bcp47Tag) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Listen Reference")
                }

                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onCaptureSpeech(langInfo.bcp47Tag) { recognized ->
                                spokenTranscript = recognized
                                onEvaluate(langCode, targetPhrase, recognized)
                            }
                        } else {
                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronunciation_record_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isListening) "Listening..." else "Speak Now")
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = spokenTranscript,
                onValueChange = { spokenTranscript = it },
                label = { Text("Recognized / Spoken Transcript (or paste transcript to compare)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onEvaluate(langCode, targetPhrase, spokenTranscript) },
                enabled = spokenTranscript.isNotBlank() && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyze Pronunciation Match")
            }
        }

        if (feedback != null) {
            FuturisticCard(accentBorder = true) {
                StatusPillBadge(
                    label = "INTELLIGIBILITY ESTIMATE: ${feedback.accuracyEstimatePercent}%",
                    color = if (feedback.accuracyEstimatePercent >= 75) EmeraldSuccess else AmberWarning
                )
                Spacer(Modifier.height(8.dp))
                Text("Transcribed: \"${feedback.spokenTranscript}\"", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Articulation: ${feedback.soundFeedback}", style = MaterialTheme.typography.bodyMedium)
                Text("Stress & Rhythm: ${feedback.stressAndRhythmFeedback}", style = MaterialTheme.typography.bodySmall)
                Text("Coaching Tip: ${feedback.intelligibilityAdvice}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        FuturisticCard {
            Text(
                text = stringResource(R.string.disclaimer_pronunciation_estimate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
