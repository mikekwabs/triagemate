# TriageMate Project Index

## Snapshot

- Project type: single-module offline Android app (`:app`)
- Product goal: offline-first maternal and child triage assistant for Ghana CHPS Community Health Officers
- Core runtime: Kotlin, Jetpack Compose, Hilt, Room, Moshi, LiteRT-LM
- Current app package: `com.triagemate.chps`
- Current compile/target SDK: 35
- Current min SDK: 26
- Current local source size: 52 Kotlin files, about 3,872 lines under `app/src/main/java`
- Git status: no `.git` repository is present in the current folder, so history/branches cannot be inspected from here

## What The App Currently Does

TriageMate currently supports a full offline triage flow for two pathways:

- `CHILD_U5`
- `ANTENATAL`

The user journey is:

1. Launch app.
2. If the Gemma LiteRT-LM model file is missing, show a one-time download/setup flow.
3. Once the model exists, land on a home screen with two assessment pathways.
4. Open an assessment form, choose symptoms, optionally enter age and medications.
5. Run on-device AI triage using a tool-enabled LiteRT-LM conversation.
6. If the model requests vital signs, show a bottom sheet for manual input.
7. Re-run the assessment with supplied vitals.
8. Save the result locally in Room.
9. Show a result screen with urgency, action, danger signs, vitals, and a shareable referral note.
10. Allow browsing saved history and reopening prior result records.

## Current Architecture

The project mostly follows a lightweight Clean Architecture / MVVM split:

- `presentation`: Compose screens, UI components, navigation, view models
- `domain`: models, repository interfaces, use cases
- `data`: repository implementations, LiteRT engine wrapper, Room DAO/entity/database, response helpers
- `di`: Hilt bindings/providers
- `tools`: LiteRT-LM tool definitions exposed to the model
- `util`: prompts, constants, JSON helpers

The layering is real, but intentionally thin:

- Use cases are mostly pass-through wrappers.
- Repository interfaces are meaningful boundaries.
- ViewModels orchestrate most workflow decisions.

## Module And Build Setup

### Gradle structure

- Root includes only `:app`
- No feature modules, library modules, or shared modules
- Version catalog is used via `gradle/libs.versions.toml`

### Key dependencies

- Compose Material 3 + Navigation Compose
- Hilt + Hilt navigation Compose
- Room + KSP
- Moshi
- LiteRT-LM Android
- CameraX dependencies are included
- Kotlin serialization is included

### Important build observations

- `litertlm` version is pinned as `"+"`, so the app depends on the latest available LiteRT-LM at resolution time rather than a fixed version.
- CameraX and serialization are present but not materially used in the active feature flow.
- Compose is enabled and Java/Kotlin target JVM 17.

## Android Manifest And Runtime Assumptions

The manifest is minimal:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `MainActivity`
- `TriageMateApp` as the `Application`
- optional native library declarations for OpenCL/GPU support

Even though the app is described as offline, `INTERNET` is still required because the first-run model download uses `DownloadManager`.

## Entry Points

### Application

[`app/src/main/java/com/triagemate/chps/TriageMateApp.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\TriageMateApp.kt) only initializes Hilt via `@HiltAndroidApp`.

### Activity

[`app/src/main/java/com/triagemate/chps/MainActivity.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\MainActivity.kt) is a standard Compose host:

- edge-to-edge enabled
- wraps content in `TriageMateTheme`
- creates nav controller
- mounts `AppNavGraph`

### Navigation

