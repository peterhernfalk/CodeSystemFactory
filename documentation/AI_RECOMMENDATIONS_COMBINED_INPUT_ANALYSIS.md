# AI Recommendations Combined Input Analysis

## Goal

When the UI button **"Get AI Recommendations for Additional Suggestions"** is pressed, the system should:

1. Use **unmatched terms** to generate per-term recommendations.
2. Use **matched terms** (SNOMED IDs) to generate additional relevant codes.
3. Return both result groups in one response and show them in the UI.

This document analyzes the current behavior and the changes needed to fully achieve that goal.

---

## Current Behavior (As-Is)

### Frontend (`App.tsx`)

- The request to `/api/ai/recommend` already sends:
  - `unmatchedTerms` (from unmatched rows)
  - `matchedSnomedIds` (from matched rows)
  - `context`
- The UI already renders:
  - `recommendations` (for unmatched terms)
  - `suggestedAdditional` (additional codes)

### Backend request contract (`AiRecommendationRequest`)

- `unmatchedTerms` can be empty (non-null).
- `matchedSnomedIds` must be non-empty.

This is suitable for both:
- mixed matched+unmatched runs
- all-matched runs (additional suggestions only)

### Backend recommendation logic (`HybridRecommendationServiceImpl`)

- **Unmatched terms path**:
  - synonym + fuzzy strategies produce deterministic recommendations
  - AI fallback is used when terms remain unmatched (`stillUnmatched`)
- **Matched terms path**:
  - hierarchy expansion from `matchedSnomedIds` builds `suggestedAdditional` candidate pool

### Critical gap vs target behavior

AI is currently called only when `stillUnmatched` is non-empty.  
If all unmatched terms are resolved deterministically, AI is not called, and additional codes remain deterministic hierarchy output only.

In other words:
- unmatched terms are used ✅
- matched terms are used ✅
- but **joint AI reasoning over both sets in every run is not guaranteed** ❌

---

## What Needs to Change

## 1) Backend orchestration: always support dual-purpose recommendation run

In `HybridRecommendationServiceImpl.recommend(...)`, adjust orchestration so that additional suggestion selection can run even when `stillUnmatched` is empty.

### Required behavior

- Build candidate pool from matched hierarchy (existing behavior).
- Generate recommendations for unmatched terms (existing behavior).
- Then, when candidate pool is non-empty and AI fallback is enabled:
  - call AI selection/ranking for additional suggestions even if `stillUnmatched` is empty.

### Implementation options

- **Option A (preferred minimal-risk):**
  - keep current `findViaAiFallback(...)` method
  - call it with `unmatchedTerms = List.of()` when only additional ranking is needed
  - accept only `suggestedAdditional` from that call; keep recommendations unchanged
- **Option B (cleaner architecture):**
  - split AI logic into:
    - `rankUnmatchedRecommendations(...)`
    - `rankAdditionalSuggestions(...)`
  - call each independently based on available inputs

Option A is faster to implement with lower regression risk.

---

## 2) Prompt and response strategy

Current prompt already includes both `unmatchedTerms` and `matchedSnomedIds` and a grounded candidate pool.

Needed refinement:
- allow prompt mode where `unmatchedTerms` is empty but additional ranking is still requested.
- keep hard grounding rules (only IDs from candidate pool).

No response schema change is required because `AiRecommendationResponse` already supports both arrays.

---

## 3) Frontend messaging/UX alignment

No structural API/UI refactor is required.  
The UI can already display both sections.

Recommended wording updates:
- Clarify button label/tooltip to reflect dual behavior:
  - "Get AI Recommendations (Unmatched + Additional Codes)"
- Clarify empty-state message:
  - distinguish between:
    - "No unmatched-term recommendations"
    - "No additional relevant codes found"

This is optional but reduces user confusion.

---

## 4) Tests required before rollout

Add backend tests for behavior contracts:

1. **Mixed input case**
   - unmatched terms present + matched IDs present
   - verifies both `recommendations` and `suggestedAdditional` may be returned

2. **All matched case**
   - `unmatchedTerms = []`, matched IDs present
   - verifies AI additional ranking path is executed (when enabled), not just deterministic fallback

3. **No candidate pool case**
   - matched IDs present but hierarchy candidate pool empty
   - verify graceful empty `suggestedAdditional` response

4. **Grounding safety**
   - verify returned IDs are always in candidate pool

Frontend tests (optional but recommended):
- one integration test where both sections render together
- one integration test where only additional suggestions render

---

## 5) Configuration and runtime considerations

- If `ai.enabled=false`, behavior should still work but additional suggestions remain deterministic.
- If true AI ranking is expected in production:
  - `ai.enabled=true`
  - valid API key/config must exist

