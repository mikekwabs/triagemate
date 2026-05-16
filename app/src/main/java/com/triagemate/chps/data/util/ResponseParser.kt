package com.triagemate.chps.data.util

import android.util.Log
import com.triagemate.chps.domain.model.TriageResult
import org.json.JSONObject

object ResponseParser {

    private const val TAG = "ResponseParser"

    fun parseToolArguments(arguments: String): TriageResult? {
        Log.d(TAG, "parseToolArguments: input='$arguments'")
        return try {
            val json = JSONObject(arguments)
            val urgency = json.optString("urgency", "AMBER")
            val action = json.optString("action", "No action provided")
            val referralNote = json.optString("referral_note", "No note provided")

            val dangerSignsRaw = json.optString("danger_signs_detected", "")
            val dangerSignsList = if (dangerSignsRaw.isNotEmpty()) {
                dangerSignsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                val array = json.optJSONArray("danger_signs_detected")
                if (array != null) {
                    List(array.length()) { array.getString(it) }
                } else {
                    emptyList()
                }
            }

            Log.d(TAG, "parseToolArguments: parsed urgency=$urgency action='$action' dangerSigns=$dangerSignsList")
            TriageResult(
                urgency = urgency,
                action = action,
                dangerSignsDetected = dangerSignsList,
                referralNote = referralNote,
                rawJson = arguments
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseToolArguments: FAILED to parse — ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    fun parseFallbackResponse(text: String): TriageResult {
        Log.w(TAG, "parseFallbackResponse: model did not call tool — rawText='$text'")
        return TriageResult(
            urgency = "AMBER",
            action = "Unable to parse result — use clinical judgement",
            dangerSignsDetected = emptyList(),
            referralNote = "The AI response could not be structured automatically. Raw output: $text",
            rawJson = text
        )
    }
}
