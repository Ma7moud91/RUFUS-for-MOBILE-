package com.example.domain.models

data class RufusLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
) {
    companion object {
        val ALL = listOf(
            RufusLanguage("en", "English", "English"),
            RufusLanguage("ar", "العربية", "Arabic"),
            RufusLanguage("es", "Español", "Spanish"),
            RufusLanguage("fr", "Français", "French"),
            RufusLanguage("de", "Deutsch", "German"),
            RufusLanguage("it", "Italiano", "Italian"),
            RufusLanguage("pt", "Português", "Portuguese"),
            RufusLanguage("ru", "Русский", "Russian"),
            RufusLanguage("zh-CN", "简体中文", "Chinese (Simplified)"),
            RufusLanguage("zh-TW", "繁體中文", "Chinese (Traditional)"),
            RufusLanguage("ja", "日本語", "Japanese"),
            RufusLanguage("ko", "한국어", "Korean"),
            RufusLanguage("tr", "Türkçe", "Turkish"),
            RufusLanguage("pl", "Polski", "Polish"),
            RufusLanguage("nl", "Nederlands", "Dutch"),
            RufusLanguage("sv", "Svenska", "Swedish"),
            RufusLanguage("da", "Dansk", "Danish"),
            RufusLanguage("fi", "Suomi", "Finnish"),
            RufusLanguage("no", "Norsk", "Norwegian"),
            RufusLanguage("cs", "Čeština", "Czech"),
            RufusLanguage("hu", "Magyar", "Hungarian"),
            RufusLanguage("ro", "Română", "Romanian"),
            RufusLanguage("el", "Ελληνικά", "Greek"),
            RufusLanguage("he", "עברית", "Hebrew"),
            RufusLanguage("hi", "हिन्दी", "Hindi"),
            RufusLanguage("vi", "Tiếng Việt", "Vietnamese"),
            RufusLanguage("id", "Bahasa Indonesia", "Indonesian"),
            RufusLanguage("ms", "Bahasa Melayu", "Malay"),
            RufusLanguage("uk", "Українська", "Ukrainian"),
            RufusLanguage("bg", "Български", "Bulgarian"),
            RufusLanguage("sk", "Slovenčina", "Slovak"),
            RufusLanguage("hr", "Hrvatski", "Croatian"),
            RufusLanguage("sr", "Srpski", "Serbian"),
            RufusLanguage("ca", "Català", "Catalan"),
            RufusLanguage("gl", "Galego", "Galician"),
            RufusLanguage("eu", "Euskara", "Basque"),
            RufusLanguage("fa", "فارسی", "Persian"),
            RufusLanguage("th", "ไทย", "Thai")
        )
    }
}