This should be documented to avoid environment-related misunderstandings.

---

## Proposed Change Set Summary

Minimal required code changes:

1. **Backend only (must-have):**
   - update `HybridRecommendationServiceImpl.recommend(...)` to trigger AI additional-suggestion ranking even when `stillUnmatched` is empty.

2. **Tests (must-have):**
   - add integration/unit tests for mixed-input and all-matched scenarios.

3. **Frontend text (nice-to-have):**
   - adjust labels/messages to communicate dual input/dual output behavior.

---

## Expected Outcome After Implementation

Pressing **"Get AI Recommendations for Additional Suggestions"** will consistently:

- use unmatched terms to propose SNOMED mappings (recommendations)
- use matched terms to propose complementary codes (suggested additional)
- return both in one response whenever relevant
- behave consistently across mixed and all-matched scenarios

---

## Implementation Update (Option A Applied)

Status: **Implemented**

### Backend behavior change

`HybridRecommendationServiceImpl.recommend(...)` now invokes AI fallback when either of these is true:

1. `stillUnmatched` is non-empty (previous behavior), or
2. `additionalCandidatePool` is non-empty (new Option A behavior)

This enables AI ranking of additional suggestions even when all unmatched terms are empty/resolved.

### New decision method

A dedicated method was added:

- `shouldInvokeAiFallback(stillUnmatched, additionalCandidatePool)`

Rules:
- returns `false` if `useAiFallback=false` or `aiEnabled=false`
- otherwise returns `true` when there are unresolved unmatched terms **or** additional candidate pool entries

### Test coverage added

New test class:

- `backend/src/test/java/com/example/codesys/service/impl/HybridRecommendationServiceImplOptionATest.java`

It verifies:
- AI fallback is invoked for **empty unmatched + non-empty additional pool** (core Option A case)
- AI fallback is not invoked when:
  - fallback feature is disabled
  - AI is disabled

### Consequences of this change

#### Positive

- Additional suggestions can now be AI-ranked in all-matched scenarios.
- The button behavior better matches user expectation: one action considers both unmatched and matched context.
- Maintains grounded safety constraints (AI output still filtered to candidate pool IDs).

#### Trade-offs

- More AI calls may occur (for all-matched scenarios with non-empty pool), which can increase latency/cost when AI is enabled.
- If AI is unavailable or returns invalid output, system still falls back gracefully to deterministic hierarchy suggestions.

#### Operational note

- To get true AI ranking in production, ensure:
  - `ai.enabled=true`
  - valid AI credentials/configuration are present

---

## Implementation Update (Separate Button Goals Applied)

Status: **Implemented**

### UX behavior now

- The button in **Unmatched Terms** keeps the title:
  - `Get AI Recommendations for Unmatched Terms`
- The button in **Ready to Build Code System** now always keeps the title:
  - `Get AI Recommendations for Additional Suggestions`

The second button no longer changes text depending on unmatched-count state.

### Frontend request behavior now

`frontend/src/ui/App.tsx` now has two explicit handlers:

- `callAiRecommendForUnmatched()` -> sends mode `UNMATCHED`
- `callAiRecommendForAdditional()` -> sends mode `ADDITIONAL`

Both call a shared internal request function, but with distinct payload intent.

For `ADDITIONAL`, frontend sends:
- `unmatchedTerms: []`
- `matchedSnomedIds: [...]`
- `recommendationMode: "ADDITIONAL"`

For `UNMATCHED`, frontend sends:
- `unmatchedTerms: [...]`
- `matchedSnomedIds: [...]`
- `recommendationMode: "UNMATCHED"`

### Backend API contract change

`AiRecommendationRequest` now includes:

- `recommendationMode` enum: `UNMATCHED | ADDITIONAL | BOTH`

Null mode defaults to `BOTH` for backward compatibility.

### Backend orchestration consequences

`HybridRecommendationServiceImpl` now normalizes and uses mode:

- `UNMATCHED`:
  - runs unmatched recommendation strategies
  - does not run additional hierarchy candidate generation
- `ADDITIONAL`:
  - skips unmatched recommendation generation
  - runs additional hierarchy candidate generation and AI ranking path
- `BOTH`:
  - behaves as combined mode (existing behavior + Option A)

### Compatibility implications

- Existing clients that do not send `recommendationMode` still work (default `BOTH`).
- New UI now has deterministic button semantics and cleaner traceability.

### Verification performed

- Frontend build passed (`npm run build`)
- Backend tests passed:
  - `AiRecommendationControllerValidationIntegrationTest`
  - `HybridRecommendationServiceImplOptionATest`

