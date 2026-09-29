package com.triagemate.chps.domain.safety

import com.triagemate.chps.domain.model.Pathway
import com.triagemate.chps.util.AUTO_RED_ANTENATAL
import com.triagemate.chps.util.AUTO_RED_CHILD_U5
import com.triagemate.chps.util.CameraTier
import com.triagemate.chps.util.Constants
import com.triagemate.chps.util.VisualCueMapper
import com.triagemate.chps.util.autoRedSignsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardrailTest {

    private fun checklistFor(pathway: Pathway) = when (pathway) {
        Pathway.CHILD_U5 -> Constants.CHILD_U5_SYMPTOMS
        Pathway.ANTENATAL -> Constants.ANTENATAL_SYMPTOMS
    }

    @Test
    fun everyAutoRedSign_isAnExactChecklistLabel() {
        Pathway.entries.forEach { pathway ->
            val missing = autoRedSignsFor(pathway) - checklistFor(pathway).toSet()
            assertTrue("$pathway auto-RED signs not on checklist: $missing", missing.isEmpty())
        }
    }

    @Test
    fun autoRedSets_matchExpectedClinicalSigns() {
        assertEquals(
            setOf(
                "Unable to drink or breastfeed", "Vomiting everything", "Convulsions",
                "Lethargic or unconscious", "Severe chest indrawing", "Stridor"
            ),
            AUTO_RED_CHILD_U5
        )
        assertEquals(
            setOf(
                "Vaginal bleeding", "Convulsions / fits",
                "Absent fetal movement", "Prolonged labour (>24h)"
            ),
            AUTO_RED_ANTENATAL
        )
    }

    @Test
    fun eachAutoRedChecklistLabel_overridesNonRedModelOutput() {
        Pathway.entries.forEach { pathway ->
            autoRedSignsFor(pathway).forEach { sign ->
                listOf("GREEN", "AMBER").forEach { modelUrgency ->
                    val result = SafetyGuardrail.apply(modelUrgency, listOf(sign), pathway)
                    assertEquals("$pathway/$sign/$modelUrgency", "RED", result.finalUrgency)
                    assertTrue(result.wasOverridden)
                    assertEquals(modelUrgency, result.originalGemmaUrgency)
                    assertEquals(listOf(sign), result.overriddenSigns)
                }
            }
        }
    }

    @Test
    fun nonAutoRedChecklistLabels_neverOverride() {
        Pathway.entries.forEach { pathway ->
            (checklistFor(pathway) - autoRedSignsFor(pathway)).forEach { symptom ->
                val result = SafetyGuardrail.apply("GREEN", listOf(symptom), pathway)
                assertEquals("$pathway/$symptom", "GREEN", result.finalUrgency)
                assertFalse(result.wasOverridden)
            }
        }
    }

    @Test
    fun redModelOutput_isNotReportedAsOverride() {
        val result = SafetyGuardrail.apply("RED", listOf("Vaginal bleeding"), Pathway.ANTENATAL)
        assertEquals("RED", result.finalUrgency)
        assertFalse(result.wasOverridden)
    }

    @Test
    fun matching_ignoresCaseAndWhitespace() {
        val result = SafetyGuardrail.apply("GREEN", listOf("  stridor "), Pathway.CHILD_U5)
        assertTrue(result.wasOverridden)
    }

    @Test
    fun signsAreScopedToTheirPathway() {
        // "Convulsions" is the child label; the antenatal label is "Convulsions / fits".
        val result = SafetyGuardrail.apply("GREEN", listOf("Convulsions"), Pathway.ANTENATAL)
        assertFalse(result.wasOverridden)
    }

    @Test
    fun cameraIsSuppressedForEveryAutoRedSign() {
        Pathway.entries.forEach { pathway ->
            autoRedSignsFor(pathway).forEach { sign ->
                val tier = VisualCueMapper.computeTier(listOf(sign), pathway, patientAgeMonths = 12)
                assertEquals("$pathway/$sign", CameraTier.SuppressCamera, tier)
            }
        }
    }
}
