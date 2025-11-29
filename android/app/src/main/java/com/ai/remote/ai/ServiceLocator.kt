package com.ai.remote.ai

import com.cactus.CactusLM

object ServiceLocator {

    private const val TOKEN =
        "AIzaSyB52Ugjg9hv-qRUV6OKWeybqS0GUx2OP8U"

    // Reuse LMs if you like, or keep as is for the hackathon
    private val classifierLM by lazy { CactusLM() }
    private val scriptLM by lazy { CactusLM() }
    val router: HybridRouter by lazy {

        val tinyClassifier = LocalClassifierLM(
            lm = classifierLM,
            modelName = "smollm2-360m"
        )

        val generator = CactusScriptGenerator(
            lm = scriptLM,
            modelName = "lfm2-1.2b",
        )

        val cloud = GeminiCloudClient(TOKEN)


        HybridRouter(tinyClassifier, generator, cloud)
    }

}
