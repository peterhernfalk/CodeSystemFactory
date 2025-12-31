# Swedish Language Support for SNOMED CT Matching

## Current Status

✅ **Swedish language support is already implemented** in the application. The code is configured to:
- Search for Swedish terms first (`language: sv`)
- Fallback to English if no Swedish match is found
- Filter and prioritize Swedish descriptions from SNOMED CT

## Configuration

The current configuration in `application.yml`:

```yaml
snomed:
  branch: MAIN
  language: sv                    # Swedish language code
  language-refset:                # Empty - may need configuration
  fallback-to-english: true      # Falls back to English if Swedish not found
```

## How It Works

1. **Primary Search**: When you enter a Swedish term (e.g., "Diabetes", "Hjärtinfarkt"), the system:
   - Searches Snowstorm with `Accept-Language: sv` header
   - Looks for Swedish descriptions in SNOMED CT
   - Returns Swedish Preferred Terms (PT) and Fully Specified Names (FSN)

2. **Fallback**: If no Swedish match is found:
   - Automatically searches in English
   - Returns English terms as fallback

3. **Language Filtering**: The system prioritizes:
   - Swedish descriptions when available
   - Falls back to English descriptions if Swedish not available

## Testing Swedish Matching

### Test with Swedish Terms

Try entering these Swedish medical terms in the frontend:

- **"Diabetes"** - Should match "Diabetes mellitus (sjukdom)" (Swedish) or "Diabetes mellitus (disorder)" (English fallback)
- **"Hjärtinfarkt"** - Should match Swedish term for myocardial infarction
- **"Hjärtsvikt"** - Should match Swedish term for heart failure
- **"Högt blodtryck"** - Should match Swedish term for hypertension

### Verify in Backend Logs

When you search, check the backend console output. You should see:
```
DEBUG: Searching with URL: ... (language: sv)
DEBUG: Search for 'Diabetes' (language: sv) returned X items
DEBUG: Concept 73211009 - Total descriptions: X, Preferred language (sv): Y, English fallback: Z
```

## Requirements for Full Swedish Support

For Swedish matching to work optimally, the Snowstorm server needs:

1. **Swedish Language Module**: The SNOMED CT release must include Swedish language descriptions
2. **Language Refset** (optional but recommended): A language reference set that defines which descriptions are in Swedish

### Language Refset Configuration

If your Snowstorm server has a Swedish language refset, you can configure it:

```yaml
snomed:
  language: sv
  language-refset: 46011000052107  # Example: Swedish language refset ID
  fallback-to-english: true
```

**Note**: The language refset ID depends on your SNOMED CT release and Snowstorm configuration. Common Swedish refsets:
- `46011000052107` - Swedish language reference set (example)
- Check with your Snowstorm administrator for the correct refset ID

## Snowstorm Server Requirements

The Snowstorm server (`snowstorm-training.snomedtools.org`) may or may not have Swedish language support:

### Check if Swedish is Available

You can test if the Snowstorm server supports Swedish by making a direct API call:

```bash
curl -H "Accept-Language: sv" \
  "https://snowstorm-training.snomedtools.org/snowstorm/snomed-ct/MAIN/concepts?term=Diabetes&limit=5&activeFilter=true"
```

Look for descriptions with `"lang": "sv"` in the response.

### If Swedish is Not Available

If the Snowstorm server doesn't have Swedish language support:

1. **The fallback will work**: English terms will be returned
2. **Consider using a different Snowstorm server**: Some Snowstorm instances have Swedish language support
3. **Use a local Snowstorm**: You can set up your own Snowstorm server with Swedish language module

## Configuration Options

### Option 1: Use Swedish with English Fallback (Current)

```yaml
snomed:
  language: sv
  language-refset: 
  fallback-to-english: true
```

**Behavior**: Tries Swedish first, falls back to English.

### Option 2: Swedish Only (No Fallback)

```yaml
snomed:
  language: sv
  language-refset: 
  fallback-to-english: false
```

**Behavior**: Only searches in Swedish, returns NO_MATCH if Swedish not found.

### Option 3: English Only

```yaml
snomed:
  language: en
  language-refset: 
  fallback-to-english: false
```

**Behavior**: Only searches in English.

## Troubleshooting

### Issue: Swedish terms not matching

**Possible causes:**
1. Snowstorm server doesn't have Swedish language module
2. Language refset not configured (if required)
3. Term spelling doesn't match SNOMED CT Swedish descriptions

**Solutions:**
1. Check backend logs for language being used
2. Verify Snowstorm server has Swedish support
3. Try the same term in English to verify the concept exists
4. Enable fallback to English

### Issue: Getting English terms instead of Swedish

**Possible causes:**
1. Swedish descriptions not available in Snowstorm
2. Language refset not configured correctly

**Solutions:**
1. Verify Snowstorm has Swedish language module loaded
2. Configure language refset if available
3. Check backend logs to see which language matched

## Example: Testing Swedish Matching

1. **Start the backend** and check console output:
   ```
   SNOMED Configuration loaded:
     Branch: MAIN
     Language: sv
     Language Refset: not set
     Fallback to English: true
   ```

2. **Enter a Swedish term** in the frontend (e.g., "Diabetes")

3. **Check backend logs** for:
   ```
   DEBUG: Searching with URL: ... (language: sv)
   DEBUG: Search for 'Diabetes' (language: sv) returned X items
   ```

4. **Check the response**: The frontend should show:
   - Swedish Preferred Term (if available)
   - Swedish FSN (if available)
   - Or English terms (if Swedish not available, due to fallback)

## Summary

✅ **Swedish matching is already implemented and configured**

The application will:
- Try to match Swedish terms with Swedish SNOMED CT descriptions
- Fall back to English if Swedish not available
- Return the best match regardless of language

**To fully utilize Swedish matching**, ensure:
1. Your Snowstorm server has Swedish language module
2. Language refset is configured (if required by your Snowstorm setup)
3. Swedish terms are spelled correctly according to SNOMED CT Swedish descriptions

---

**Last Updated**: 2024
**Application Version**: 0.0.2-SNAPSHOT

