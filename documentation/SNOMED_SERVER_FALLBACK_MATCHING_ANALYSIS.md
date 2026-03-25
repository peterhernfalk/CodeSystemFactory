# SNOMED CT Server Fallback Matching (ordered per term) — Analysis & Options

## Goal

Add a new frontend dropdown option for choosing SNOMED CT terminology servers such that matching is done with an **ordered fallback chain**:

1. Start with the first server in the chain.
2. For terms that could **not** be matched, try the next server.
3. Continue until either all terms are matched or the chain ends.
4. Result: **as many terms as possible are matched**, and preference is given to earlier servers in the chain.

The UI should still show the same data model:
- `matched` terms (with `snomedId`, `preferredTerm`, `fsn`, `similarity`)
- `unmatched` terms (with a `reason`)

This analysis focuses on implementation approaches that achieve the ordered fallback behavior **per term**.

---

## Chosen fallback chain + metadata requirement

Preferred server order (highest preference first):
- `snowstorm` -> `ontoserver` -> `inera`

UI/UX requirement:
- Display **which server matched** each term.

This implies the matching response needs additional metadata, for example:
- `matchedByServer: string` on each item in `matched[]`.

## Current matching behavior (baseline)

The current flow is:

1. Frontend dropdown chooses **one** `selectedServer` value (`snowstorm`, `ontoserver`, `inera`).
2. Frontend calls `POST /api/terms/match` with `{ terms, server }`.
3. Backend `TermsController` selects a single `SnomedService` via `SnomedServiceFactory.getService(request.server())`.
4. Each service returns `MATCHED` or `NO_MATCH` results; `TermsController` splits them into `matched` and `unmatched` lists.

Relevant code locations:
- Frontend: `frontend/src/ui/App.tsx` (`selectedServer` + `callMatch`)
- Backend: `backend/src/main/java/com/example/codesys/controller/TermsController.java`
- Backend: `backend/src/main/java/com/example/codesys/service/SnomedServiceFactory.java`

Because `POST /api/terms/match` currently supports only **one** server selection at a time, ordered fallback requires either:
- orchestrating multiple calls (frontend or backend), or
- adding a new backend endpoint that takes a chain/list of servers.

---

## Key requirement details (important for correct solutions)

To satisfy “start with the first alternative and only use the next when a term couldn’t be matched” you must enforce:

For each input term `t`:
- choose the **first** server in the chain that returns `t` as `MATCHED`
- if the first server returns `NO_MATCH`, then attempt `t` with the next server
- do not “upgrade” a term that was already matched by an earlier server

This is different from:
- “best overall match” across all servers (which might prefer a later server for a term even if an earlier server could match it)
- “parallel matching” with post-selection (possible, but harder to preserve the strict server-order preference)

---

## Option A — Frontend orchestrated sequential fallback (multiple calls)

### Description

Add a new dropdown option such as:
- `snowstorm` (current behavior)
- `ontoserver` (current behavior)
- `inera` (current behavior)
- `fallback_chain` (new option) with a defined order, e.g. `snowstorm -> ontoserver -> inera`

When the user selects `fallback_chain`:
1. Call `/api/terms/match` with server #1.
2. Take `matched` from server #1.
3. Take `unmatched` terms from server #1 as the input set for server #2.
4. Call again with server #2, using only the still-unmatched term strings.
5. Repeat until the chain ends.

Finally:
- `matchedTerms = matched_from_server1 + matched_from_server2 + ...` (merged)
- `unmatchedTerms = unmatched_from_last_server_that_was_tried`

### Pros
- Minimal backend changes (no new endpoints required)
- Quick to prototype and test in the UI
- Works with the existing `TermRequest` model (`server: string`)
- Keeps the “earlier servers get preference” rule naturally (because you never retry matched terms)

