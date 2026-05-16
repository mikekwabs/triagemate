package com.triagemate.chps.util

import com.triagemate.chps.domain.model.TriageResult
import org.json.JSONObject

object JsonParser {
    fun parseTriageResult(input: String): TriageResult {
        val jsonString = extractJson(input)
        return try {
            val json = JSONObject(jsonString)
            TriageResult(
                urgency = json.optString("urgency", "AMBER"),
                action = json.optString("action", "Unable to parse result — use clinical judgement"),
                dangerSignsDetected = parseList(json, "danger_signs_detected"),
                referralNote = json.optString("referral_note", ""),
                rawJson = input
            )
        } catch (e: Exception) {
            TriageResult(
                urgency = "AMBER",
                action = "Unable to parse result — use clinical judgement",
                dangerSignsDetected = emptyList(),
                referralNote = "Error parsing AI response. Raw output: $input",
                rawJson = input
            )
        }
    }

    private fun extractJson(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("```json")) {
            trimmed.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (trimmed.startsWith("```")) {
            trimmed.substringAfter("```").substringBeforeLast("```").trim()
        } else {
            trimmed
        }
    }

    private fun parseList(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getString(it) }
    }
}
