package com.ai.search.model

/** Which search domain a result came from. */
enum class ResultDomain { SETTINGS, FILES }

/** The action to perform when the user activates a result. */
sealed interface ResultAction {
    /** Open a Windows Settings page via an ms-settings: deep link. */
    data class OpenSettings(val deepLink: String) : ResultAction

    /** Open a file with its default application. */
    data class OpenFile(val path: String) : ResultAction

    /** Reveal a file in File Explorer. */
    data class RevealFile(val path: String) : ResultAction
}

/**
 * A single, render-ready result item shown in the list, independent of domain.
 */
data class ResultItem(
    val id: String,
    val domain: ResultDomain,
    val title: String,
    val subtitle: String,
    val action: ResultAction,
    val score: Double = 0.0,
)
