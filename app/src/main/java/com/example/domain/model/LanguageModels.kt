package com.example.domain.model

data class LanguageInfo(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val bcp47Tag: String,
    val accents: List<AccentOption>,
    val usesNonLatinScript: Boolean = false
)

data class AccentOption(
    val label: String,
    val bcp47Tag: String
)

object LanguageCatalog {
    val languages: List<LanguageInfo> = listOf(
        LanguageInfo(
            code = "en",
            englishName = "English",
            nativeName = "English",
            bcp47Tag = "en-US",
            accents = listOf(
                AccentOption("US Standard", "en-US"),
                AccentOption("UK British", "en-GB"),
                AccentOption("Australian", "en-AU")
            )
        ),
        LanguageInfo(
            code = "fr",
            englishName = "French",
            nativeName = "Français",
            bcp47Tag = "fr-FR",
            accents = listOf(
                AccentOption("France (Paris)", "fr-FR"),
                AccentOption("Canada (Québec)", "fr-CA")
            )
        ),
        LanguageInfo(
            code = "es",
            englishName = "Spanish",
            nativeName = "Español",
            bcp47Tag = "es-ES",
            accents = listOf(
                AccentOption("Spain (Castilian)", "es-ES"),
                AccentOption("Latin America (Mexico)", "es-MX"),
                AccentOption("Argentina", "es-AR")
            )
        ),
        LanguageInfo(
            code = "de",
            englishName = "German",
            nativeName = "Deutsch",
            bcp47Tag = "de-DE",
            accents = listOf(
                AccentOption("Germany (Hochdeutsch)", "de-DE"),
                AccentOption("Austria", "de-AT")
            )
        ),
        LanguageInfo(
            code = "ja",
            englishName = "Japanese",
            nativeName = "日本語",
            bcp47Tag = "ja-JP",
            accents = listOf(AccentOption("Standard Tokyo", "ja-JP")),
            usesNonLatinScript = true
        ),
        LanguageInfo(
            code = "zh",
            englishName = "Mandarin Chinese",
            nativeName = "中文 (普通话)",
            bcp47Tag = "zh-CN",
            accents = listOf(
                AccentOption("Mainland Putonghua", "zh-CN"),
                AccentOption("Taiwanese Mandarin", "zh-TW")
            ),
            usesNonLatinScript = true
        ),
        LanguageInfo(
            code = "ar",
            englishName = "Arabic",
            nativeName = "العربية",
            bcp47Tag = "ar-SA",
            accents = listOf(
                AccentOption("Modern Standard Arabic", "ar-SA"),
                AccentOption("Egyptian", "ar-EG")
            ),
            usesNonLatinScript = true
        ),
        LanguageInfo(
            code = "it",
            englishName = "Italian",
            nativeName = "Italiano",
            bcp47Tag = "it-IT",
            accents = listOf(AccentOption("Standard Italian", "it-IT"))
        ),
        LanguageInfo(
            code = "pt",
            englishName = "Portuguese",
            nativeName = "Português",
            bcp47Tag = "pt-BR",
            accents = listOf(
                AccentOption("Brazil", "pt-BR"),
                AccentOption("Portugal", "pt-PT")
            )
        ),
        LanguageInfo(
            code = "ko",
            englishName = "Korean",
            nativeName = "한국어",
            bcp47Tag = "ko-KR",
            accents = listOf(AccentOption("Seoul Standard", "ko-KR")),
            usesNonLatinScript = true
        ),
        LanguageInfo(
            code = "ru",
            englishName = "Russian",
            nativeName = "Русский",
            bcp47Tag = "ru-RU",
            accents = listOf(AccentOption("Standard Russian", "ru-RU")),
            usesNonLatinScript = true
        ),
        LanguageInfo(
            code = "mg",
            englishName = "Malagasy",
            nativeName = "Malagasy",
            bcp47Tag = "mg-MG",
            accents = listOf(AccentOption("Official Plateau", "mg-MG"))
        )
    )

    val proficiencyLevels = listOf(
        "Beginner",
        "Elementary",
        "Intermediate",
        "Upper-Intermediate",
        "Advanced",
        "Fluent Practice"
    )

    val learningObjectives = listOf(
        "Conversational Fluency",
        "Career & International Business",
        "Global Travel & Culture",
        "Academic Study & Reading",
        "Heritage & Family Connection"
    )

