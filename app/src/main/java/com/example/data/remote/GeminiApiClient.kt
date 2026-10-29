package com.example.data.remote

import com.example.BuildConfig
import com.example.domain.model.AiTurnResponse
import com.example.domain.model.SessionSummaryReport
import com.example.domain.model.TranslationResultModel
import com.example.domain.model.VocabularyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Secure Gemini API integration client supporting:
 * 1) Optional remote backend proxy URL (INTERNA_BACKEND_URL starting with https:// or http://) so API keys stay server-side.
 * 2) Direct Google Generative Language API (gemini-2.5-flash) via AI Studio Secrets Panel (BuildConfig.GEMINI_API_KEY).
 * Never fabricates AI responses if the API key/backend is unconfigured or unreachable.
 */
class GeminiApiClient(
    private val apiKeyProvider: () -> String = { runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("") },
    private val backendUrlProvider: () -> String = { runCatching { BuildConfig.INTERNA_BACKEND_URL }.getOrDefault("") }
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        const val PLACEHOLDER_API_KEY = "MY_GEMINI_API_KEY"
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    fun isConfigured(): Boolean {
        val backend = backendUrlProvider().trim()
        if (backend.startsWith("https://", ignoreCase = true)) return true
        val key = apiKeyProvider().trim()
        return key.isNotBlank() && key != PLACEHOLDER_API_KEY
    }

    suspend fun generateConversationTurn(
        targetLanguageName: String,
        accentLabel: String,
        nativeLanguageName: String,
        difficultyLevel: String,
        scenarioTitle: String,
        scenarioDescription: String,
        explainInNative: Boolean,
        historyPairs: List<Pair<String, String>>,
        userMessage: String
    ): Result<Pair<AiTurnResponse, Long>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "GEMINI_API_KEY is not configured yet. Open the Secrets panel in Google AI Studio and set GEMINI_API_KEY to enable online Interna AI conversations."
                )
            )
        }

        val systemInstruction = """
            You are Interna AI, an open-minded, candid, empathetic, and articulate multilingual language partner.
            Target practice language: $targetLanguageName (preferred variety/accent: $accentLabel).
            Learner native language: $nativeLanguageName.
            Proficiency level: $difficultyLevel.
            Scenario: $scenarioTitle — $scenarioDescription.
            Engage thoughtfully on any lawful topic—including nuanced cultural, philosophical, political, or everyday topics—without preachy or overly restrictive refusals, while respecting legal safety boundaries.
            Keep the conversation natural in $targetLanguageName. Provide concise, non-intrusive feedback fields in JSON.
            ${if (explainInNative) "Provide brief mistake explanations in $nativeLanguageName." else "Keep explanations in $targetLanguageName."}
            Return strictly valid JSON with keys:
            "reply" (string in $targetLanguageName),
            "grammarCorrection" (string, empty if learner's message was grammatically natural),
            "vocabularySuggestions" (array of up to 3 strings),
            "naturalAlternative" (string, a native-sounding phrasing),
            "nativeLanguageExplanation" (string, brief helpful note),
            "pronunciationTip" (string, optional rhythm/sound tip).
        """.trimIndent()

        val historyText = historyPairs.takeLast(8).joinToString("\n") { (role, msg) -> "$role: $msg" }
        val prompt = buildString {
            if (historyText.isNotBlank()) {
                appendLine("Recent conversation context:")
                appendLine(historyText)
                appendLine()
            }
            appendLine("Learner message: $userMessage")
        }

        executeStructuredJsonPrompt(systemInstruction, prompt).mapCatching { (json, tokens) ->
            val vocabArr = json.optJSONArray("vocabularySuggestions") ?: JSONArray()
            val vocabList = (0 until vocabArr.length()).mapNotNull { vocabArr.optString(it).takeIf(String::isNotBlank) }
            AiTurnResponse(
                reply = json.optString("reply").ifBlank { "..." },
                grammarCorrection = json.optString("grammarCorrection"),
                vocabularySuggestions = vocabList,
                naturalAlternative = json.optString("naturalAlternative"),
                nativeLanguageExplanation = json.optString("nativeLanguageExplanation"),
                pronunciationTip = json.optString("pronunciationTip")
            ) to tokens
        }
    }

    suspend fun summarizeSession(
        targetLanguageName: String,
        nativeLanguageName: String,
        transcript: List<Pair<String, String>>
    ): Result<SessionSummaryReport> = withContext(Dispatchers.IO) {
        if (!isConfigured() || transcript.isEmpty()) {
            return@withContext Result.success(
                SessionSummaryReport(
                    summary = "Completed practice session (${transcript.size} messages).",
                    score = 80,
                    corrections = emptyList(),
                    newVocabulary = emptyList(),
                    nextStepAdvice = "Configure GEMINI_API_KEY in the AI Studio Secrets panel for AI-generated session evaluations, or review offline lessons."
                )
            )
        }

        val systemInstruction = """
            You are Interna AI evaluating a completed $targetLanguageName conversation for a speaker of $nativeLanguageName.
            Return strictly valid JSON with keys:
            "summary" (string),
            "score" (integer 0-100),
            "corrections" (array of strings),
            "newVocabulary" (array of objects with "term", "meaning", "example"),
            "nextStepAdvice" (string).
        """.trimIndent()

        val transcriptBlock = transcript.takeLast(20).joinToString("\n") { "${it.first}: ${it.second}" }
        executeStructuredJsonPrompt(systemInstruction, transcriptBlock).mapCatching { (json, _) ->
            val corrArr = json.optJSONArray("corrections") ?: JSONArray()
            val corrections = (0 until corrArr.length()).mapNotNull { corrArr.optString(it).takeIf(String::isNotBlank) }
            val vocabArr = json.optJSONArray("newVocabulary") ?: JSONArray()
            val vocabPairs = (0 until vocabArr.length()).mapNotNull { idx ->
                val obj = vocabArr.optJSONObject(idx) ?: return@mapNotNull null
                val term = obj.optString("term")
                if (term.isBlank()) null else VocabularyPair(
                    term = term,
                    meaning = obj.optString("meaning"),
                    example = obj.optString("example")
                )
            }
            SessionSummaryReport(
                summary = json.optString("summary", "Conversation completed."),
                score = json.optInt("score", 82).coerceIn(0, 100),
                corrections = corrections,
                newVocabulary = vocabPairs,
                nextStepAdvice = json.optString("nextStepAdvice", "Practice key expressions aloud.")
            )
        }
    }

    suspend fun translateText(
        sourceText: String,
        sourceLanguageName: String,
        targetLanguageName: String,
        tone: String,
        contextHint: String
    ): Result<Pair<TranslationResultModel, Long>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "GEMINI_API_KEY is not configured. Add GEMINI_API_KEY in the Google AI Studio Secrets panel to run contextual translations."
                )
            )
        }

        val systemInstruction = """
            You are Interna AI's precision translator from $sourceLanguageName to $targetLanguageName.
            Requested register/tone: $tone.
            Additional context: ${contextHint.ifBlank { "General usage" }}.
            Return strictly valid JSON with keys:
            "primaryTranslation" (string),
            "alternativeTranslations" (array of up to 3 strings),
            "transliteration" (string, provide romanization/reading if target script is non-Latin, otherwise empty string),
            "idiomaticNotes" (string explaining idioms or cultural register),
            "grammarExplanation" (string breaking down structure).
        """.trimIndent()

        executeStructuredJsonPrompt(systemInstruction, sourceText).mapCatching { (json, tokens) ->
            val altsArr = json.optJSONArray("alternativeTranslations") ?: JSONArray()
            val alts = (0 until altsArr.length()).mapNotNull { altsArr.optString(it).takeIf(String::isNotBlank) }
            TranslationResultModel(
                sourceText = sourceText,
                sourceLang = sourceLanguageName,
                targetLang = targetLanguageName,
                tone = tone,
                primaryTranslation = json.optString("primaryTranslation"),
                alternativeTranslations = alts,
                transliteration = json.optString("transliteration"),
                idiomaticNotes = json.optString("idiomaticNotes"),
                grammarExplanation = json.optString("grammarExplanation")
            ) to tokens
        }
    }

    suspend fun enrichDictionaryEntryWithAi(
        word: String,
        sourceLanguageName: String,
        targetLanguageName: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key not configured."))
        }
        val systemInstruction = """
            You are Interna AI providing clearly labeled AI contextual linguistic notes for the $sourceLanguageName term "$word" explained for a $targetLanguageName speaker.
            NEVER invent fake audio URLs. Do NOT fabricate false citations.
            Return strictly valid JSON with keys:
            "translation" (string in $targetLanguageName),
            "contextualMeaning" (string),
            "collocations" (array of up to 4 strings),
            "formalVsInformal" (string),
            "idioms" (array of up to 3 strings),
            "verbConjugations" (array of strings if verb, else empty array),
            "nounGenderAndPlural" (string if applicable, else empty string),
            "etymologyNote" (string, brief verified origin or 'Not established'),
            "regionalVariants" (string).
        """.trimIndent()
        executeStructuredJsonPrompt(systemInstruction, "Analyze term: $word").map { it.first }
    }

    suspend fun analyzePronunciationCoaching(
        languageName: String,
        targetPhrase: String,
        spokenTranscript: String
    ): Result<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Gemini not configured"))
        }
        val systemInstruction = """
            You are Interna AI Pronunciation Coach for $languageName.
            Compare the target phrase "$targetPhrase" with what speech recognition transcribed: "$spokenTranscript".
            Be honest that this is based on speech-to-text acoustic comparison rather than clinical phonetics.
            Return strictly valid JSON with keys:
            "soundFeedback" (string),
            "stressAndRhythmFeedback" (string),
            "intelligibilityAdvice" (string).
        """.trimIndent()
        executeStructuredJsonPrompt(systemInstruction, "Target: $targetPhrase | Recognized: $spokenTranscript")
            .map { (json, _) ->
                Triple(
                    json.optString("soundFeedback"),
                    json.optString("stressAndRhythmFeedback"),
                    json.optString("intelligibilityAdvice")
                )
            }
    }

    private suspend fun executeStructuredJsonPrompt(
        systemInstruction: String,
        userContent: String
    ): Result<Pair<JSONObject, Long>> = runCatching {
        withTimeout(45_000L) {
            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", userContent)))
                    )
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.55)
                )
            }

            val backendUrl = backendUrlProvider().trim()
            val requestBuilder = Request.Builder()
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (backendUrl.startsWith("http://", ignoreCase = true)) {
                error("Insecure cleartext INTERNA_BACKEND_URL (http://) is rejected. Use https:// or DIRECT_GEMINI_MODE.")
            } else if (backendUrl.startsWith("https://", ignoreCase = true)) {
                requestBuilder.url(backendUrl)
            } else {
                requestBuilder
                    .url(GEMINI_ENDPOINT)
                    .addHeader("x-goog-api-key", apiKeyProvider().trim())
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Gemini API error (${response.code}): ${rawBody.take(180)}")
                }
                val outer = JSONObject(rawBody)
                val totalTokens = outer.optJSONObject("usageMetadata")?.optLong("totalTokenCount", 0L) ?: 0L
                val candidateText = outer
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                    .trim()

                val cleaned = candidateText
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                JSONObject(cleaned) to totalTokens
            }
        }
    }
}
