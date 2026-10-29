package com.example.data.local

/**
 * Provides genuine offline-ready multilingual curriculum lessons, grammar explanations,
 * reading/listening exercises, speaking prompts, and verified initial dictionary cache items
 * so learners have full offline functionality even without an internet connection.
 */
object SeedContentProvider {

    fun defaultOfflineLessons(): List<OfflineLessonEntity> = listOf(
        // French
        OfflineLessonEntity(
            id = "fr_vocab_01",
            languageCode = "fr",
            level = "Beginner",
            skillCategory = "VOCABULARY",
            title = "Essential Parisian Greetings & Politeness",
            summary = "Master natural greetings, register distinctions (tu vs. vous), and café etiquette.",
            lessonBody = "In French, social interactions almost always begin with 'Bonjour' (daytime) or 'Bonsoir' (evening) before asking a question.\n\n• Bonjour, madame / monsieur — Good day, ma'am / sir\n• S'il vous plaît (formal) / S'il te plaît (informal) — Please\n• Je voudrais un café allongé — I would like an espresso with hot water\n• Merci infiniment — Thank you very much\n• Bonne journée ! — Have a wonderful day!",
            targetPhraseForAudio = "Bonjour monsieur, je voudrais un café allongé, s'il vous plaît.",
            quizQuestion = "Which phrase is most natural and polite when ordering at a bakery in Paris?",
            quizOptionsPipe = "Donne-moi du pain.|Bonjour, je voudrais une baguette, s'il vous plaît.|Salut, une baguette maintenant.|Je veux pain merci.",
            quizCorrectIndex = 1,
            quizExplanation = "'Bonjour, je voudrais...' uses the polite conditional form ('I would like') preceded by a greeting.",
            estimatedMinutes = 8
        ),
        OfflineLessonEntity(
            id = "fr_grammar_02",
            languageCode = "fr",
            level = "Intermediate",
            skillCategory = "GRAMMAR",
            title = "Passé Composé vs. Imparfait in Storytelling",
            summary = "Distinguish completed actions from ongoing background states in past narratives.",
            lessonBody = "French uses two primary past tenses together:\n1. L'imparfait describes background scenes, habits, or ongoing states (Il pleuvait — It was raining).\n2. Le passé composé marks specific, completed events that interrupt or advance the plot (Soudain, le train est arrivé — Suddenly, the train arrived).\n\nExample: Quand j'habitais à Lyon (background), j'ai rencontré mon meilleur ami (specific event).",
            targetPhraseForAudio = "Quand j'habitais à Lyon, j'ai rencontré mon meilleur ami.",
            quizQuestion = "Complete: 'Il ___ beau quand nous ___ sortis.'",
            quizOptionsPipe = "a fait / étions|faisait / sommes|faisait / avons|fait / sommes",
            quizCorrectIndex = 1,
            quizExplanation = "'Il faisait beau' sets the ongoing weather background (imparfait), while 'nous sommes sortis' (sortir takes être) marks the completed action.",
            estimatedMinutes = 12
        ),
        OfflineLessonEntity(
            id = "fr_reading_03",
            languageCode = "fr",
            level = "Upper-Intermediate",
            skillCategory = "READING",
            title = "Reading Comprehension: Le Télétravail Moderne",
            summary = "Analyze nuanced arguments around remote work and urban mobility in Francophone Europe.",
            lessonBody = "Passage:\n« Depuis quelques années, l'essor du travail hybride transforme profondément l'équilibre entre vie professionnelle et vie personnelle. Si de nombreux salariés apprécient l'autonomie gagnée et la réduction des trajets quotidiens, certains sociologues soulignent néanmoins le risque d'isolement et l'effacement progressif des frontières horaires. »",
            targetPhraseForAudio = "L'essor du travail hybride transforme profondément l'équilibre entre vie professionnelle et vie personnelle.",
            quizQuestion = "According to the passage, what risk do sociologists highlight regarding hybrid work?",
            quizOptionsPipe = "Higher commuting costs|Isolation and blurred work-hour boundaries|Lower salary packages|Lack of internet infrastructure",
            quizCorrectIndex = 1,
            quizExplanation = "'le risque d'isolement et l'effacement progressif des frontières horaires' translates to isolation and the gradual erasure of time boundaries.",
            estimatedMinutes = 10
        ),
        // Spanish
        OfflineLessonEntity(
            id = "es_vocab_01",
            languageCode = "es",
            level = "Beginner",
            skillCategory = "VOCABULARY",
            title = "Navigating Markets & Tapas Bars",
            summary = "Order confidently, ask for recommendations, and handle bills across Spain and Latin America.",
            lessonBody = "Key expressions:\n• ¿Qué me recomienda hoy? — What do you recommend today?\n• Para compartir, por favor — To share, please\n• ¿Me trae la cuenta cuando pueda? — Could you bring me the check when you can?\n• Estaba delicioso — It was delicious",
            targetPhraseForAudio = "¿Qué me recomienda hoy? ¿Me trae la cuenta cuando pueda, por favor?",
            quizQuestion = "How do you politely ask what the server recommends?",
            quizOptionsPipe = "¿Qué quieres hoy?|¿Qué me recomienda hoy?|Dame comida buena.|¿Dónde está la cuenta?",
            quizCorrectIndex = 1,
            quizExplanation = "'¿Qué me recomienda hoy?' uses the polite 'usted' form of recomendar.",
            estimatedMinutes = 8
        ),
        OfflineLessonEntity(
            id = "es_grammar_02",
            languageCode = "es",
            level = "Intermediate",
            skillCategory = "GRAMMAR",
            title = "Mastering Ser vs. Estar with Adjectives",
            summary = "See how meaning shifts dramatically depending on whether you choose ser or estar.",
            lessonBody = "While 'ser' expresses inherent characteristics and 'estar' expresses states or locations, several adjectives change meaning:\n• ser listo = to be clever / smart\n• estar listo = to be ready\n• ser aburrido = to be boring\n• estar aburrido = to feel bored",
            targetPhraseForAudio = "Ella es muy lista y ya está lista para la entrevista.",
            quizQuestion = "How do you say 'I am ready for the trip'?",
            quizOptionsPipe = "Soy listo para el viaje.|Estoy listo para el viaje.|Tengo listo el viaje.|Hago listo para el viaje.",
            quizCorrectIndex = 1,
            quizExplanation = "'Estar listo' means 'to be ready', whereas 'ser listo' means 'to be clever'.",
            estimatedMinutes = 10
        ),
        // Japanese
        OfflineLessonEntity(
            id = "ja_listening_01",
            languageCode = "ja",
            level = "Beginner",
            skillCategory = "LISTENING",
            title = "Polite Desu/Masu Introductions & Station Navigation",
            summary = "Understand polite Japanese sentence endings, particles (wa, ni, e), and train station requests.",
            lessonBody = "Core patterns:\n• はじめまして (Hajimemashite) — Nice to meet you (for the first time)\n• すみません、東京駅はどこですか？ (Sumimasen, Tōkyō-eki wa doko desu ka?) — Excuse me, where is Tokyo Station?\n• この電車は京都に行きますか？ (Kono densha wa Kyōto ni ikimasu ka?) — Does this train go to Kyoto?",
            targetPhraseForAudio = "すみません、この電車は京都に行きますか？",
            quizQuestion = "What is the speaker asking in 'Kono densha wa Kyōto ni ikimasu ka?'",
            quizOptionsPipe = "When did the train leave Kyoto?|Does this train go to Kyoto?|How much is a ticket to Kyoto?|Is Kyoto far from here?",
            quizCorrectIndex = 1,
            quizExplanation = "'Kono densha' = this train, 'Kyōto ni ikimasu ka' = does it go to Kyoto?",
            estimatedMinutes = 10
        ),
        // German
        OfflineLessonEntity(
            id = "de_grammar_01",
            languageCode = "de",
            level = "Intermediate",
            skillCategory = "GRAMMAR",
            title = "Subordinate Clauses & Verb-Final Word Order",
            summary = "Build fluent complex sentences using weil, dass, and obwohl.",
            lessonBody = "In German subordinate clauses introduced by conjunctions such as 'weil' (because), 'dass' (that), and 'obwohl' (although), the conjugated verb moves to the very end of the clause:\n• Ich lerne Deutsch, weil ich in Berlin arbeiten möchte.\n• Obwohl es heute kalt ist, gehen wir spazieren.",
            targetPhraseForAudio = "Ich lerne Deutsch, weil ich nächstes Jahr in Berlin arbeiten möchte.",
            quizQuestion = "Choose the grammatically correct sentence:",
            quizOptionsPipe = "Ich weiß, dass er kommt morgen.|Ich weiß, dass er morgen kommt.|Ich weiß, dass kommt er morgen.|Ich weiß, er dass morgen kommt.",
            quizCorrectIndex = 1,
            quizExplanation = "After 'dass', the conjugated verb 'kommt' moves to the end of the subordinate clause.",
            estimatedMinutes = 11
        ),
        // Arabic
        OfflineLessonEntity(
            id = "ar_speaking_01",
            languageCode = "ar",
            level = "Beginner",
            skillCategory = "SPEAKING",
            title = "Warm Arabic Hospitality & Courteous Responses",
            summary = "Practice welcoming phrases, pharyngeal consonant clarity, and polite replies.",
            lessonBody = "Arabic greetings carry rich warmth and reciprocity:\n• أهلاً وسهلاً (Ahlan wa sahlan) — Welcome\n• صباح الخير / صباح النور (Sabāḥ al-khayr / Sabāḥ an-nūr) — Good morning / Morning of light (reply)\n• تشرفت بمعرفتك (Tasharraftu bima'rifatik) — Honored to meet you",
            targetPhraseForAudio = "أهلاً وسهلاً، تشرفت بمعرفتك",
            quizQuestion = "What is the traditional response to 'Sabāḥ al-khayr' (Good morning)?",
            quizOptionsPipe = "Ma'a as-salāmah|Sabāḥ an-nūr|Shukran jazīlan|La a'rif",
            quizCorrectIndex = 1,
            quizExplanation = "'Sabāḥ an-nūr' ('Morning of light') is the warm customary response to 'Sabāḥ al-khayr'.",
            estimatedMinutes = 9
        ),
        // Malagasy
        OfflineLessonEntity(
            id = "mg_vocab_01",
            languageCode = "mg",
            level = "Beginner",
            skillCategory = "VOCABULARY",
            title = "Everyday Malagasy Greetings & Respectful Dialogue",
            summary = "Learn VOS (Verb-Object-Subject) sentence rhythm and everyday expressions in Madagascar.",
            lessonBody = "Malagasy belongs to the Austronesian family and typically places the predicate/verb before the subject:\n• Manao ahoana tompoko — Hello (polite)\n• Salama tsara fa misaotra — I am doing well, thank you\n• Misaotra indrindra — Thank you very much\n• Azafady — Excuse me / Please\n• Veloma finaritra — Goodbye, stay well",
            targetPhraseForAudio = "Manao ahoana tompoko, salama tsara fa misaotra indrindra.",
            quizQuestion = "Which Malagasy word means both 'Please' and 'Excuse me'?",
            quizOptionsPipe = "Veloma|Azafady|Misaotra|Salama",
            quizCorrectIndex = 1,
            quizExplanation = "'Azafady' is used politely both to say 'excuse me' and 'please'.",
            estimatedMinutes = 8
        ),
        // Mandarin Chinese
        OfflineLessonEntity(
            id = "zh_writing_01",
            languageCode = "zh",
            level = "Elementary",
            skillCategory = "WRITING",
            title = "Time-Place-Action Sentence Architecture in Mandarin",
            summary = "Structure natural Chinese sentences using Subject + Time + Location + Verb.",
            lessonBody = "Unlike English (which often puts time and place at the end), Mandarin places Time and Location before the main action verb:\n• 我明天在北京开会 (Wǒ míngtiān zài Běijīng kāihuì) — Literally: I tomorrow at Beijing hold-meeting.\n• 她每天早上喝茶 (Tā měitiān zǎoshang hē chá) — She drinks tea every morning.",
            targetPhraseForAudio = "我明天在北京开会。",
            quizQuestion = "Which word order matches natural Mandarin structure for 'I study Chinese at home today'?",
            quizOptionsPipe = "我学习中文在家今天|我今天在家学习中文|今天学习中文我在家|在家学习我今天中文",
            quizCorrectIndex = 1,
            quizExplanation = "Subject (我) + Time (今天) + Location (在家) + Verb-Object (学习中文).",
            estimatedMinutes = 10
        )
    )