    val roleplayScenarios = listOf(
        RoleplayScenario("free_talk", "Open Candid Conversation", "Discuss any topic, ideas, culture, news, or philosophy naturally.", false),
        RoleplayScenario("daily_life", "Daily Life & Neighborhood", "Casual chat with neighbors, errands, routines, and hobbies.", false),
        RoleplayScenario("restaurant", "Restaurant & Café", "Order food, ask about ingredients, dietary needs, and pay the bill.", false),
        RoleplayScenario("airport", "Airport & Immigration", "Check-in, boarding gates, customs questions, and lost luggage.", false),
        RoleplayScenario("hotel", "Hotel Check-In & Concierge", "Reservations, room requests, local recommendations, and checkout.", false),
        RoleplayScenario("shopping", "Shopping & Markets", "Compare sizes, negotiate politely, ask about returns and prices.", false),
        RoleplayScenario("friendship", "Making Friends & Socializing", "Introduce yourself, share stories, humor, and weekend plans.", false),
        RoleplayScenario("travel", "Travel & Directions", "Navigate trains, emergencies, sightseeing, and cultural etiquette.", true),
        RoleplayScenario("interview", "Job Interview Coaching", "Answer behavioral and technical questions with professional polish.", true),
        RoleplayScenario("business", "Executive Business Meeting", "Lead negotiations, pitch proposals, and align cross-border teams.", true)
    )

    fun byCode(code: String): LanguageInfo =
        languages.find { it.code.equals(code, ignoreCase = true) } ?: languages.first()
}

data class RoleplayScenario(
    val id: String,
    val title: String,
    val description: String,
    val isPremiumRecommended: Boolean
)

enum class UserRole { USER, ADMIN }
enum class AccountStatus { ACTIVE, SUSPENDED, DELETED }
enum class AccessPlanType { FREE_OFFLINE, PREMIUM_ONLINE }
enum class SubscriptionStatus { NONE, PENDING, APPROVED, REJECTED, EXPIRED, SUSPENDED }

data class AiTurnResponse(
    val reply: String,
    val grammarCorrection: String = "",
    val vocabularySuggestions: List<String> = emptyList(),
    val naturalAlternative: String = "",
    val nativeLanguageExplanation: String = "",
    val pronunciationTip: String = ""
)

data class SessionSummaryReport(
    val summary: String,
    val score: Int,
    val corrections: List<String>,
    val newVocabulary: List<VocabularyPair>,
    val nextStepAdvice: String
)

data class VocabularyPair(
    val term: String,
    val meaning: String,
    val example: String
)

data class DictionaryResultModel(
    val word: String,
    val sourceLang: String,
    val targetLang: String,
    // Verified dictionary API data (dictionaryapi.dev)
    val hasVerifiedEntry: Boolean,
    val verifiedPartOfSpeech: String,
    val verifiedDefinitions: List<String>,
    val verifiedIpa: String?, // Null when unavailable — NEVER invented
    val verifiedAudioUrl: String?, // Null when unavailable — NEVER faked
    val verifiedExamples: List<String>,
    val verifiedSynonyms: List<String>,
    val verifiedAntonyms: List<String>,
    // Clearly distinguished AI-generated contextual explanation
    val hasAiExplanation: Boolean,
    val aiTranslation: String,
    val aiContextualMeaning: String,
    val aiCollocations: List<String>,
    val aiFormalVsInformal: String,
    val aiIdioms: List<String>,
    val aiVerbConjugations: List<String>,
    val aiNounGenderAndPlural: String,
    val aiEtymologyNote: String,
    val aiRegionalVariants: String,
    val isFromOfflineCache: Boolean = false
)

data class TranslationResultModel(
    val sourceText: String,
    val sourceLang: String,
    val targetLang: String,
    val tone: String,
    val primaryTranslation: String,
    val alternativeTranslations: List<String>,
    val transliteration: String,
    val idiomaticNotes: String,
    val grammarExplanation: String
)

data class PronunciationFeedbackModel(
    val targetPhrase: String,
    val spokenTranscript: String,
    val accuracyEstimatePercent: Int,
    val matchedWords: List<Pair<String, Boolean>>,
    val soundFeedback: String,
    val stressAndRhythmFeedback: String,
    val intelligibilityAdvice: String,
    val isAiEnhanced: Boolean
)
