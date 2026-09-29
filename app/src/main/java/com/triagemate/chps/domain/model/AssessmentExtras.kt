package com.triagemate.chps.domain.model

enum class RdtResult(val label: String) {
    POSITIVE("positive"),
    NEGATIVE("negative"),
    NOT_DONE("not done")
}

/** Urine dipstick protein, in increasing order so `>=` comparisons work. */
enum class UrineProtein(val label: String) {
    NOT_DONE("not done"),
    NEGATIVE("negative"),
    TRACE("trace"),
    PLUS1("+"),
    PLUS2("++"),
    PLUS3("+++")
}

/**
 * Optional structured findings from the "TriageMate Assessment Additions" review document
 * (sections D and E). Every field is nullable: null means "not recorded". Durations only
 * count when the matching symptom is ticked; [forSymptoms] drops the rest.
 */
data class AssessmentExtras(
    val feverDays: Int? = null,
    val coughDays: Int? = null,
    val diarrhoeaDays: Int? = null,
    val malariaRdt: RdtResult? = null,
    val oedemaBothFeet: Boolean? = null,
    val muacMm: Int? = null,
    val urineProtein: UrineProtein? = null
) {
    val isEmpty: Boolean get() = this == AssessmentExtras()

    /** Drops durations and the RDT for symptoms that are no longer ticked. */
    fun forSymptoms(pathway: Pathway, symptoms: Collection<String>): AssessmentExtras {
        val fever = ChecklistLabels.fever(pathway) in symptoms
        return copy(
            feverDays = feverDays.takeIf { fever },
            coughDays = coughDays.takeIf { pathway == Pathway.CHILD_U5 && ChecklistLabels.COUGH in symptoms },
            diarrhoeaDays = diarrhoeaDays.takeIf { pathway == Pathway.CHILD_U5 && ChecklistLabels.DIARRHOEA in symptoms },
            malariaRdt = malariaRdt.takeIf { fever },
            oedemaBothFeet = oedemaBothFeet.takeIf { pathway == Pathway.CHILD_U5 },
            muacMm = muacMm.takeIf { pathway == Pathway.CHILD_U5 },
            urineProtein = urineProtein.takeIf { pathway == Pathway.ANTENATAL }
        )
    }

    /** One line for the model prompt and the referral note, or null when nothing was recorded. */
    fun summary(): String? {
        val parts = buildList {
            feverDays?.let { add("fever for $it day${if (it == 1) "" else "s"}") }
            coughDays?.let { add("cough for $it day${if (it == 1) "" else "s"}") }
            diarrhoeaDays?.let { add("diarrhoea for $it day${if (it == 1) "" else "s"}") }
            malariaRdt?.let { add("malaria RDT ${it.label}") }
            oedemaBothFeet?.let { add("oedema of both feet: ${if (it) "yes" else "no"}") }
            muacMm?.let { add("MUAC $it mm") }
            urineProtein?.let { add("urine protein ${it.label}") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}

/** Checklist labels the additional findings attach to. Must match [com.triagemate.chps.util.Constants]. */
object ChecklistLabels {
    const val CHILD_FEVER = "Fever"
    const val COUGH = "Fast breathing / cough"
    const val DIARRHOEA = "Diarrhoea"
    const val BLOOD_IN_STOOL = "Blood in stool"
    const val ANTENATAL_FEVER = "High fever"
    const val SEVERE_HEADACHE = "Severe headache"
    const val BLURRED_VISION = "Blurred or lost vision"
    const val SEVERE_ABDOMINAL_PAIN = "Severe abdominal pain"
    const val LEAKING_FLUID = "Leaking fluid from the vagina"

    fun fever(pathway: Pathway): String = when (pathway) {
        Pathway.CHILD_U5 -> CHILD_FEVER
        Pathway.ANTENATAL -> ANTENATAL_FEVER
    }
}
