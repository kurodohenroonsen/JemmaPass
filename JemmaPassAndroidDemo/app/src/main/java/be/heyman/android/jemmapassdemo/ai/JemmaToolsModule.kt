/*
 * JemmaToolsModule.kt — JEMMA Pass · JemmaAppDemo · v2.6.0
 *
 * Hilt wiring for [JemmaTools] and its derived `List<ToolProvider>` that
 * consumers can pass directly to `LlmChatModelHelper.initialize(tools=…)`.
 *
 * The class itself is `@Singleton @Inject constructor(…)` so Hilt knows
 * how to build it without this module. What this module DOES add :
 *
 *   1. A `@JemmaToolProviders`-qualified `List<ToolProvider>` that wraps
 *      [JemmaTools] via `litertlm.tool(toolSet)`. This is the shape the
 *      LiteRT-LM engine expects.
 *
 *   2. A safe single point of construction — if 2.6.6 introduces a chat
 *      panel that also wants `JemmaTools` plus a few extra tools, we add
 *      them here rather than scatter `listOf(tool(jemmaTools))` calls
 *      across fragments.
 *
 * The `@JemmaToolProviders` qualifier lets a future delivery also expose a
 * second list (e.g. `@ForgeToolProviders` for wizard-only tools). It also
 * sidesteps the otherwise-ambiguous `@Inject providers: List<ToolProvider>`
 * lookup error if another module already provides a list of providers.
 *
 *
 * Sample consumer wiring (livraison 2.6.1, in MedScanForVictimController) :
 * ```kotlin
 * @Singleton
 * class MedScanForVictimController @Inject constructor(
 *     private val jemmaTools: JemmaTools,
 *     @JemmaToolProviders private val toolProviders: List<ToolProvider>,
 *     ...
 * ) {
 *     suspend fun start(victim: JemmaProfileJ) {
 *         jemmaTools.bind(victim)
 *         LlmChatModelHelper.initialize(
 *             context, model, taskId, supportImage = true, supportAudio = false,
 *             tools = toolProviders,
 *             systemInstruction = ...,
 *         )
 *     }
 * }
 * ```
 */
package be.heyman.android.jemmapassdemo.ai

import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the JEMMA `List<ToolProvider>` so it can coexist with
 * other tool-provider lists (e.g. Edge Gallery's MobileActionsTask).
 *
 * Usage : `@Inject @JemmaToolProviders private val tools: List<ToolProvider>`
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JemmaToolProviders

@Module
@InstallIn(SingletonComponent::class)
object JemmaToolsModule {

    /**
     * Wrap the [JemmaTools] singleton in the `ToolProvider` shape expected
     * by `LlmChatModelHelper.initialize(tools=...)`. Returned as a
     * `List<ToolProvider>` (currently size = 1) so callers can append
     * more provider lists if needed.
     */
    @Provides
    @Singleton
    @JemmaToolProviders
    fun provideJemmaToolProviders(jemmaTools: JemmaTools): List<ToolProvider> =
        listOf(tool(jemmaTools))
}
