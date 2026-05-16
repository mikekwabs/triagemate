# Project Plan

TriageMate: an offline-first maternal and child health triage companion for Ghana's CHPS Community Health Officers, powered by Gemma 4 E2B running entirely on-device via the NEW LiteRT-LM (com.google.ai.edge.litertlm) API.

## Project Brief

# TriageMate Project Brief

TriageMate is an offline-first maternal and child health triage companion designed for Community Health Officers (CHOs) in Ghana’s CHPS zones. It leverages the latest on-device AI to provide immediate clinical decision support, ensuring high-quality care even in the most remote, disconnected environments.

## Features
- **On-Device AI Triage:** Conducts clinical assessments for Child Under 5 and Antenatal pathways using Gemma 4 E2B, powered by the LiteRT-LM engine for 100% offline inference.
- **Protocol-Driven Checklists:** Guided symptom checklists based on WHO IMCI logic to help health workers collect accurate and comprehensive patient data.
- **Structured Triage Outputs:** Automatically generates urgency levels (RED/AMBER/GREEN), recommended actions, and referral notes using the `classifyTriage` AI tool calling mechanism.
- **Local Assessment History:** Persists all triage sessions securely on the device, allowing health officers to review past assessments and track patient outcomes without internet.

## High-Level Technical Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3) with full Edge-to-Edge display support.
- **AI Inference:** LiteRT-LM (`com.google.ai.edge.litertlm`) using `Engine`, `Conversation`, and `ToolSet` with manual tool call handling (`automaticToolCalling = false`).
- **AI Tooling:** Native `@Tool` and `@ToolParam` annotations for structured clinical output.
- **Persistence:** Room (utilizing **KSP** for efficient code generation).
- **Dependency Injection:** Hilt.
- **Architecture:** MVVM with Clean Architecture (Data, Domain, Presentation layers).
- **Concurrency:** Kotlin Coroutines and Flow for non-blocking UI and background AI initialization.

## Implementation Steps
**Total Duration:** 54m 48s

### Task_1_Foundation_and_AI_Core: Initialize the project core infrastructure by setting up Hilt for dependency injection, Room for local storage, and the LiteRT-LM engine for on-device AI. Implement the `classifyTriage` AI tool using @Tool and @ToolParam annotations.
- **Status:** COMPLETED
- **Updates:** Hilt, Room, and LiteRT-LM engine infrastructure have been initialized. TriageToolSet with @Tool annotations, Constants, and PromptBuilder are implemented. Project compiles and foundational DI is set up.
- **Acceptance Criteria:**
  - Hilt is correctly configured and app builds
  - Room database is initialized
  - LiteRT-LM engine and Triage tool are defined
  - Project compiles without errors
- **Duration:** 54m 48s

### Task_2_Data_Persistence_and_AI_Logic: Implement the Data and Domain layers, including the Triage Repository and manual tool call handling logic for LiteRT-LM. Ensure that triage sessions can be saved and retrieved from the Room database.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Triage sessions are persisted in Room
  - Manual tool call handling logic correctly processes AI outputs
  - Repository provides a clean API for the ViewModel
- **StartTime:** 2026-04-27 16:51:21 GMT

### Task_3_Triage_UI_and_Checklists: Build the Jetpack Compose UI for the triage assessment flow, including protocol-driven checklists for Child Under 5 and Antenatal pathways. Integrate the AI engine to provide real-time clinical decision support and display structured triage outputs (RED/AMBER/GREEN).
- **Status:** PENDING
- **Acceptance Criteria:**
  - Triage checklists are functional and follow WHO IMCI logic
  - AI-generated urgency levels and recommendations are displayed correctly
  - UI follows Material 3 guidelines and supports Edge-to-Edge display

### Task_4_History_Theming_and_Verification: Implement the assessment history screen to allow CHOs to review past triage sessions. Apply a vibrant Material 3 color scheme, create an adaptive app icon, and perform a final run and verify of the entire application.
- **Status:** PENDING
- **Acceptance Criteria:**
  - History screen displays list of past assessments with details
  - App uses a vibrant M3 color scheme and has an adaptive icon
  - Final Run and Verify: App is stable, no crashes, and meets all project brief requirements