    fun defaultCachedDictionaryEntries(): List<DictionaryCacheEntity> = listOf(
        DictionaryCacheEntity(
            cacheKey = "en_fr_resilience",
            word = "resilience",
            sourceLanguage = "en",
            targetLanguage = "fr",
            hasVerifiedEntry = true,
            verifiedPartOfSpeech = "noun",
            verifiedDefinitionsPipe = "The mental ability to recover quickly from depression, illness or misfortune.|The physical property of a material that can resume its shape after being stretched or deformed; elasticity.",
            verifiedIpa = "/ɹɪˈzɪli.əns/",
            verifiedAudioUrl = null,
            verifiedExamplesPipe = "Community resilience helped the coastal town rebuild within months.",
            verifiedSynonymsPipe = "elasticity|toughness|grit|adaptability",
            verifiedAntonymsPipe = "fragility|vulnerability",
            hasAiExplanation = true,
            aiTranslation = "la résilience",
            aiContextualMeaning = "Used in both psychology and engineering; in French ('la résilience'), popularized in psychology by Boris Cyrulnik.",
            aiCollocationsPipe = "build resilience (développer la résilience)|emotional resilience (résilience émotionnelle)|climate resilience (résilience climatique)",
            aiFormalVsInformal = "Neutral to formal across professional, academic, and everyday contexts.",
            aiIdiomsPipe = "bounce back from adversity — rebondir après l'épreuve",
            aiVerbConjugationsPipe = "Not applicable (noun)",
            aiNounGenderAndPlural = "Feminine in French: la résilience (pl. les résiliences)",
            aiEtymologyNote = "From Latin 'resilire' meaning 'to leap back' or 'rebound'.",
            aiRegionalVariants = "Consistent usage across France, Belgium, Switzerland, and Québec."
        ),
        DictionaryCacheEntity(
            cacheKey = "en_es_eloquent",
            word = "eloquent",
            sourceLanguage = "en",
            targetLanguage = "es",
            hasVerifiedEntry = true,
            verifiedPartOfSpeech = "adjective",
            verifiedDefinitionsPipe = "Fluently persuasive and articulate in speech or writing.|Clearly expressing or indicating something without words.",
            verifiedIpa = "/ˈɛləkwənt/",
            verifiedAudioUrl = null,
            verifiedExamplesPipe = "She delivered an eloquent defense of multilingual education.",
            verifiedSynonymsPipe = "articulate|expressive|silver-tongued|persuasive",
            verifiedAntonymsPipe = "inarticulate|hesitant",
            hasAiExplanation = true,
            aiTranslation = "elocuente",
            aiContextualMeaning = "Describes speech, writers, or even silent gestures ('un silencio elocuente').",
            aiCollocationsPipe = "eloquent speaker (orador elocuente)|eloquent testimony (testimonio elocuente)",
            aiFormalVsInformal = "Slightly elevated / literary, widely understood in everyday educated speech.",
            aiIdiomsPipe = "wax eloquent — hablar con gran elocuencia",
            aiVerbConjugationsPipe = "Not applicable (adjective)",
            aiNounGenderAndPlural = "Invariant for gender in Spanish: singular 'elocuente', plural 'elocuentes'.",
            aiEtymologyNote = "From Latin 'eloquens', present participle of 'eloqui' (to speak out).",
            aiRegionalVariants = "Universal across Spain and Latin America."
        ),
        DictionaryCacheEntity(
            cacheKey = "fr_en_bonjour",
            word = "bonjour",
            sourceLanguage = "fr",
            targetLanguage = "en",
            hasVerifiedEntry = true,
            verifiedPartOfSpeech = "interjection / noun",
            verifiedDefinitionsPipe = "Good day; hello (polite daytime greeting in French).",
            verifiedIpa = "/bɔ̃.ʒuʁ/",
            verifiedAudioUrl = null,
            verifiedExamplesPipe = "Bonjour madame, comment allez-vous aujourd'hui ?",
            verifiedSynonymsPipe = "salut (informal)|bien le bonjour",
            verifiedAntonymsPipe = "au revoir|bonsoir",
            hasAiExplanation = true,
            aiTranslation = "hello / good morning / good afternoon",
            aiContextualMeaning = "Standard polite greeting across Francophone cultures from morning until early evening; essential before asking a question in shops or cafés.",
            aiCollocationsPipe = "dire bonjour (to say hello)|souhaiter le bonjour (to send greetings)",
            aiFormalVsInformal = "Universal across formal and everyday contexts ('salut' is reserved for friends/peers).",
            aiIdiomsPipe = "simple comme bonjour — as easy as pie",
            aiVerbConjugationsPipe = "Not applicable (interjection / masculine noun: un bonjour)",
            aiNounGenderAndPlural = "Masculine when used as a noun: le bonjour",
            aiEtymologyNote = "Compound of 'bon' (good) + 'jour' (day).",
            aiRegionalVariants = "Universal across France, Québec, Belgium, Switzerland, and Francophone Africa."
        )
    )

