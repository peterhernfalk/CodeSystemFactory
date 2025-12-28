# Similarity Calculation Explanation

## How Similarity is Calculated

The similarity score for term matching is calculated using a multi-step process:

### Step 1: Base Similarity (Jaro-Winkler Algorithm)
- Uses the **Jaro-Winkler similarity algorithm** from Apache Commons Text
- Compares the input term (e.g., "Diabetes") against:
  - Preferred Term (PT) in Swedish
  - Fully Specified Name (FSN) in Swedish
  - All descriptions/synonyms in the preferred language
- Returns a value between **0.0 and 1.0** (0% to 100%)

### Step 2: Score Boosting
The base similarity is then boosted based on matching patterns:

1. **Exact Match**: `score = 1.0` (100%)
   - Input exactly matches PT or FSN

2. **Starts With Input + Space**: `score = Math.max(score, 0.95)` (95%)
   - Example: "Diabetes" matches "Diabetes mellitus"
   - Input is the first word of the term

3. **Starts With Input**: `score = Math.max(score, 0.9)` (90%)
   - Input is at the start of the term

4. **Contains Input as Word**: `score = Math.max(score, 0.85)` (85%)
   - Input appears as a complete word in the term

5. **Contains Input**: `score = Math.max(score, 0.8)` (80%)
   - Input appears anywhere in the term

### Step 3: Additional Boosting (The Bug!)
On **line 338** of `FhirSnomedService.java`, there's an additional boost:

```java
if (score >= 0.9 && (ptLower.startsWith(inputLower + " ") || fsnLower.startsWith(inputLower + " "))) {
    // Prefer terms that are more commonly used medical terms
    if (ptLower.contains("mellitus") || ptLower.contains("myocardial") 
            || ptLower.contains("cardiac mri") || ptLower.contains("magnetic resonance")) {
        score += 0.1; // Boost for standard terms to ensure they win
    }
}
```

**This is where the bug occurs!**

### The Problem: Why 105%?

For "Diabetes" matching "Diabetes mellitus (disorder)":

1. **Base similarity**: Jaro-Winkler calculates ~0.85-0.90
2. **First boost** (line 266): Since "Diabetes mellitus" starts with "Diabetes ", score becomes `Math.max(score, 0.95)` = **0.95**
3. **Second boost** (line 338): Since score >= 0.9 and term contains "mellitus", score gets `+= 0.1`, making it **0.95 + 0.1 = 1.05**
4. **Frontend display**: Multiplies by 100 and shows as **105%**

### Why This Happens

The code uses `score += 0.1` (addition) instead of `Math.max(score, 1.0)` (capping at 100%). This allows the score to exceed 1.0, which is incorrect for a similarity metric.

## Fix

The similarity should be capped at 1.0 (100%). Here's the corrected code:

```java
// Instead of: score += 0.1;
score = Math.min(score + 0.1, 1.0); // Cap at 1.0
```

Or better yet, use `Math.max()` to ensure it doesn't exceed 1.0:

```java
score = Math.max(score, Math.min(score + 0.1, 1.0));
```

## Summary

- **Base calculation**: Jaro-Winkler algorithm (0.0-1.0)
- **Boosting**: Multiple boosts based on matching patterns
- **Bug**: Additional boost can push score above 1.0
- **Display**: Frontend multiplies by 100 to show as percentage
- **Result**: Can show values > 100% (e.g., 105%)

The fix is to cap the similarity score at 1.0 before returning it.

