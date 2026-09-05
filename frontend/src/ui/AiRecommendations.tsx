
import React, { useState } from 'react'

interface Recommendation {
  inputTerm: string
  recommendedSnomedId: string
  recommendedTerm: string
  fsn: string
  confidence: number
  reason: string
  definition: string
  relations: string[]
}

interface SuggestedTerm {
  snomedId: string
  term: string
  fsn: string
  reason: string
  definition: string
  relations: string[]
}

interface ExistingSnomedAddition {
  snomedId: string
  pt: string
  fsn: string
  whyAdd: string
  relationsToExisting: string[]
  confidence: number
  inputTerm?: string
}

interface ParentSuggestion {
  snomedId: string
  term: string
}

interface DefiningAttribute {
  attribute: string
  value: string
  valueSnomedId: string
}

interface CandidateNewTerm {
  proposedPt: string
  proposedFsn: string
  semanticTag: string
  gapType: string
  gapJustification: string
  proximalPrimitiveParentSuggestions: ParentSuggestion[]
  definingAttributes: DefiningAttribute[]
  postcoordinationCandidate: boolean
  exampleExpressions: string[]
  synonymsSv: string[]
  synonymsEn: string[]
  usageExample: string
  uncertaintyNotes: string
  confidence: number
  /** User-editable local code for build/export. */
  localCode: string
  inputTerm?: string
  decision?: string
}

interface Props {
  recommendations: Recommendation[]
  suggestedAdditional: SuggestedTerm[]
  existingAdditions: ExistingSnomedAddition[]
  candidateNewTerms: CandidateNewTerm[]
  modelingChecklist: string[]
  selectedRecommendations: Set<number>
  selectedSuggested: Set<number>
  selectedExistingAdditions: Set<number>
  selectedCandidateNewTerms: Set<number>
  onRecommendationsChange: (recommendations: Recommendation[]) => void
  onSuggestedChange: (suggested: SuggestedTerm[]) => void
  onExistingAdditionsChange: (additions: ExistingSnomedAddition[]) => void
  onCandidateNewTermsChange: (candidates: CandidateNewTerm[]) => void
  onSelectedRecommendationsChange: (selected: Set<number>) => void
  onSelectedSuggestedChange: (selected: Set<number>) => void
  onSelectedExistingAdditionsChange: (selected: Set<number>) => void
  onSelectedCandidateNewTermsChange: (selected: Set<number>) => void
  onBuild: () => void
}

function adjustSelectionAfterRemove(selected: Set<number>, removedIndex: number): Set<number> {
  const adjusted = new Set<number>()
  selected.forEach(idx => {
    if (idx < removedIndex) adjusted.add(idx)
    else if (idx > removedIndex) adjusted.add(idx - 1)
  })
  return adjusted
}

function toggleIndex(selected: Set<number>, index: number): Set<number> {
  const next = new Set(selected)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  return next
}

const selectedCardStyle = (selected: boolean, baseBg: string): React.CSSProperties => ({
  border: selected ? '2px solid #28a745' : '1px solid #ddd',
  padding: 12,
  marginBottom: 12,
  borderRadius: 4,
  backgroundColor: selected ? '#d4edda' : baseBg,
  cursor: 'pointer',
  position: 'relative'
})

