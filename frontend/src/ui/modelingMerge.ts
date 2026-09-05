/** Helpers to merge successive MODELING AI responses without wiping earlier results. */

export function mergeByInputTermKey<T>(
  previous: T[],
  incoming: T[],
  inputTermOf: (item: T) => string | undefined,
  duplicateKeyOf: (item: T) => string
): T[] {
  const coveredInputTerms = new Set(
    previous
      .map(inputTermOf)
      .filter((t): t is string => !!t && t.trim().length > 0)
      .map(t => t.trim().toLowerCase())
  )
  const seenDupKeys = new Set(previous.map(duplicateKeyOf).filter(Boolean))

  const appended: T[] = []
  for (const item of incoming) {
    const input = (inputTermOf(item) || '').trim().toLowerCase()
    if (input && coveredInputTerms.has(input)) {
      continue
    }
    const dup = duplicateKeyOf(item)
    if (dup && seenDupKeys.has(dup)) {
      continue
    }
    appended.push(item)
    if (input) coveredInputTerms.add(input)
    if (dup) seenDupKeys.add(dup)
  }
  return [...previous, ...appended]
}

export function renumberLocalCodes<T extends { localCode: string }>(items: T[]): T[] {
  return items.map((item, index) => ({
    ...item,
    localCode: `LOCAL-${String(index + 1).padStart(3, '0')}`
  }))
}

export function mergeChecklist(previous: string[], incoming: string[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const item of [...previous, ...incoming]) {
    const key = (item || '').trim()
    if (!key || seen.has(key)) continue
    seen.add(key)
    out.push(item)
  }
  return out
}