    fun initialWelcomeSurvey(): SurveyEntity = SurveyEntity(
        id = "srv_learner_experience_01",
        title = "Interna Learner Experience Pulse",
        description = "Help shape upcoming language pairs, roleplay scenarios, and offline study packs.",
        mcQuestion = "Which Interna feature helps your fluency the most right now?",
        mcOptionsPipe = "Interna AI Conversation & Roleplay|Universal Dictionary & Nuance|Pronunciation Coach|Offline Lessons & Flashcards",
        freeTextQuestion = "Which language, regional dialect, or roleplay topic should we expand next?",
        isPublished = true,
        createdByAdminId = "system_seed"
    )

    /**
     * Initial mobile-money provider records with blank receiving numbers and prices.
     * No unverified receiving numbers or payment credentials are ever invented or hardcoded;
     * the administrator configures real verified details in the Developer Console.
     */
    fun defaultPaymentMethodConfigs(): List<PaymentMethodConfigEntity> = listOf(
        PaymentMethodConfigEntity(
            methodCode = "YAS_MONEY",
            displayName = "Yas Money",
            isEnabled = true
        ),
        PaymentMethodConfigEntity(
            methodCode = "AIRTEL_MONEY",
            displayName = "Airtel Money",
            isEnabled = true
        ),
        PaymentMethodConfigEntity(
            methodCode = "ORANGE_MONEY",
            displayName = "Orange Money",
            isEnabled = true
        )
    )
}
