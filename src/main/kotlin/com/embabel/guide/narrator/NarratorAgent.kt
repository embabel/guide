package com.embabel.guide.narrator

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.Condition
import com.embabel.agent.api.common.OperationContext
import com.embabel.common.textio.template.TemplateRenderer
import com.embabel.guide.GuideProperties

/**
 * Embabel agent that converts markdown assistant messages into TTS-friendly narration.
 *
 * Routing:
 * - SIMPLE (short plain text): pass-through, no LLM call
 * - COMPLEX (markdown without code): LLM summarization
 * - COMPLEX_WITH_CODE (markdown with code blocks): LLM with code-aware prompt
 */
@Agent(description = "Convert markdown to TTS-friendly narration")
class NarratorAgent(
    private val guideProperties: GuideProperties,
    private val templateRenderer: TemplateRenderer
) {

    companion object {
        private const val SIMPLE_MAX_LENGTH = 300
        private val TRIPLE_BACKTICK = Regex("```")
        private val MARKDOWN_INDICATORS = Regex("(^#{1,6}\\s|\\*\\*|\\*|^-\\s|^\\d+\\.\\s|^>\\s|\\[.*]\\(.*\\))", RegexOption.MULTILINE)

        // Unicode emoji ranges: emoticons, symbols, dingbats, transport, misc, flags, etc.
        private val EMOJI_PATTERN = Regex("[\\x{2600}-\\x{27BF}\\x{FE00}-\\x{FE0F}\\x{1F000}-\\x{1FAFF}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]+")

        // URLs: http(s)://... and markdown links [text](url)
        private val MARKDOWN_LINK_PATTERN = Regex("\\[([^]]+)]\\([^)]+\\)")
        private val BARE_URL_PATTERN = Regex("https?://\\S+")

        fun stripEmojis(text: String): String =
            EMOJI_PATTERN.replace(text, "").trim()

        /**
         * Replace markdown links with just their display text.
         * Bare URLs are left for the LLM to describe contextually.
         */
        fun stripMarkdownLinks(text: String): String =
            MARKDOWN_LINK_PATTERN.replace(text, "$1")
    }

    /**
     * Classify the input content to determine narration strategy.
     * Pure code — no LLM call.
     */
    @Action(readOnly = true)
    fun classify(input: NarrationInput): ClassifiedNarration {
        val content = input.content
        val category = when {
            TRIPLE_BACKTICK.containsMatchIn(content) -> NarrationCategory.COMPLEX_WITH_CODE
            content.length <= SIMPLE_MAX_LENGTH && !MARKDOWN_INDICATORS.containsMatchIn(content) -> NarrationCategory.SIMPLE
            else -> NarrationCategory.COMPLEX
        }
        return ClassifiedNarration(content = content, category = category)
    }

    // ─── Condition routing ───

    @Condition(name = "isSimple")
    fun isSimple(c: ClassifiedNarration): Boolean = c.category == NarrationCategory.SIMPLE

    @Condition(name = "isComplex")
    fun isComplex(c: ClassifiedNarration): Boolean = c.category == NarrationCategory.COMPLEX

    @Condition(name = "hasCode")
    fun hasCode(c: ClassifiedNarration): Boolean = c.category == NarrationCategory.COMPLEX_WITH_CODE

    // ─── Direct invocation (bypasses agent planner) ───

    /**
     * Narrate content directly, without going through AgentInvocation.
     * Classifies the content and routes to the appropriate narration strategy.
     *
     * @param persona the persona name to use for narration voice (e.g. "jesse", "adaptive")
     */
    fun narrate(content: String, persona: String?, ctx: OperationContext): Narration {
        val classified = classify(NarrationInput(content))
        return when (classified.category) {
            NarrationCategory.SIMPLE -> Narration(stripMarkdownLinks(stripEmojis(classified.content)))
            NarrationCategory.COMPLEX -> narrateComplex(classified, persona, ctx)
            NarrationCategory.COMPLEX_WITH_CODE -> narrateWithCode(classified, persona, ctx)
        }
    }

    private fun templateModel(content: String, persona: String?): Map<String, Any> {
        val cleaned = stripMarkdownLinks(content)
        val wordCount = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val model = mutableMapOf<String, Any>(
            "content" to cleaned,
            "wordCount" to wordCount,
            "targetWords" to when {
                wordCount <= 350 -> wordCount
                wordCount <= 700 -> 180
                wordCount <= 1200 -> 250
                else -> 300
            }
        )
        if (persona != null) {
            model["persona"] = persona
        }
        return model
    }

    // ─── Narration actions ───

    /**
     * Simple content: return as-is, no LLM needed.
     */
    @AchievesGoal(description = "Markdown narrated for text-to-speech")
    @Action(pre = ["isSimple"], readOnly = true)
    fun narrateSimple(c: ClassifiedNarration): Narration = Narration(stripMarkdownLinks(stripEmojis(c.content)))

    /**
     * Complex markdown (no code): LLM summarization into speech-friendly text.
     */
    @AchievesGoal(description = "Markdown narrated for text-to-speech")
    @Action(pre = ["isComplex"])
    fun narrateComplex(c: ClassifiedNarration, persona: String?, ctx: OperationContext): Narration {
        val prompt = templateRenderer.renderLoadedTemplate(
            "narration_complex",
            templateModel(c.content, persona)
        )
        return ctx.ai()
            .withLlm(guideProperties.narratorLlm)
            .createObject(prompt, Narration::class.java)
    }

    /**
     * Markdown with code blocks: LLM with code-aware prompt.
     */
    @AchievesGoal(description = "Markdown narrated for text-to-speech")
    @Action(pre = ["hasCode"])
    fun narrateWithCode(c: ClassifiedNarration, persona: String?, ctx: OperationContext): Narration {
        val prompt = templateRenderer.renderLoadedTemplate(
            "narration_code",
            templateModel(c.content, persona)
        )
        return ctx.ai()
            .withLlm(guideProperties.narratorLlm)
            .createObject(prompt, Narration::class.java)
    }
}
