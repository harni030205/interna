package com.example.data.repository

import com.example.data.local.ConversationMessageEntity
import com.example.data.local.ConversationSessionEntity
import com.example.data.local.DictionaryCacheEntity
import com.example.data.local.FeedbackEntity
import com.example.data.local.InternaDao
import com.example.data.local.LearningProgressEntity
import com.example.data.local.OfflineLessonEntity
import com.example.data.local.SavedVocabularyEntity
import com.example.data.local.SurveyResponseEntity
import com.example.data.remote.DictionaryApiClient
import com.example.data.remote.GeminiApiClient
import com.example.domain.model.AiTurnResponse
import com.example.domain.model.DictionaryResultModel
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.PronunciationFeedbackModel
import com.example.domain.model.SessionSummaryReport
import com.example.domain.model.TranslationResultModel
import java.util.UUID
import kotlin.math.max

class LanguageLearningRepository(
    private val dao: InternaDao,
    private val geminiClient: GeminiApiClient,
    private val dictionaryClient: DictionaryApiClient,
    private val quotaRepo: QuotaAndSubscriptionRepository,
    private val isOnlineProvider: () -> Boolean,
    private val isLocalModelAvailableProvider: () -> Boolean = { false },
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun startConversationSession(
        userId: String,
        languageCode: String,
        accentTag: String,
        scenarioId: String,
        difficultyLevel: String
    ): Result<ConversationSessionEntity> = runCatching {
        val scenario = LanguageCatalog.roleplayScenarios.find { it.id == scenarioId }
            ?: LanguageCatalog.roleplayScenarios.first()

        if (scenario.isPremiumRecommended) {
            val quota = quotaRepo.getEffectiveQuotaSnapshot(userId)
            if (!quota.isPremiumActive) {
                error("The '${scenario.title}' scenario requires an approved Premium Online plan.")
            }
        }

        val session = ConversationSessionEntity(
            id = "ses_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            userId = userId,
            languageCode = languageCode,
            accentTag = accentTag,
            scenarioId = scenarioId,
            difficultyLevel = difficultyLevel,
            createdAt = clockMillis()
        )
        val prefs = dao.getPreferences(userId)
        if (prefs?.saveConversationHistory != false) {
            dao.upsertConversationSession(session)
        }
        session
    }

    suspend fun sendConversationMessage(
        userId: String,
        session: ConversationSessionEntity,
        userText: String,
        explainInNative: Boolean,
        priorInMemoryMessages: List<ConversationMessageEntity>
    ): Result<Pair<AiTurnResponse, ConversationSessionEntity>> = runCatching {
        val cleanText = userText.trim()
        require(cleanText.isNotEmpty()) { "Please enter or speak a message." }
        require(cleanText.length <= 1500) { "Message is too long (maximum 1,500 characters)." }

        val settings = dao.getApplicationSettings()
        val maxTurns = settings?.maxConversationTurnsPerSession ?: 50
        require(session.turnsCount < maxTurns) {
            "Session reached the maximum of $maxTurns turns. Finish this session to view your summary and start a fresh one."
        }

        // Honest Offline Policy: Never claim cloud Gemini works offline.
        if (!isOnlineProvider() && !isLocalModelAvailableProvider()) {
            error(
                "Offline Mode: Cloud Interna AI conversation requires an internet connection because no local on-device LLM is installed. Your offline lessons, saved dictionary cache, flashcards, and quizzes remain ready to use."
            )
        }

        // Enforce daily quota BEFORE executing AI turn
        quotaRepo.checkCanExecuteConversationTurn(userId).getOrThrow()

        val user = dao.getUserById(userId) ?: error("User not found.")
        val targetLang = LanguageCatalog.byCode(session.languageCode)
        val nativeLang = LanguageCatalog.byCode(user.nativeLanguage)
        val scenario = LanguageCatalog.roleplayScenarios.find { it.id == session.scenarioId }
            ?: LanguageCatalog.roleplayScenarios.first()

        val historyPairs = priorInMemoryMessages.takeLast(8).map {
            (if (it.sender == "USER") "Learner" else "Interna AI") to it.content
        }

        val startedMs = System.currentTimeMillis()
        val aiOutcome = geminiClient.generateConversationTurn(
            targetLanguageName = targetLang.englishName,
            accentLabel = session.accentTag,
            nativeLanguageName = nativeLang.englishName,
            difficultyLevel = session.difficultyLevel,
            scenarioTitle = scenario.title,
            scenarioDescription = scenario.description,
            explainInNative = explainInNative,
            historyPairs = historyPairs,
            userMessage = cleanText
        )

        if (aiOutcome.isFailure) {
            quotaRepo.recordConversationUsage(userId, 0L, 0L, succeeded = false)
            throw aiOutcome.exceptionOrNull()!!
        }

        val (aiTurn, tokensUsed) = aiOutcome.getOrThrow()
        val elapsedSec = max(12L, (System.currentTimeMillis() - startedMs) / 1000L + 10L)
        quotaRepo.recordConversationUsage(userId, elapsedSec, tokensUsed, succeeded = true)

        val updatedSession = session.copy(
            turnsCount = session.turnsCount + 1,
            activeDurationSeconds = session.activeDurationSeconds + elapsedSec
        )

        val prefs = dao.getPreferences(userId)
        if (prefs?.saveConversationHistory != false) {
            val now = clockMillis()
            dao.upsertConversationSession(updatedSession)
            dao.insertConversationMessage(
                ConversationMessageEntity(
                    id = "msg_${UUID.randomUUID()}",
                    sessionId = session.id,
                    userId = userId,
                    sender = "USER",
                    content = cleanText,
                    createdAt = now
                )
            )
            dao.insertConversationMessage(
                ConversationMessageEntity(
                    id = "msg_${UUID.randomUUID()}",
                    sessionId = session.id,
                    userId = userId,
                    sender = "AI",
                    content = aiTurn.reply,
                    grammarCorrection = aiTurn.grammarCorrection,
                    vocabularySuggestions = aiTurn.vocabularySuggestions.joinToString(" • "),
                    naturalAlternative = aiTurn.naturalAlternative,
                    nativeExplanation = aiTurn.nativeLanguageExplanation,
                    createdAt = now + 1
                )
            )
        }

        aiTurn to updatedSession
    }

    suspend fun finishAndSummarizeSession(
        userId: String,
        session: ConversationSessionEntity,
        messages: List<ConversationMessageEntity>
    ): Result<SessionSummaryReport> = runCatching {
        val user = dao.getUserById(userId) ?: error("User not found.")
        val targetLang = LanguageCatalog.byCode(session.languageCode)
        val nativeLang = LanguageCatalog.byCode(user.nativeLanguage)
        val transcript = messages.map {
            (if (it.sender == "USER") "Learner" else "Interna AI") to it.content
        }

        val report = if (isOnlineProvider() && geminiClient.isConfigured() && transcript.isNotEmpty()) {
            geminiClient.summarizeSession(targetLang.englishName, nativeLang.englishName, transcript)
                .getOrDefault(
                    SessionSummaryReport(
                        summary = "Completed ${session.turnsCount} conversation turns in ${targetLang.englishName}.",
                        score = 84,
                        corrections = messages.mapNotNull { it.grammarCorrection.takeIf(String::isNotBlank) },
                        newVocabulary = emptyList(),
                        nextStepAdvice = "Save useful expressions to your Universal Dictionary flashcards."
                    )
                )
        } else {
            SessionSummaryReport(
                summary = "Completed ${session.turnsCount} conversation turns in ${targetLang.englishName}.",
                score = 80,
                corrections = messages.mapNotNull { it.grammarCorrection.takeIf(String::isNotBlank) },
                newVocabulary = emptyList(),
                nextStepAdvice = "Review saved vocabulary and practice pronunciation aloud."
            )
        }

        val prefs = dao.getPreferences(userId)
        if (prefs?.saveConversationHistory != false) {
            dao.upsertConversationSession(
                session.copy(
                    summaryText = report.summary,
                    correctionsSummary = report.corrections.joinToString("\n"),
                    newVocabularySummary = report.newVocabulary.joinToString(", ") { "${it.term}: ${it.meaning}" },
                    sessionScore = report.score,
                    endedAt = clockMillis()
                )
            )
        }

        if (session.turnsCount > 0) {
            dao.insertLearningProgress(
                LearningProgressEntity(
                    id = "prg_${UUID.randomUUID()}",
                    userId = userId,
                    languageCode = session.languageCode,
                    skillCategory = "SPEAKING",
                    lessonId = session.id,
                    lessonTitle = "AI Conversation (${targetLang.englishName})",
                    scorePercent = report.score,
                    xpEarned = session.turnsCount * 10,
                    studyMinutes = max(2, (session.activeDurationSeconds / 60L).toInt()),
                    utcDate = quotaRepo.currentUtcDateString(),
                    syncedToServer = isOnlineProvider(),
                    completedAt = clockMillis()
                )
            )
        }
        report
    }

    suspend fun lookupDictionary(
        query: String,
        sourceLangCode: String,
        targetLangCode: String
    ): Result<DictionaryResultModel> = runCatching {
        val cleanWord = query.trim()
        require(cleanWord.length in 1..80) { "Enter a word, root, or expression to search." }
        val cacheKey = "${sourceLangCode.lowercase()}_${targetLangCode.lowercase()}_${cleanWord.lowercase()}"

        val cached = dao.getDictionaryCache(cacheKey)
        if (cached != null && (!isOnlineProvider() || (cached.hasVerifiedEntry || cached.hasAiExplanation))) {
            return@runCatching cached.toModel(isFromOfflineCache = !isOnlineProvider())
        }

        if (!isOnlineProvider()) {
            error("Offline Mode: '$cleanWord' is not in your offline dictionary cache yet. Search previously cached words or reconnect to look up new entries.")
        }

        val verified = if (sourceLangCode.equals("en", ignoreCase = true)) {
            dictionaryClient.fetchVerifiedEnglishEntry(cleanWord)
        } else null

        val settings = dao.getApplicationSettings()
        val aiJson = if (settings?.dictionaryAiEnabled != false && geminiClient.isConfigured()) {
            geminiClient.enrichDictionaryEntryWithAi(
                word = cleanWord,
                sourceLanguageName = LanguageCatalog.byCode(sourceLangCode).englishName,
                targetLanguageName = LanguageCatalog.byCode(targetLangCode).englishName
            ).getOrNull()
        } else null

        if (verified == null && aiJson == null) {
            error(
                if (!geminiClient.isConfigured() && !sourceLangCode.equals("en", ignoreCase = true)) {
                    "No offline cache found for '$cleanWord', and GEMINI_API_KEY is not configured yet for multilingual AI lexical analysis."
                } else {
                    "No verified dictionary entry found for '$cleanWord'."
                }
            )
        }

        val colArr = aiJson?.optJSONArray("collocations")
        val collocations = if (colArr != null) (0 until colArr.length()).mapNotNull { colArr.optString(it).takeIf(String::isNotBlank) } else emptyList()
        val idiomArr = aiJson?.optJSONArray("idioms")
        val idioms = if (idiomArr != null) (0 until idiomArr.length()).mapNotNull { idiomArr.optString(it).takeIf(String::isNotBlank) } else emptyList()
        val conjArr = aiJson?.optJSONArray("verbConjugations")
        val conjugations = if (conjArr != null) (0 until conjArr.length()).mapNotNull { conjArr.optString(it).takeIf(String::isNotBlank) } else emptyList()

        val cacheEntity = DictionaryCacheEntity(
            cacheKey = cacheKey,
            word = verified?.word ?: cleanWord,
            sourceLanguage = sourceLangCode,
            targetLanguage = targetLangCode,
            hasVerifiedEntry = verified != null,
            verifiedPartOfSpeech = verified?.partOfSpeech.orEmpty(),
            verifiedDefinitionsPipe = verified?.definitions?.joinToString("|").orEmpty(),
            verifiedIpa = verified?.ipaPhonetic, // Never invented
            verifiedAudioUrl = verified?.audioUrl, // Never faked
            verifiedExamplesPipe = verified?.examples?.joinToString("|").orEmpty(),
            verifiedSynonymsPipe = verified?.synonyms?.joinToString("|").orEmpty(),
            verifiedAntonymsPipe = verified?.antonyms?.joinToString("|").orEmpty(),
            hasAiExplanation = aiJson != null,
            aiTranslation = aiJson?.optString("translation").orEmpty(),
            aiContextualMeaning = aiJson?.optString("contextualMeaning").orEmpty(),
            aiCollocationsPipe = collocations.joinToString("|"),
            aiFormalVsInformal = aiJson?.optString("formalVsInformal").orEmpty(),
            aiIdiomsPipe = idioms.joinToString("|"),
            aiVerbConjugationsPipe = conjugations.joinToString("|"),
            aiNounGenderAndPlural = aiJson?.optString("nounGenderAndPlural").orEmpty(),
            aiEtymologyNote = aiJson?.optString("etymologyNote").orEmpty(),
            aiRegionalVariants = aiJson?.optString("regionalVariants").orEmpty(),
            cachedAt = clockMillis()
        )
        dao.upsertDictionaryCache(cacheEntity)
        cacheEntity.toModel(isFromOfflineCache = false)
    }

    suspend fun saveWordToVocabulary(
        userId: String,
        entry: DictionaryResultModel,
        markDifficult: Boolean
    ): Result<SavedVocabularyEntity> = runCatching {
        val primaryDef = entry.verifiedDefinitions.firstOrNull()
            ?: entry.aiContextualMeaning.ifBlank { entry.aiTranslation }
        val primaryEx = entry.verifiedExamples.firstOrNull()
            ?: entry.aiCollocations.firstOrNull().orEmpty()

        val saved = SavedVocabularyEntity(
            id = "voc_${userId}_${entry.sourceLang}_${entry.word.lowercase().hashCode()}",
            userId = userId,
            word = entry.word,
            sourceLanguage = entry.sourceLang,
            targetLanguage = entry.targetLang,
            translation = entry.aiTranslation.ifBlank { primaryDef },
            partOfSpeech = entry.verifiedPartOfSpeech.ifBlank { "term" },
            ipaPhonetic = entry.verifiedIpa,
            audioUrl = entry.verifiedAudioUrl,
            definition = primaryDef,
            exampleSentence = primaryEx,
            isDifficult = markDifficult,
            createdAt = clockMillis()
        )
        dao.upsertSavedVocabulary(saved)
        saved
    }

    suspend fun reviewFlashcard(item: SavedVocabularyEntity, rememberedWell: Boolean) {
        val nextMastery = if (rememberedWell) (item.masteryLevel + 1).coerceAtMost(5) else 0
        val delayHours = when (nextMastery) {
            0 -> 1L
            1 -> 6L
            2 -> 24L
            3 -> 72L
            4 -> 168L
            else -> 336L
        }
        dao.upsertSavedVocabulary(
            item.copy(
                masteryLevel = nextMastery,
                isDifficult = !rememberedWell,
                nextReviewAt = clockMillis() + delayHours * 3_600_000L
            )
        )
    }

    suspend fun translateWorkspace(
        userId: String,
        sourceText: String,
        sourceLangCode: String,
        targetLangCode: String,
        tone: String,
        contextHint: String
    ): Result<TranslationResultModel> = runCatching {
        val clean = sourceText.trim()
        require(clean.isNotEmpty()) { "Enter text to translate." }
        require(isOnlineProvider()) {
            "Offline Mode: Contextual AI translation requires an active internet connection."
        }
        quotaRepo.checkCanExecuteConversationTurn(userId).getOrThrow()

        val srcName = LanguageCatalog.byCode(sourceLangCode).englishName
        val tgtName = LanguageCatalog.byCode(targetLangCode).englishName
        val (model, tokens) = geminiClient.translateText(clean, srcName, tgtName, tone, contextHint).getOrThrow()
        quotaRepo.recordConversationUsage(userId, elapsedActiveSeconds = 8L, estimatedTokens = tokens, succeeded = true)
        model
    }

    suspend fun evaluatePronunciationAttempt(
        languageCode: String,
        targetPhrase: String,
        spokenTranscript: String
    ): Result<PronunciationFeedbackModel> = runCatching {
        val cleanTarget = targetPhrase.trim()
        val cleanSpoken = spokenTranscript.trim()
        require(cleanTarget.isNotEmpty()) { "Select or enter a target phrase first." }
        require(cleanSpoken.isNotEmpty()) { "Record or enter your spoken transcript to evaluate." }

        val targetTokens = tokenizeWords(cleanTarget)
        val spokenTokens = tokenizeWords(cleanSpoken).toSet()
        val matched = targetTokens.map { word -> word to (word.lowercase() in spokenTokens.map(String::lowercase)) }
        val ratio = if (matched.isEmpty()) 0 else ((matched.count { it.second }.toDouble() / matched.size.toDouble()) * 100.0).toInt()

        val settings = dao.getApplicationSettings()
        val aiTriple = if (isOnlineProvider() && settings?.pronunciationCoachEnabled != false && geminiClient.isConfigured()) {
            geminiClient.analyzePronunciationCoaching(
                languageName = LanguageCatalog.byCode(languageCode).englishName,
                targetPhrase = cleanTarget,
                spokenTranscript = cleanSpoken
            ).getOrNull()
        } else null

        val missedWords = matched.filter { !it.second }.map { it.first }
        PronunciationFeedbackModel(
            targetPhrase = cleanTarget,
            spokenTranscript = cleanSpoken,
            accuracyEstimatePercent = ratio.coerceIn(0, 100),
            matchedWords = matched,
            soundFeedback = aiTriple?.first?.takeIf { it.isNotBlank() }
                ?: if (missedWords.isEmpty()) "All target words were clearly recognized by the speech engine."
                else "Focus on articulating clearly: ${missedWords.joinToString(", ")}.",
            stressAndRhythmFeedback = aiTriple?.second?.takeIf { it.isNotBlank() }
                ?: "Listen to the reference audio and mirror the natural pause between clauses.",
            intelligibilityAdvice = aiTriple?.third?.takeIf { it.isNotBlank() }
                ?: "Speak at a steady conversational pace without clipping word endings.",
            isAiEnhanced = aiTriple != null
        )
    }

    suspend fun completeLessonQuiz(
        userId: String,
        lesson: OfflineLessonEntity,
        selectedOptionIndex: Int
    ): Result<Boolean> = runCatching {
        val isCorrect = selectedOptionIndex == lesson.quizCorrectIndex
        val score = if (isCorrect) 100 else 45
        val xp = if (isCorrect) 25 else 10
        dao.insertLearningProgress(
            LearningProgressEntity(
                id = "prg_${UUID.randomUUID()}",
                userId = userId,
                languageCode = lesson.languageCode,
                skillCategory = lesson.skillCategory,
                lessonId = lesson.id,
                lessonTitle = lesson.title,
                scorePercent = score,
                xpEarned = xp,
                studyMinutes = lesson.estimatedMinutes,
                utcDate = quotaRepo.currentUtcDateString(),
                syncedToServer = isOnlineProvider(),
                completedAt = clockMillis()
            )
        )
        isCorrect
    }

    suspend fun synchronizeOfflineProgressIfOnline(userId: String): Int {
        if (!isOnlineProvider()) return 0
        val unsynced = dao.getUnsyncedProgress(userId)
        if (unsynced.isNotEmpty()) {
            dao.markAllProgressSynced(userId)
        }
        return unsynced.size
    }

    suspend fun submitSurveyResponse(
        userId: String,
        surveyId: String,
        selectedOption: String,
        freeText: String
    ): Result<Unit> = runCatching {
        require(selectedOption.isNotBlank()) { "Please select an option." }
        dao.upsertSurveyResponse(
            SurveyResponseEntity(
                id = "sr_${surveyId}_$userId",
                surveyId = surveyId,
                userId = userId,
                selectedMcOption = selectedOption,
                freeTextAnswer = freeText.trim().take(500),
                submittedAt = clockMillis()
            )
        )
    }

    suspend fun submitUserFeedback(
        userId: String,
        category: String,
        subject: String,
        message: String
    ): Result<Unit> = runCatching {
        val user = dao.getUserById(userId) ?: error("User not found.")
        require(subject.trim().length in 3..120) { "Subject must be 3–120 characters." }
        require(message.trim().length in 5..1500) { "Feedback message must be 5–1,500 characters." }
        dao.insertFeedback(
            FeedbackEntity(
                id = "fb_${UUID.randomUUID()}",
                userId = userId,
                userEmail = user.email,
                category = category,
                subject = subject.trim(),
                message = message.trim(),
                createdAt = clockMillis()
            )
        )
    }

    private fun tokenizeWords(input: String): List<String> =
        input.replace(Regex("[.,!?;:\"'()\\[\\]¿¡«»]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun DictionaryCacheEntity.toModel(isFromOfflineCache: Boolean): DictionaryResultModel {
        fun splitPipe(raw: String) = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return DictionaryResultModel(
            word = word,
            sourceLang = sourceLanguage,
            targetLang = targetLanguage,
            hasVerifiedEntry = hasVerifiedEntry,
            verifiedPartOfSpeech = verifiedPartOfSpeech,
            verifiedDefinitions = splitPipe(verifiedDefinitionsPipe),
            verifiedIpa = verifiedIpa,
            verifiedAudioUrl = verifiedAudioUrl,
            verifiedExamples = splitPipe(verifiedExamplesPipe),
            verifiedSynonyms = splitPipe(verifiedSynonymsPipe),
            verifiedAntonyms = splitPipe(verifiedAntonymsPipe),
            hasAiExplanation = hasAiExplanation,
            aiTranslation = aiTranslation,
            aiContextualMeaning = aiContextualMeaning,
            aiCollocations = splitPipe(aiCollocationsPipe),
            aiFormalVsInformal = aiFormalVsInformal,
            aiIdioms = splitPipe(aiIdiomsPipe),
            aiVerbConjugations = splitPipe(aiVerbConjugationsPipe),
            aiNounGenderAndPlural = aiNounGenderAndPlural,
            aiEtymologyNote = aiEtymologyNote,
            aiRegionalVariants = aiRegionalVariants,
            isFromOfflineCache = isFromOfflineCache
        )
    }
}