### Cons
- Multiple network round trips (worst case: number_of_servers in chain)
- More client-side logic complexity in `frontend/src/ui/App.tsx`
- Harder to add server-quality metrics (e.g. logging which server matched each term) unless you extend the response model
- More opportunities for partial failure (server #2 down → behavior must be decided)

### When this option is best
- You want the feature fast with low backend risk
- The number of terms and server count is moderate
- You’re okay with the extra network calls

---

## Option B — Backend orchestrated fallback (new endpoint / enhanced request)

### Description

Add a backend capability that accepts a chain and performs the sequential fallback internally.

Two common designs:
1. New endpoint:
   - `POST /api/terms/match/fallback`
   - request: `{ terms, serverChain: ["snowstorm","ontoserver","inera"] }`
   - response: same `TermMatchResponse` shape as today
2. Extend existing request model:
   - Add optional `serverChain` field to `TermRequest`
   - If present, ignore `server` and use the chain

Algorithm in backend:
- `unmatchedTerms = original terms`
- for each `server` in chain:
  - call the selected `SnomedService` for `unmatchedTerms` only
  - move `MATCHED` results to the global `matched` list
  - keep `unmatched` for next iteration
- return final `matched/unmatched`

### Pros
- Single network round trip from frontend
- Centralizes the logic (easier to maintain and test)
- Enables richer response metadata later (e.g. “which server matched this term”) without duplicating logic across clients
- Easier to implement caching/retries/failover policies server-side

### Cons
- Larger backend change surface:
  - request/response contract changes (or new endpoint)
  - update OpenAPI / docs / tests
- More work than Option A

### When this option is best
- You want correctness + maintainability for a “production-ish” feature
- You anticipate adding more servers or more sophisticated fallback logic later

---

## Option C — Parallel matching across servers (then pick per term with server-order precedence)

### Description

Run matching for all servers concurrently:
- call `/api/terms/match` for each server with the full term list
- wait for all results
- for each term, select the result from the earliest server in the chain that matched

This still preserves “earlier servers win”, but implementation becomes:
- either frontend does concurrency + merge, or
- backend does parallel service invocations

### Pros
- Potentially faster wall-clock time (especially if servers have variable latency)

### Cons
- Higher compute / network usage (all servers are called even for terms that will match on the first server)
- Harder to preserve the intended “try next only when unmatched” semantics efficiently (you’re effectively doing all tries up front)
- Still needs careful merge logic to enforce the server-order preference per term

### When this option is best
- You mainly care about latency and can tolerate extra load
- You will implement caching so repeated calls are cheap

---

## Option D — Hybrid: partial parallel + sequential continuation

### Description

Examples of hybrids:
- Try servers #1 and #2 in parallel, then fall back to #3 only for terms still unmatched after those two results.
- Or do parallel in batches of size 2/3 to reduce time while limiting wasted work.

### Pros
- Better latency than pure sequential
- Less wasted calls than full parallel

### Cons
- More complicated merge and orchestration
- More edge cases around timeouts and partial results

### When this option is best
- You’re optimizing for both speed and load

---

## Recommended approach (decision guidance)

If your top priority is “implement reliably with minimal risk”:
- **Recommend Option A** first (frontend orchestrated sequential fallback).
- It meets the requirement cleanly because it retries only the terms that are truly unmatched.

If your top priority is “clean architecture + scalable long-term maintenance”:
- **Recommend Option B** (backend orchestrated fallback).
- This is the more future-proof design because fallback logic belongs on the backend where it can be tested and extended centrally.

If you expect:
- many terms,
- frequent matching actions,
- or more than 3 servers,

then Option B will likely save time later.

### Recommendation given your specific choices
Because you want **ordered fallback** *and* you want the UI to show **which server matched**, I would lean toward **Option B (backend orchestrated fallback)**:
- it naturally centralizes the server-chain logic (so precedence is guaranteed per term)
- it can attach `matchedByServer` metadata in the same place the match happens
- it avoids duplicating merge/tagging logic in the frontend

---

## Additional considerations / edge cases to document before implementing

1. **Timeouts / server errors**
   - What should happen if the second server in the chain fails?
   - Suggested behavior: continue with remaining servers if possible, or stop and return current unmatched state.

2. **Result transparency**
   - Right now the UI doesn’t show which server produced each match.
   - Consider adding `matchedByServer` metadata (optional) for debugging/UX.

3. **Duplicate terms**
   - If the user enters duplicate term lines, the fallback merge should preserve the same semantics (match each occurrence or dedupe input?).

4. **Internationalization / language settings**
   - Different servers may interpret similarity differently (some services use different fields like `display` vs FSN).
   - The chain should still aim to maximize matched terms, but quality comparison may differ.

5. **AI recommendations step**
   - `callAiRecommend` currently runs after `/terms/match` and uses `unmatchedTerms` + `matchedTerms`.
   - If fallback matching reduces unmatched terms, it will naturally improve the overall pipeline (fewer unmatched terms).

---

## Next step question (to finalize implementation choice)

Before implementing, decide:

1. Which server order should be used in the new “fallback chain” option?
   - Example: `snowstorm -> ontoserver -> inera`
2. Should we show which server matched each term (optional metadata), or is current UX acceptable?
3. Do you prefer Option A (fast UI-only) or Option B (clean backend endpoint)?

