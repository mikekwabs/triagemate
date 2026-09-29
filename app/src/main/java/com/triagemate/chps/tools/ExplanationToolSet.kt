package com.triagemate.chps.tools

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * The only tool exposed to the "Learn about this case" conversation. Kept separate
 * from [ClinicalToolSet] so the educator prompt doesn't prefill the triage tool schemas.
 */
class ExplanationToolSet : ToolSet {

    @Tool(
        description = "Generate a short educational explanation for an already-finalised triage decision. This is shown after the assessment as supporting context for the Community Health Officer. It does NOT change the urgency, action, or referral note — those are already fixed. Be concise, clinically accurate, and pragmatic for a CHO working in a CHPS compound."
    )
    fun generateClinicalExplanation(
        @ToolParam(description = "Plain-language reason this case received its urgency classification (RED / AMBER / GREEN). 2 to 4 sentences. Reference the specific symptoms, danger signs, or vitals that drove the decision.")
        whyThisClassification: String,
        @ToolParam(description = "Practical signs the CHO should watch for during transport, follow-up, or local care — early-warning indicators of deterioration. 2 to 4 sentences.")
        whatToWatchFor: String,
        @ToolParam(description = "Brief clinical reference: e.g. 'WHO IMCI 2014', 'Ghana Health Service Antenatal Guidelines', or the specific danger-sign chapter that applies. One short phrase.")
        clinicalReference: String
    ): Map<String, Any> = mapOf(
        "why_this_classification" to whyThisClassification,
        "what_to_watch_for" to whatToWatchFor,
        "clinical_reference" to clinicalReference
    )
}
