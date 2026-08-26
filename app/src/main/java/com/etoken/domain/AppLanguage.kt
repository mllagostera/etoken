package com.etoken.domain

/**
 * The languages the app ships strings for, plus the option of not choosing one.
 *
 * Declaration order is the order the picker shows: [SYSTEM] first because it is
 * the answer for anyone who has not thought about it, then English and Spanish
 * — the default locale and the one the project was written in — and the rest
 * alphabetically. [tag] is a BCP 47 language tag and has to match the `values-`
 * qualifier of the locale it names, since that is what resolves the strings.
 */
enum class AppLanguage(val tag: String) {
    /** Whatever the device asks for, which is what Android does unaided. */
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
    CATALAN("ca"),
    FRENCH("fr"),
    GERMAN("de"),
    ITALIAN("it"),
    JAPANESE("ja"),
    ;

    companion object {
        /**
         * The language stored under [tag], or [SYSTEM] for anything unknown —
         * an absent preference, but also a tag written by a version of the app
         * that offered a locale this one has dropped. Falling back to the
         * device's own language is the one answer that is never wrong.
         */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
