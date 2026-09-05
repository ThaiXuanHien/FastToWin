@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.localization

// This source is compiled for both JS and Wasm by the shared module.
actual fun platformLanguageTags(): List<String> = browserLanguageTags()
    .split('|')
    .filter(String::isNotBlank)

actual fun applyPlatformLanguageTag(languageTag: String) = updateDocumentLanguage(languageTag)

private fun browserLanguageTags(): String = js(
    """{
        if (typeof navigator === 'undefined') return '';
        const languages = Array.isArray(navigator.languages)
            ? navigator.languages.filter(tag => typeof tag === 'string' && tag.trim().length > 0)
            : [];
        if (languages.length > 0) return languages.join('|');
        return typeof navigator.language === 'string' ? navigator.language : '';
    }"""
)

private fun updateDocumentLanguage(languageTag: String): Unit = js(
    """{
        if (typeof document !== 'undefined' && document.documentElement) {
            document.documentElement.lang = languageTag;
        }
    }"""
)
