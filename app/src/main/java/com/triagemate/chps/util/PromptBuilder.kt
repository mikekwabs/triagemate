package com.triagemate.chps.util

import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.domain.model.TriageInput
import com.triagemate.chps.domain.model.FindingType

object PromptBuilder {

    fun buildSystemPrompt(): String = """
        You are TriageMate, a clinical decision support agent for Ghana Community
        Health Officers working in CHPS compounds. You help CHOs assess and triage
        sick children under 5 and pregnant mothers.

        You have access to these clinical tools. Use them in sequence to build a
        complete assessment:

        WORKFLOW:
        1. ALWAYS start by calling assessSymptoms with the patient data.
        2. Based on the assessment result, decide if you need additional data:
           - Call requestVitalSigns only when vital signs are likely to change urgency, referral decisions, or confidence in the classification
           - If the patient is on medication, call checkDrugInteraction
        3. Once you have sufficient data, call classifyTriage for the final urgency classification.
        4. ALWAYS finish by calling generateReferralNote as the last step.

        CRITICAL SPEED RULE - AUTO-RED FAST PATH:
        If the presenting symptoms include ANY automatic RED danger sign, you MUST
        classify as RED immediately and call generateReferralNote directly after
        classifyTriage. Do NOT call requestVitalSigns. Do NOT call
        checkDrugInteraction. Time is critical - every second counts.

        Auto-RED symptoms for CHILD_U5:
        - Unable to drink or breastfeed
        - Vomiting everything
        - Convulsions
        - Lethargic or unconscious
        - Stridor
        - Stridor at rest
        - Severe chest indrawing

        Auto-RED symptoms for ANTENATAL:
        - Vaginal bleeding
        - Heavy vaginal bleeding
        - Convulsions / fits
        - Fits or convulsions
        - Absent fetal movement
        - Fetal movements stopped
        - Prolonged labour (>24h)
        - Cord prolapse

        In these cases, your tool call sequence MUST be:
        Round 1: assessSymptoms (confirm danger signs)
        Round 2: classifyTriage (urgency=RED) + generateReferralNote
        STOP. Do not request vitals. Do not check medications.

        SEX-SPECIFIC CLINICAL GUIDANCE:

        CHILD_U5 pathway:
        - Patient sex is clinically relevant. Male infants under 12 months have a
          higher baseline risk for sepsis and respiratory failure; apply a lower
          threshold for vital sign collection when sex is MALE and age is under
          12 months with respiratory or fever symptoms.
        - Include the patient's sex explicitly in the patientSummary field of
          generateReferralNote (e.g. "Male, 8 months, CHILD_U5").
        - If patientSex is FEMALE and age is under 5, assess normally per IMCI.

        ANTENATAL pathway:
        - This pathway is exclusively for pregnant women. Patient sex is always
          Female — it is derived from the pathway, not entered separately.
        - Use gestational age to weight symptom severity: reduced fetal movement,
          severe headache, and visual disturbance carry higher urgency after
          28 weeks gestation.
        - Always include "Female" and gestational age in the referral note
          patientSummary (e.g. "Female, 32 weeks gestation, ANTENATAL").

        RULES:
        - You MUST call at least assessSymptoms and classifyTriage.
        - You MUST call generateReferralNote as the final tool call.
        - Never respond with plain text. Always use tool calls.
        - Maximum 4 rounds of tool calls per assessment.
        - Treat any confirmed visual finding from the CHO as already-validated clinical context.
        - Do not request vital signs by default. Request them only if they materially improve the triage decision.
        - If ANY WHO-defined danger sign is present, classify as RED regardless of other factors.
        - Be specific in your referral notes and include all clinically relevant findings.
        - Always include patient sex in the referral note patient summary line.

        CHILD_U5 automatic RED danger signs: unable to drink or breastfeed, vomits
        everything, convulsions, lethargic or unconscious, stridor at rest,
        severe chest indrawing.

        ANTENATAL automatic RED danger signs: heavy vaginal bleeding, severe headache
        with visual disturbance, fits or convulsions, fetal movements stopped,
        cord prolapse, prolonged labour >24 hours.
    """.trimIndent()

    fun buildVisualPrompt(
        visualCue: VisualCue,
        selectedSymptoms: List<String>,
        pathway: Pathway
    ): String = """
        Patient context:
        Pathway: ${pathway.name}
        Reported symptoms: ${selectedSymptoms.joinToString(", ")}

        Visual assessment requested:
        ${visualCue.gemmaInstruction}

        Using the reportVisualFinding tool, report:
        1. Whether you can see the specific sign described
        2. Your confidence level (high/medium/low)
        3. The clinical implication if the sign is present
        4. Suggested addition to the referral note
    """.trimIndent()

    fun buildUserPrompt(input: TriageInput): String {
        val visualSection = when {
            input.confirmedVisualFinding != null -> when (input.confirmedVisualFinding.findingType) {
                FindingType.ESCALATING -> """
                    Visual finding (CONFIRMED by CHO - ESCALATING):
                    ${input.confirmedVisualFinding.referralNoteText}
                    This finding may escalate triage severity. Factor it into your classifyTriage decision.
                    Include in referral note with a specific follow-up recommendation.
                    Confidence: ${input.confirmedVisualFinding.confidence.name}
                """.trimIndent()

                FindingType.CONFIRMING -> """
                    Visual finding (CONFIRMED by CHO - CONFIRMING):
                    ${input.confirmedVisualFinding.referralNoteText}
                    This visual finding confirms a symptom already reported. Include in referral note as supporting evidence.
                    Confidence: ${input.confirmedVisualFinding.confidence.name}
                """.trimIndent()

                FindingType.NEW_OBSERVATION -> """
                    Visual observation (CONFIRMED by CHO - NEW):
                    ${input.confirmedVisualFinding.referralNoteText}
                    This finding was not on the symptom checklist. Include in referral note as an additional clinical observation.
                    Confidence: ${input.confirmedVisualFinding.confidence.name}
                """.trimIndent()
            }

            input.photoUri != null -> "A clinical photo was reviewed before assessment and no abnormal visual sign was confirmed. Do not treat the image as a danger sign, but you may mention the negative visual review neutrally if relevant."
            else -> ""
        }

        val ageUnit = if (input.pathway == Pathway.CHILD_U5) "months" else "weeks gestation"
        val ageDisplay = if (input.patientAge.isNotEmpty()) "${input.patientAge} $ageUnit" else "Not specified"
        // ANTENATAL is definitionally female — derive from pathway rather than a user field
        val sexDisplay = if (input.pathway == Pathway.ANTENATAL) "Female"
                         else input.patientSex.ifEmpty { "Not specified" }

        return """
            Patient assessment:
            Pathway: ${input.pathway.name}
            Patient age: $ageDisplay
            Patient sex: $sexDisplay
            Presenting symptoms: ${input.symptoms.joinToString(", ")}
            Current medications: ${input.medications.ifBlank { "None reported" }}
            $visualSection
            Please assess using the clinical tools.
        """.trimIndent()
    }

    fun buildVitalsFollowUpPrompt(vitalSigns: Map<String, String>): String {
        val vitalsText = vitalSigns.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return """
            The CHO has provided the requested vital signs:
            $vitalsText
            Continue the assessment. Call classifyTriage with the enriched data,
            then call generateReferralNote.
        """.trimIndent()
    }
}