[`app/src/main/java/com/triagemate/chps/presentation/navigation/AppNavGraph.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\navigation\AppNavGraph.kt) drives all routing:

- `model_setup`
- `home`
- `assessment/{pathway}`
- `result/{id}`
- `history`

Important behavior:

- Start destination is decided by checking whether the model file exists in app external files.
- There is no splash screen or async bootstrap state.
- A downloaded model file is treated as the only readiness gate.

## Dependency Injection Graph

Hilt wiring is small and straightforward:

- `AppModule`: provides `DownloadManager`
- `DatabaseModule`: provides `TriageMateDatabase` and `AssessmentDao`
- `MoshiModule`: provides `Moshi`
- `RepositoryModule`: binds repository interfaces to implementations
- `InferenceModule`: effectively a placeholder; `EngineProvider` is constructor-injected directly

This means the key singleton graph is:

- `EngineProvider`
- `ModelDownloadRepositoryImpl`
- `AssessmentRepositoryImpl`
- `InferenceRepositoryImpl`
- `TriageMateDatabase`

## Domain Model Inventory

### Core business inputs/outputs

- `TriageInput`
  - pathway
  - symptom list
  - presenting complaint
  - optional `photoUri`
  - patient age
  - patient sex
  - medications

- `TriageResult`
  - urgency
  - recommended action
  - detected danger signs
  - referral note
  - vital signs map
  - tool call log
  - raw JSON

### Agentic workflow models

- `AgenticTriageResult`
  - `COMPLETE`
  - `AWAITING_VITALS`
  - `ERROR`
  - optional triage result
  - referral note
  - required vitals
  - supplied vitals
  - drug interaction result
  - tool call log
  - current round

- `ToolCallRecord`
  - round
  - tool name
  - arguments
  - timestamp
  - result

### Supporting models

- `Pathway`
- `HistoryEntry`
- `DownloadState`

## Data Layer

### EngineProvider

[`app/src/main/java/com/triagemate/chps/data/engine/EngineProvider.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\engine\EngineProvider.kt) owns the LiteRT-LM `Engine`.

What it does:

- checks for model presence on disk
- chooses GPU only on devices with at least about 10 GB total RAM
- falls back from GPU to CPU if GPU engine creation fails
- initializes the engine lazily on `Dispatchers.IO`
- uses a `Mutex` to prevent double initialization

What that implies:

- inference is truly on-device
- backend choice is device-sensitive
- the engine survives as a singleton for the app process lifetime

### Model download

[`app/src/main/java/com/triagemate/chps/data/repository/ModelDownloadRepositoryImpl.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\repository\ModelDownloadRepositoryImpl.kt) handles first-run model acquisition.

Behavior:

- downloads `gemma-4-E2B-it.litertlm`
- stores it in app external files directory
- persists `DownloadManager` ID in `SharedPreferences`
- polls status every second using a `Flow`
- surfaces `Idle`, `Enqueued`, `Downloading`, `Completed`, `Failed`

This is the only networked step in the user-facing product flow.

### Room persistence

#### Database

- database name: `triagemate_db`
- database class: `TriageMateDatabase`
- current schema version: `3`

#### DAO

`AssessmentDao` supports:

- insert assessment
- fetch all assessments as `Flow`
- fetch by ID
- delete all assessments

#### Entity evolution

Schema history shows feature growth:

- Version 1:
  - basic assessment result fields only
- Version 2:
  - added `patient_age`
  - added `symptoms_json`
- Version 3:
  - added `vital_signs_json`
  - added `drug_interaction_json`
  - added `tool_call_log_json`
  - added `agent_rounds`

Current `AssessmentEntity` stores:

- pathway
- patient age
- symptoms JSON
- presenting complaint
- danger signs JSON
- vital signs JSON
- drug interaction JSON
- urgency
- action
- referral note
- tool call log JSON
- agent rounds

### Assessment repository

[`app/src/main/java/com/triagemate/chps/data/repository/AssessmentRepositoryImpl.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\repository\AssessmentRepositoryImpl.kt) maps between domain objects and Room.

What it persists well:

- triage summary
- vitals
- pathway
- symptoms
- audit metadata

What it does not reconstruct on read:

- `drugInteractionJson`
- `toolCallLogJson`
- `agentRounds`
- `presentingComplaint`

Those fields are saved, but not surfaced back through `getAssessmentById()`. So the result screen only sees a partial projection of the stored record.

## Inference Layer

### Clinical tool set

[`app/src/main/java/com/triagemate/chps/tools/ClinicalToolSet.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\tools\ClinicalToolSet.kt) is the current heart of the AI workflow.

Available model tools:

1. `assessSymptoms`
2. `requestVitalSigns`
3. `checkDrugInteraction`
4. `classifyTriage`
5. `generateReferralNote`

The tool set is stateful and accumulates:

- classified urgency
- classified action
- classified danger signs
- generated referral note
- whether vitals were requested
- which vitals were requested
- drug interaction result
- per-call log
- round counter

Important implementation detail:

- `record()` stores tool name, arguments, and timestamp.
- It does not currently store the tool result payload back into `ToolCallRecord.result`.

That matters because parts of the UI try to infer progress from tool results later.

### Inference repository

[`app/src/main/java/com/triagemate/chps/data/repository/InferenceRepositoryImpl.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\repository\InferenceRepositoryImpl.kt) runs the tool-enabled LiteRT-LM workflow.

