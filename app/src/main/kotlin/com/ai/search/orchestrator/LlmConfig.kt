package com.ai.search.orchestrator

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/** Which cloud LLM provider drives the agent. */
enum class LlmProvider { OPENAI, ANTHROPIC, GOOGLE }

/**
 * LLM selection resolved from environment variables so the model is swappable without a rebuild:
 *  - `LLM_PROVIDER` : `openai` | `anthropic` | `google`  (default `openai`)
 *  - `LLM_API_KEY`  : provider API key (required to enable the real agent)
 *  - `LLM_MODEL`    : optional model id override
 */
data class LlmConfig(
    val provider: LlmProvider,
    val apiKey: String,
    val model: LLModel,
) {
    /** Builds the Koog prompt executor for the selected provider. */
    fun executor(): PromptExecutor = when (provider) {
        LlmProvider.OPENAI -> simpleOpenAIExecutor(apiKey)
        LlmProvider.ANTHROPIC -> simpleAnthropicExecutor(apiKey)
        LlmProvider.GOOGLE -> simpleGoogleAIExecutor(apiKey)
    }

    companion object {
        /** Returns null when no `LLM_API_KEY` is set, so the caller can fall back to the mock. */
        fun fromEnv(): LlmConfig? {
            val key = System.getenv("LLM_API_KEY")?.trim().orEmpty()
            if (key.isEmpty()) return null

            val provider = when (System.getenv("LLM_PROVIDER")?.trim()?.lowercase()) {
                "anthropic", "claude" -> LlmProvider.ANTHROPIC
                "google", "gemini" -> LlmProvider.GOOGLE
                else -> LlmProvider.OPENAI
            }
            val modelId = System.getenv("LLM_MODEL")?.trim()?.lowercase().orEmpty()
            return LlmConfig(provider, key, resolveModel(provider, modelId))
        }

        private fun resolveModel(provider: LlmProvider, id: String): LLModel = when (provider) {
            LlmProvider.OPENAI -> OpenAIModels.Chat.GPT4o
            LlmProvider.ANTHROPIC -> when {
                id.contains("haiku") -> AnthropicModels.Haiku_4_5
                else -> AnthropicModels.Sonnet_4_5
            }
            LlmProvider.GOOGLE -> when {
                id.contains("pro") -> GoogleModels.Gemini2_5Pro
                else -> GoogleModels.Gemini2_5Flash
            }
        }
    }
}
