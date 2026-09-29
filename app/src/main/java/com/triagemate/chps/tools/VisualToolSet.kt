package com.triagemate.chps.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * The only tool exposed to the visual-assessment conversation. Kept separate from
 * [ClinicalToolSet] because LiteRT-LM prefills the schema of every @Tool in a
 * registered ToolSet on each new conversation.
 */
class VisualToolSet : ToolSet {

    @Tool(
        description = "Report the result of a clinical visual assessment from a photograph taken by a Community Health Officer. Call this tool after analysing a clinical photo. Report exactly what you see in the image relevant to the clinical question asked. Be specific and honest — if the image is unclear or you cannot make a confident assessment, say so."
    )
    fun reportVisualFinding(
        @ToolParam(description = "Whether the specific clinical sign was detected: true or false")
        signDetected: Boolean,
        @ToolParam(description = "What was observed in the image.")
        observation: String,
        @ToolParam(description = "Clinical interpretation of the observation.")
        clinicalImplication: String,
        @ToolParam(description = "Confidence in the assessment: high, medium, or low")
        confidence: String,
        @ToolParam(description = "One sentence for inclusion in referral note.")
        referralNoteAddition: String,
        @ToolParam(description = "Whether image quality was sufficient to make a meaningful assessment: true or false")
        imageQualitySufficient: Boolean
    ): Map<String, Any> = mapOf(
        "sign_detected" to signDetected,
        "observation" to observation,
        "clinical_implication" to clinicalImplication,
        "confidence" to confidence,
        "referral_note_addition" to referralNoteAddition,
        "image_quality_sufficient" to imageQualitySufficient
    )
}
