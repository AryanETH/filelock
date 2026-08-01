package com.aitoyz.mapplock.model

data class Language(
    val name: String,
    val nativeName: String,
    val code: String,
    val prefix: String,
    val flag: String = ""
)

val supportedLanguages = listOf(
    Language("English", "English", "en", "EN"),
    Language("Hindi", "हिन्दी", "hi", "हि"),
    Language("Arabic", "العربية", "ar", "ع"),
    Language("Bangla", "বাংলা", "bn", "বা"),
    Language("German", "Deutsch", "de", "DE"),
    Language("Spanish", "Español", "es", "ES"),
    Language("French", "Français", "fr", "FR"),
    Language("Indonesian", "Bahasa Indonesia", "in", "ID"),
    Language("Portuguese", "Português", "pt", "PT"),
    Language("Portuguese Brazil", "Português Brasil", "pt-rBR", "BR"),
    Language("Russian", "Русский", "ru", "RU"),
    Language("Tagalog", "Tagalog", "tl", "TL"),
    Language("Turkish", "Türkçe", "tr", "TR"),
    Language("Urdu", "اردو", "ur", "ار"),
    Language("Vietnamese", "Tiếng Việt", "vi", "VI"),
    Language("Chinese", "简体中文", "zh", "ZH")
)