export function AiRecommendations({
  recommendations,
  suggestedAdditional,
  existingAdditions,
  candidateNewTerms,
  modelingChecklist,
  selectedRecommendations,
  selectedSuggested,
  selectedExistingAdditions,
  selectedCandidateNewTerms,
  onRecommendationsChange,
  onSuggestedChange,
  onExistingAdditionsChange,
  onCandidateNewTermsChange,
  onSelectedRecommendationsChange,
  onSelectedSuggestedChange,
  onSelectedExistingAdditionsChange,
  onSelectedCandidateNewTermsChange,
  onBuild
}: Props){
  const [editingRec, setEditingRec] = useState<number | null>(null)
  const [editingCandidate, setEditingCandidate] = useState<number | null>(null)

  const updateRecommendation = (index: number, field: keyof Recommendation, value: string | number | string[]) => {
    const updated = [...recommendations]
    updated[index] = { ...updated[index], [field]: value }
    onRecommendationsChange(updated)
  }

  const removeRecommendation = (index: number) => {
    onRecommendationsChange(recommendations.filter((_, i) => i !== index))
    onSelectedRecommendationsChange(adjustSelectionAfterRemove(selectedRecommendations, index))
  }

  const updateCandidate = (index: number, field: keyof CandidateNewTerm, value: string | number | boolean | string[]) => {
    const updated = [...candidateNewTerms]
    updated[index] = { ...updated[index], [field]: value }
    onCandidateNewTermsChange(updated)
  }

  const removeSuggested = (index: number) => {
    onSuggestedChange(suggestedAdditional.filter((_, i) => i !== index))
    onSelectedSuggestedChange(adjustSelectionAfterRemove(selectedSuggested, index))
  }

  const removeExistingAddition = (index: number) => {
    onExistingAdditionsChange(existingAdditions.filter((_, i) => i !== index))
    onSelectedExistingAdditionsChange(adjustSelectionAfterRemove(selectedExistingAdditions, index))
  }

  const removeCandidate = (index: number) => {
    onCandidateNewTermsChange(candidateNewTerms.filter((_, i) => i !== index))
    onSelectedCandidateNewTermsChange(adjustSelectionAfterRemove(selectedCandidateNewTerms, index))
  }

  const hasAnyResults =
    recommendations.length > 0 ||
    suggestedAdditional.length > 0 ||
    existingAdditions.length > 0 ||
    candidateNewTerms.length > 0

  const selectedCount =
    selectedRecommendations.size +
    selectedSuggested.size +
    selectedExistingAdditions.size +
    selectedCandidateNewTerms.size

  return (
    <div style={{marginTop:24, border: '1px solid #ddd', padding: 16, borderRadius: 8}}>
      <h2>AI Recommendations</h2>
      <p style={{fontSize: '0.9em', color: '#666'}}>
        Review proposals below. Remove any you do not want. <strong>Build Code System</strong> includes all remaining items plus matched terms.
      </p>
      
      {recommendations.length > 0 && (
        <div style={{marginTop:16}}>
          <h3>Recommendations for Unmatched Terms ({recommendations.length})</h3>
          <p style={{fontSize: '0.9em', color: '#666'}}>Click a card to select/deselect. Click underlined fields to edit.</p>
          {recommendations.map((r, i) => (
            <div
              key={i}
              onClick={() => onSelectedRecommendationsChange(toggleIndex(selectedRecommendations, i))}
              style={selectedCardStyle(selectedRecommendations.has(i), '#f8f9fa')}
            >
              <div style={{position: 'absolute', top: 8, right: 8, display: 'flex', gap: 4, alignItems: 'center'}}>
                {selectedRecommendations.has(i) && (
                  <span style={{color: '#28a745', fontWeight: 'bold'}}>✓ Selected</span>
                )}
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    removeRecommendation(i)
                  }}
                  style={{
                    padding: '4px 8px',
                    backgroundColor: '#dc3545',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '0.85em'
                  }}
                >
                  Remove
                </button>
              </div>
              <div>
                <strong>Input:</strong>{' '}
                {editingRec === i ? (
                  <input
                    type="text"
                    value={r.inputTerm}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateRecommendation(i, 'inputTerm', e.target.value)}
                    onBlur={() => setEditingRec(null)}
                    style={{padding: 4, width: '200px'}}
                    autoFocus
                  />
                ) : (
                  <span onClick={(e) => { e.stopPropagation(); setEditingRec(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {r.inputTerm}
                  </span>
                )}
              </div>
              <div>
                <strong>Recommended:</strong>{' '}
                {editingRec === i ? (
                  <input
                    type="text"
                    value={r.recommendedTerm}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateRecommendation(i, 'recommendedTerm', e.target.value)}
                    style={{padding: 4, width: '300px'}}
                  />
                ) : (
                  <span onClick={(e) => { e.stopPropagation(); setEditingRec(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {r.recommendedTerm}
                  </span>
                )}
              </div>
              <div>
                <strong>SNOMED ID:</strong>{' '}
                {editingRec === i ? (
                  <input
                    type="text"
                    value={r.recommendedSnomedId}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateRecommendation(i, 'recommendedSnomedId', e.target.value)}
                    style={{padding: 4, width: '200px', fontFamily: 'monospace'}}
                  />
                ) : (
                  <code onClick={(e) => { e.stopPropagation(); setEditingRec(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {r.recommendedSnomedId}
                  </code>
                )}
              </div>
              <div>
                <strong>FSN:</strong>{' '}
                {editingRec === i ? (
                  <input
                    type="text"
                    value={r.fsn}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateRecommendation(i, 'fsn', e.target.value)}
                    style={{padding: 4, width: '100%'}}
                  />
                ) : (
                  <span onClick={(e) => { e.stopPropagation(); setEditingRec(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {r.fsn}
                  </span>
                )}
              </div>
              <div><strong>Confidence:</strong> {(r.confidence * 100).toFixed(0)}%</div>
              <div><strong>Reason:</strong> {r.reason}</div>
              {r.definition && (
                <div>
                  <strong>Definition:</strong>{' '}
                  {editingRec === i ? (
                    <textarea
                      value={r.definition}
                      onClick={e => e.stopPropagation()}
                      onChange={e => updateRecommendation(i, 'definition', e.target.value)}
                      style={{padding: 4, width: '100%', minHeight: '60px'}}
                    />
                  ) : (
                    <span onClick={(e) => { e.stopPropagation(); setEditingRec(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                      {r.definition}
                    </span>
                  )}
                </div>
              )}
              {r.relations.length > 0 && (
                <div><strong>Relations:</strong> {r.relations.join(', ')}</div>
              )}
            </div>
          ))}
        </div>
      )}

      {suggestedAdditional.length > 0 && (
        <div style={{marginTop:24}}>
          <h3>Additional Suggested Codes ({suggestedAdditional.length})</h3>
          <p style={{fontSize: '0.9em', color: '#666'}}>Click to select/deselect terms to include in the code system</p>
          {suggestedAdditional.map((s, i) => (
            <div
              key={i}
              onClick={() => onSelectedSuggestedChange(toggleIndex(selectedSuggested, i))}
              style={selectedCardStyle(selectedSuggested.has(i), '#f8f9fa')}
            >
              <div style={{position: 'absolute', top: 8, right: 8, display: 'flex', gap: 4}}>
                {selectedSuggested.has(i) && (
                  <span style={{color: '#28a745', fontWeight: 'bold'}}>✓ Selected</span>
                )}
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    removeSuggested(i)
                  }}
                  style={{
                    padding: '4px 8px',
                    backgroundColor: '#dc3545',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '0.85em'
                  }}
                >
                  Remove
                </button>
              </div>
              <div><strong>Term:</strong> {s.term} <code>({s.snomedId})</code></div>
              <div><strong>FSN:</strong> {s.fsn}</div>
              <div><strong>Reason:</strong> {s.reason}</div>
              {s.definition && <div><strong>Definition:</strong> {s.definition}</div>}
              {s.relations.length > 0 && (
                <div><strong>Relations:</strong> {s.relations.join(', ')}</div>
              )}
            </div>
          ))}
        </div>
      )}

      {existingAdditions.length > 0 && (
        <div style={{marginTop:24}}>
          <h3>Existing SNOMED Additions ({existingAdditions.length})</h3>
          <p style={{fontSize: '0.9em', color: '#666'}}>Click to select/deselect. Review before adding to the code system.</p>
          {existingAdditions.map((a, i) => (
            <div
              key={i}
              onClick={() => onSelectedExistingAdditionsChange(toggleIndex(selectedExistingAdditions, i))}
              style={selectedCardStyle(selectedExistingAdditions.has(i), '#f8f9fa')}
            >
              <div style={{position: 'absolute', top: 8, right: 8, display: 'flex', gap: 4}}>
                {selectedExistingAdditions.has(i) && (
                  <span style={{color: '#28a745', fontWeight: 'bold'}}>✓ Selected</span>
                )}
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    removeExistingAddition(i)
                  }}
                  style={{
                    padding: '4px 8px',
                    backgroundColor: '#dc3545',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '0.85em'
                  }}
                >
                  Remove
                </button>
              </div>
              <div><strong>Term:</strong> {a.pt} <code>({a.snomedId})</code></div>
              {a.inputTerm && <div><strong>Covers input:</strong> {a.inputTerm}</div>}
              <div><strong>FSN:</strong> {a.fsn}</div>
              <div><strong>Reason:</strong> {a.whyAdd}</div>
              <div><strong>Confidence:</strong> {(a.confidence * 100).toFixed(0)}%</div>
              {a.relationsToExisting?.length > 0 && (
                <div><strong>Relations:</strong> {a.relationsToExisting.join(', ')}</div>
              )}
            </div>
          ))}
        </div>
      )}

      {candidateNewTerms.length > 0 && (
        <div style={{marginTop:24}}>
          <h3>Candidate New Terms (Modeling) ({candidateNewTerms.length})</h3>
          <p style={{fontSize: '0.9em', color: '#666'}}>
            Editorial Guide modeling candidates (proximal primitive parents + defining attributes).
            Click to select/deselect. Edit local code, PT, and FSN before building.
          </p>
          {candidateNewTerms.map((t, i) => (
            <div
              key={i}
              onClick={() => onSelectedCandidateNewTermsChange(toggleIndex(selectedCandidateNewTerms, i))}
              style={selectedCardStyle(selectedCandidateNewTerms.has(i), '#fffbe6')}
            >
              <div style={{position: 'absolute', top: 8, right: 8, display: 'flex', gap: 4}}>
                {selectedCandidateNewTerms.has(i) && (
                  <span style={{color: '#28a745', fontWeight: 'bold'}}>✓ Selected</span>
                )}
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    removeCandidate(i)
                  }}
                  style={{
                    padding: '4px 8px',
                    backgroundColor: '#dc3545',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '0.85em'
                  }}
                >
                  Remove
                </button>
              </div>
              {t.inputTerm && <div><strong>Input term:</strong> {t.inputTerm}</div>}
              {t.decision && <div><strong>Decision:</strong> {t.decision}</div>}
              <div>
                <strong>Local code:</strong>{' '}
                {editingCandidate === i ? (
                  <input
                    type="text"
                    value={t.localCode}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateCandidate(i, 'localCode', e.target.value)}
                    style={{padding: 4, width: '220px', fontFamily: 'monospace'}}
                    autoFocus
                  />
                ) : (
                  <code onClick={(e) => { e.stopPropagation(); setEditingCandidate(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {t.localCode || '(set code)'}
                  </code>
                )}
              </div>
              <div>
                <strong>Proposed PT:</strong>{' '}
                {editingCandidate === i ? (
                  <input
                    type="text"
                    value={t.proposedPt}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateCandidate(i, 'proposedPt', e.target.value)}
                    style={{padding: 4, width: '100%'}}
                  />
                ) : (
                  <span onClick={(e) => { e.stopPropagation(); setEditingCandidate(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {t.proposedPt}
                  </span>
                )}
              </div>
              <div>
                <strong>Proposed FSN:</strong>{' '}
                {editingCandidate === i ? (
                  <input
                    type="text"
                    value={t.proposedFsn}
                    onClick={e => e.stopPropagation()}
                    onChange={e => updateCandidate(i, 'proposedFsn', e.target.value)}
                    style={{padding: 4, width: '100%'}}
                  />
                ) : (
                  <span onClick={(e) => { e.stopPropagation(); setEditingCandidate(i) }} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {t.proposedFsn}
                  </span>
                )}
              </div>
              <div><strong>Semantic Tag:</strong> {t.semanticTag}</div>
              <div><strong>Gap Type:</strong> {t.gapType}</div>
              <div><strong>Gap Justification:</strong> {t.gapJustification}</div>
              {t.proximalPrimitiveParentSuggestions?.length > 0 && (
                <div><strong>Parent suggestions:</strong> {t.proximalPrimitiveParentSuggestions.map(p => `${p.term}${p.snomedId ? ` (${p.snomedId})` : ''}`).join(', ')}</div>
              )}
              {t.definingAttributes?.length > 0 && (
                <div><strong>Defining attributes:</strong> {t.definingAttributes.map(a => `${a.attribute}=${a.value}${a.valueSnomedId ? ` (${a.valueSnomedId})` : ''}`).join('; ')}</div>
              )}
              {t.synonymsSv?.length > 0 && <div><strong>Synonyms SV:</strong> {t.synonymsSv.join(', ')}</div>}
              {t.synonymsEn?.length > 0 && <div><strong>Synonyms EN:</strong> {t.synonymsEn.join(', ')}</div>}
              {t.usageExample && <div><strong>Usage example:</strong> {t.usageExample}</div>}
              {t.uncertaintyNotes && <div><strong>Uncertainty:</strong> {t.uncertaintyNotes}</div>}
              <div><strong>Postcoordination candidate:</strong> {t.postcoordinationCandidate ? 'Yes' : 'No'}</div>
              <div><strong>Confidence:</strong> {(t.confidence * 100).toFixed(0)}%</div>
            </div>
          ))}
        </div>
      )}

      {modelingChecklist.length > 0 && (
        <div style={{marginTop: 16}}>
          <h4>Modeling Review Checklist</h4>
          <ul>
            {modelingChecklist.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      )}

      {hasAnyResults && (
        <div style={{marginTop:16}}>
          <p style={{fontSize: '0.9em', color: '#666', marginBottom: 8}}>
            Build includes matched terms plus all {recommendations.length + suggestedAdditional.length + existingAdditions.length + candidateNewTerms.length} AI proposal{recommendations.length + suggestedAdditional.length + existingAdditions.length + candidateNewTerms.length === 1 ? '' : 's'} listed above
            {selectedCount > 0 ? ` (${selectedCount} marked selected for review).` : '.'}
          </p>
          <button
            onClick={onBuild}
            style={{padding: '10px 20px', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: '1em'}}
          >
            Build Code System
          </button>
        </div>
      )}
    </div>
  )
}