What the current implementation actually does:

- ensures the engine is ready
- resets the tool set each run
- optionally injects previously collected vitals into the tool set
- creates a new LiteRT-LM conversation
- enables `automaticToolCalling = true`
- sends a single prompt
- closes the conversation
- reads all outcome state from `ClinicalToolSet`
- returns `AWAITING_VITALS`, `COMPLETE`, or `ERROR`

This means the current Moat 1 behavior is:

- first pass can request vitals
- second pass is not a resumed conversation
- it is a fresh conversation seeded with the same input plus follow-up vitals prompt

The repository keeps only one in-memory resumption anchor:

- `lastInput`

If the process dies between first pass and vitals submission, there is no persisted partial session to resume.

### Prompting

[`app/src/main/java/com/triagemate/chps/util/PromptBuilder.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\util\PromptBuilder.kt) defines:

- a system prompt describing required tool workflow
- a user prompt built from pathway, age, sex, and symptoms
- a follow-up prompt for collected vitals

The prompt enforces:

- `assessSymptoms` first
- `classifyTriage` required
- `generateReferralNote` last
- no plain-text responses
- max 3 rounds

## Presentation Layer

### 1. Model setup flow

Files:

- [`presentation/screens/setup/ModelSetupViewModel.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\setup\ModelSetupViewModel.kt)
- [`presentation/screens/setup/ModelSetupScreen.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\setup\ModelSetupScreen.kt)

Capabilities:

- start download
- cancel download
- observe progress
- initialize engine after download completion
- transition to home

UI quality is high here: this is one of the most fully designed screens in the app.

### 2. Home screen

Files:

- [`presentation/screens/home/HomeScreen.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\home\HomeScreen.kt)
- [`presentation/screens/home/HomeViewModel.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\home\HomeViewModel.kt)

What it provides:

- offline-ready banner
- pathway selection
- link to history
- decorative stats cards
- top and bottom app chrome

Observations:

- `HomeViewModel` currently has no logic.
- stats are static placeholders, not backed by persisted data.

### 3. Assessment screen

Files:

- [`presentation/screens/assessment/AssessmentViewModel.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\assessment\AssessmentViewModel.kt)
- [`presentation/screens/assessment/AssessmentScreen.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\assessment\AssessmentScreen.kt)
- [`presentation/components/SymptomCheckItem.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\components\SymptomCheckItem.kt)
- [`presentation/components/VitalSignsSheet.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\components\VitalSignsSheet.kt)

This is the main workflow screen.

What it supports:

- pathway-specific symptom list
- age entry
- free-text medications entry
- symptom selection cards with severity styling
- start assessment
- progress stepper
- progress card during AI workflow
- modal bottom sheet for vitals entry

Runtime state captured by `AssessmentUiState`:

- selected symptoms
- age
- medications
- loading state
- error state
- awaiting vitals
- required vitals
- typed vital values
- tool call log
- current round
- computed danger-sign count
- assessment start time

Important workflow behavior:

- symptom selection is required before assessment can start
- no patient sex input is collected
- no photo input is collected
- vitals can be submitted or skipped
- skip currently calls the same `submitVitals()` path, so a skip is really a resume with an empty vitals map

### 4. Result screen

Files:

- [`presentation/screens/result/ResultViewModel.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\result\ResultViewModel.kt)
- [`presentation/screens/result/ResultScreen.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\result\ResultScreen.kt)

What it shows:

- urgency banner
- recommended action
- danger sign chips
- vital sign tiles
- expandable referral note
- share via Android share intent
- start new assessment

What it does not show even though the app stores it:

- tool call audit trail
- round count
- pathway
- patient age
- medications
- drug interaction output

### 5. History screen

Files:

- [`presentation/screens/history/HistoryViewModel.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\history\HistoryViewModel.kt)
- [`presentation/screens/history/HistoryScreen.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\history\HistoryScreen.kt)

Capabilities:

- list prior assessments
- show pathway, age, symptom preview, urgency color, timestamp
- navigate back into saved result

History is present and usable, but intentionally lightweight.

## Active Clinical Scope

### Implemented pathways

- Child Under 5
- Antenatal

### Symptom checklists

Current hardcoded checklists live in `Constants.kt`.

Child U5:

- Fever
- Unable to drink or breastfeed
- Vomiting everything
- Convulsions
- Lethargic or unconscious
- Fast breathing / cough
- Diarrhoea
- Severe chest indrawing
- Stridor

Antenatal:

- Vaginal bleeding
- Convulsions / fits
- Absent fetal movement
- Blurred or lost vision
- Severe headache
- Difficulty breathing
- Swollen face, hands or feet
- High fever
- Prolonged labour (>24h)

### Clinical logic placement

Clinical logic is currently distributed across:

- prompt instructions
- symptom ordering and labeling
- tool descriptions
- some UI severity styling

There is not yet a separate formal protocol rules engine in Kotlin.

## Moat 1 Status Versus The Current Code

The provided Moat 1 summary is directionally right but no longer perfectly matches source.

### Confirmed present

- `ClinicalToolSet`
- `AgenticTriageResult`
- `ToolCallRecord`
- `VitalSignsSheet`
- Room version 3
- `AssessmentViewModel` support for `AWAITING_VITALS`
- `PromptBuilder` agentic prompt

### Important drift from the summary

The summary says:

- agentic loop with max 3 rounds
- conversation pause/resume
- full agentic audit trail

The current implementation instead:

- relies on LiteRT-LM automatic tool calling rather than explicit manual loop control
- does not maintain a live paused conversation object
- re-runs a fresh conversation with follow-up vitals
- stores tool call metadata, but not the per-call returned result content

So Moat 1 is implemented in spirit, but simplified in mechanics.

## Planned Scope Versus Delivered Scope

A local planning file exists at [`.agent/plan.md`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\.agent\plan.md).

It describes the original plan as:

- foundation and AI core
- data persistence and AI logic
- triage UI and checklists
- history, theming, and verification

Interesting mismatch:

- the plan still describes manual tool handling with `automaticToolCalling = false`
- current code uses `automaticToolCalling = true`
- the plan says later tasks were pending, but most of those features now exist in source

This suggests the codebase has advanced beyond the tracked plan, but the plan document was not kept current.

## Source Inventory By Area

### Core app shell

- `MainActivity.kt`
- `TriageMateApp.kt`
- `AppNavGraph.kt`

### DI

- `AppModule.kt`
- `DatabaseModule.kt`
- `InferenceModule.kt`
- `MoshiModule.kt`
- `RepositoryModule.kt`

### Data

- `EngineProvider.kt`
- `AssessmentDao.kt`
- `TriageMateDatabase.kt`
- `AssessmentEntity.kt`
- `AssessmentRepositoryImpl.kt`
- `InferenceRepositoryImpl.kt`
- `ModelDownloadRepositoryImpl.kt`
- `ResponseParser.kt`

### Domain

- `AgenticTriageResult.kt`
- `DownloadState.kt`
- `HistoryEntry.kt`
- `Pathway.kt`
- `ToolCallRecord.kt`
- `TriageInput.kt`
- `TriageResult.kt`
- repository interfaces
- 3 use cases

### Tools and prompts

- `ClinicalToolSet.kt`
- `TriageToolSet.kt`
- `Constants.kt`
- `JsonParser.kt`
- `PromptBuilder.kt`

### Presentation

- setup screen + VM
- home screen + VM
- assessment screen + VM
- result screen + VM
- history screen + VM
- 5 reusable UI component files
- theme files

## Things That Are Implemented But Not Fully Used

### Legacy or effectively unused files

- `TriageToolSet.kt`
  - old single-tool approach
  - no active references found

- `ModelSetupCard.kt`
  - no active references found

- `ReferralNoteCard.kt`
  - no active references found

- `UrgencyBadge.kt`
  - no active references found

- `JsonParser.kt`
  - no active references found

- `ResponseParser.kt`
  - no active references found

### Data fields with weak end-to-end usage

- `photoUri` exists in `TriageInput`, but there is no photo capture or upload UI
- `patientSex` exists in `TriageInput`, but there is no sex input in the assessment form
- `drugInteractionJson`, `toolCallLogJson`, and `agentRounds` are stored, but not surfaced in read models/UI
- `presentingComplaint` is saved, but not shown later

### Dependencies ahead of product scope

- CameraX is present in Gradle but not wired into the active assessment flow
- Kotlin serialization is included, but the current code mostly uses `JSONObject` and Moshi

## Notable Technical Gaps Or Risks

### 1. Tool result logging is incomplete

`ToolCallRecord` has a `result` field, but `ClinicalToolSet.record()` never populates it.

Impact:

- progress reconstruction is weaker than intended
- `extractDangerSignCount()` in `AssessmentViewModel` tries to read `assessSymptoms.result`, but that result is currently never recorded, so the danger sign count will usually be `0`

### 2. Resume semantics are process-local

`resumeWithVitals()` depends on `lastInput` stored in memory.

Impact:

- if the app process is killed while waiting for vitals, the session cannot truly resume

### 3. Stored audit fields are not exposed back to the UI

The database stores richer data than the result/history screens display.

Impact:

- the app has audit potential, but currently surfaces only a simplified view

### 4. Theme layering is mixed

- Compose app uses `TriageMateTheme`
- XML theme parent is `Theme.MaterialComponents.Light`

This works, but it is a hybrid setup rather than a fully Compose-native theme stack.

### 5. Backup XML files are still stock templates

`backup_rules.xml` and `data_extraction_rules.xml` still contain sample comments/TODOs.

### 6. Version drift risk from `litertlm = "+"`

LiteRT-LM API behavior may change between resolutions, especially relevant because Moat 1 notes already mention API constructor uncertainty.

## Testing And Verification State

Only scaffold tests are present:

- `ExampleUnitTest.kt`
- `ExampleInstrumentedTest.kt`

There are no real tests for:

- inference workflow
- prompt/tool integration
- Room repository behavior
- ViewModel state transitions
- migration validation

This means the app is currently code-driven rather than test-driven.

## Design And UI Maturity

The UI is noticeably more mature than a raw prototype:

- setup flow is polished
- home screen has branded structure
- assessment screen has pathway-specific checklists and progress UX
- result screen is presentation-ready
- history screen is coherent

The visual language is consistent:

- navy primary brand
- traffic-light urgency colors
- teal progress accents
- Inter font family

## Practical Scope Summary

Today, the codebase is best described as:

An offline-first, single-module Android Compose app that already ships the complete skeleton of a maternal/child triage product, including model setup, on-device LiteRT-LM inference, agentic tool-calling triage, vitals follow-up, local persistence, result presentation, and history review, but still has a few unfinished edges around audit completeness, resumption robustness, test coverage, and cleanup of legacy scaffolding.

## Highest-Value Next Steps

If the goal is to harden the current product rather than add new surface area, the most valuable next moves would be:

1. Record actual tool result payloads into `ToolCallRecord.result`.
2. Surface saved audit data and drug interaction info in result/history detail views.
3. Decide whether Moat 1 should truly pause/resume a conversation or officially keep the current re-run model.
4. Remove or repurpose legacy unused files (`TriageToolSet`, old helper components/parsers).
5. Add real tests around:
   - `AssessmentRepositoryImpl`
   - `AssessmentViewModel`
   - `InferenceRepositoryImpl` state behavior
6. Either wire up `patientSex` and `photoUri`, or remove them from the domain model until needed.
7. Pin LiteRT-LM to a fixed version.

## Files Most Central To Understanding The Project

If you want the shortest path back into the code later, start here:

- [`app/src/main/java/com/triagemate/chps/presentation/navigation/AppNavGraph.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\navigation\AppNavGraph.kt)
- [`app/src/main/java/com/triagemate/chps/presentation/screens/assessment/AssessmentViewModel.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\assessment\AssessmentViewModel.kt)
- [`app/src/main/java/com/triagemate/chps/presentation/screens/assessment/AssessmentScreen.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\presentation\screens\assessment\AssessmentScreen.kt)
- [`app/src/main/java/com/triagemate/chps/data/repository/InferenceRepositoryImpl.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\repository\InferenceRepositoryImpl.kt)
- [`app/src/main/java/com/triagemate/chps/tools/ClinicalToolSet.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\tools\ClinicalToolSet.kt)
- [`app/src/main/java/com/triagemate/chps/data/repository/AssessmentRepositoryImpl.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\repository\AssessmentRepositoryImpl.kt)
- [`app/src/main/java/com/triagemate/chps/data/engine/EngineProvider.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\data\engine\EngineProvider.kt)
- [`app/src/main/java/com/triagemate/chps/util/PromptBuilder.kt`](C:\Users\Micheal Koranteng\AndroidStudioProjects\TriageMate\app\src\main\java\com\triagemate\chps\util\PromptBuilder.kt)

