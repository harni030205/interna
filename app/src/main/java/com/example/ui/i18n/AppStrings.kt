package com.example.ui.i18n

data class UiStrings(
    val appName: String = "Interna",
    val tagline: String = "One World. Every Language.",
    val navHome: String = "Home",
    val navConversation: String = "AI Conversation",
    val navPractice: String = "Practice",
    val navDictionary: String = "Universal Dictionary",
    val navTranslation: String = "Translation",
    val navPronunciation: String = "Pronunciation",
    val navProgress: String = "My Progress",
    val navSubscription: String = "Subscription",
    val navSettings: String = "Settings",
    val navDeveloperConsole: String = "Developer Console",
    val navMoreHub: String = "Tools",
    val onlineLabel: String = "Online",
    val offlineLabel: String = "Offline Mode",
    val signOut: String = "Sign Out"
)

object InterfaceLocalizer {
    val supportedInterfaceLanguages = listOf(
        "en" to "English",
        "fr" to "Français",
        "es" to "Español",
        "de" to "Deutsch",
        "ja" to "日本語",
        "ar" to "العربية"
    )

    fun stringsFor(code: String): UiStrings = when (code) {
        "fr" -> UiStrings(
            tagline = "Un Monde. Toutes les Langues.",
            navHome = "Accueil",
            navConversation = "Conversation IA",
            navPractice = "Pratique",
            navDictionary = "Dictionnaire Universel",
            navTranslation = "Traduction",
            navPronunciation = "Prononciation",
            navProgress = "Mes Progrès",
            navSubscription = "Abonnement",
            navSettings = "Paramètres",
            navDeveloperConsole = "Console Développeur",
            navMoreHub = "Outils",
            onlineLabel = "En ligne",
            offlineLabel = "Hors ligne",
            signOut = "Déconnexion"
        )
        "es" -> UiStrings(
            tagline = "Un Mundo. Todos los Idiomas.",
            navHome = "Inicio",
            navConversation = "Conversación IA",
            navPractice = "Práctica",
            navDictionary = "Diccionario Universal",
            navTranslation = "Traducción",
            navPronunciation = "Pronunciación",
            navProgress = "Mi Progreso",
            navSubscription = "Suscripción",
            navSettings = "Ajustes",
            navDeveloperConsole = "Consola de Desarrollador",
            navMoreHub = "Herramientas",
            onlineLabel = "En línea",
            offlineLabel = "Sin conexión",
            signOut = "Cerrar sesión"
        )
        "de" -> UiStrings(
            tagline = "Eine Welt. Jede Sprache.",
            navHome = "Start",
            navConversation = "KI-Gespräch",
            navPractice = "Üben",
            navDictionary = "Universalwörterbuch",
            navTranslation = "Übersetzung",
            navPronunciation = "Aussprache",
            navProgress = "Fortschritt",
            navSubscription = "Abonnement",
            navSettings = "Einstellungen",
            navDeveloperConsole = "Entwicklerkonsole",
            navMoreHub = "Werkzeuge",
            onlineLabel = "Online",
            offlineLabel = "Offline-Modus",
            signOut = "Abmelden"
        )
        "ja" -> UiStrings(
            tagline = "ひとつの世界、すべての言語。",
            navHome = "ホーム",
            navConversation = "AI会話",
            navPractice = "練習",
            navDictionary = "ユニバーサル辞書",
            navTranslation = "翻訳",
            navPronunciation = "発音コーチ",
            navProgress = "学習進捗",
            navSubscription = "プラン管理",
            navSettings = "設定",
            navDeveloperConsole = "開発者コンソール",
            navMoreHub = "ツール",
            onlineLabel = "オンライン",
            offlineLabel = "オフライン",
            signOut = "ログアウト"
        )
        "ar" -> UiStrings(
            tagline = "عالم واحد. كل اللغات.",
            navHome = "الرئيسية",
            navConversation = "محادثة الذكاء الاصطناعي",
            navPractice = "التدريب",
            navDictionary = "القاموس الشامل",
            navTranslation = "الترجمة",
            navPronunciation = "مدرب النطق",
            navProgress = "تقدمي",
            navSubscription = "الاشتراك",
            navSettings = "الإعدادات",
            navDeveloperConsole = "لوحة المطور",
            navMoreHub = "الأدوات",
            onlineLabel = "متصل",
            offlineLabel = "وضع عدم الاتصال",
            signOut = "تسجيل الخروج"
        )
        else -> UiStrings()
    }
}
