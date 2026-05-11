package com.geovault.model

data class Language(
    val name: String,
    val nativeName: String,
    val code: String,
    val flag: String = "" // Optional: could add flag emojis or icons
)

val supportedLanguages = listOf(
    Language("English", "English", "en"),
    Language("Hindi", "हिन्दी", "hi"),
    Language("Afrikaans", "Afrikaans", "af"),
    Language("Arabic", "العربية", "ar"),
    Language("Bangla", "বাংলা", "bn"),
    Language("Dutch", "Nederlands", "nl"),
    Language("German", "Deutsch", "de"),
    Language("Indonesian", "Bahasa Indonesia", "id"),
    Language("Italian", "Italiano", "it"),
    Language("Japanese", "日本語", "ja"),
    Language("Korean", "한국어", "ko"),
    Language("Malay", "Bahasa Melayu", "ms"),
    Language("Marathi", "मराठी", "mr"),
    Language("Portuguese", "Português", "pt"),
    Language("Russian", "Русский", "ru"),
    Language("Spanish", "Español", "es"),
    Language("Tagalog", "Tagalog", "tl"),
    Language("Thai", "ไทย", "th"),
    Language("Turkish", "Türkçe", "tr"),
    Language("Ukrainian", "Українська", "uk"),
    Language("Vietnamese", "Tiếng Việt", "vi")
)
