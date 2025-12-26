
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

interface Props {
  recommendations: Recommendation[]
  suggestedAdditional: SuggestedTerm[]
  selectedSuggested: Set<number>
  onRecommendationsChange: (recommendations: Recommendation[]) => void
  onSuggestedChange: (suggested: SuggestedTerm[]) => void
  onSelectedSuggestedChange: (selected: Set<number>) => void
  onBuild: () => void
}

export function AiRecommendations({recommendations, suggestedAdditional, selectedSuggested, onRecommendationsChange, onSuggestedChange, onSelectedSuggestedChange, onBuild}: Props){
  const [editingRec, setEditingRec] = useState<number | null>(null)

  const updateRecommendation = (index: number, field: keyof Recommendation, value: string | number | string[]) => {
    const updated = [...recommendations]
    updated[index] = { ...updated[index], [field]: value }
    onRecommendationsChange(updated)
  }

  const removeRecommendation = (index: number) => {
    onRecommendationsChange(recommendations.filter((_, i) => i !== index))
  }

  const toggleSuggested = (index: number) => {
    const newSelected = new Set(selectedSuggested)
    if (newSelected.has(index)) {
      newSelected.delete(index)
    } else {
      newSelected.add(index)
    }
    onSelectedSuggestedChange(newSelected)
  }

  const removeSuggested = (index: number) => {
    const newList = suggestedAdditional.filter((_, i) => i !== index)
    onSuggestedChange(newList)
    const newSelected = new Set(selectedSuggested)
    newSelected.delete(index)
    // Adjust indices for remaining items
    const adjustedSelected = new Set<number>()
    newSelected.forEach(idx => {
      if (idx < index) adjustedSelected.add(idx)
      else if (idx > index) adjustedSelected.add(idx - 1)
    })
    onSelectedSuggestedChange(adjustedSelected)
  }

  return (
    <div style={{marginTop:24, border: '1px solid #ddd', padding: 16, borderRadius: 8}}>
      <h2>AI Recommendations</h2>
      
      {recommendations.length > 0 && (
        <div style={{marginTop:16}}>
          <h3>Recommendations for Unmatched Terms ({recommendations.length})</h3>
          {recommendations.map((r, i) => (
            <div key={i} style={{border: '1px solid #ccc', padding: 12, marginBottom: 12, borderRadius: 4, position: 'relative'}}>
              <button
                onClick={() => removeRecommendation(i)}
                style={{
                  position: 'absolute',
                  top: 8,
                  right: 8,
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
              <div>
                <strong>Input:</strong>{' '}
                {editingRec === i ? (
                  <input 
                    type="text" 
                    value={r.inputTerm} 
                    onChange={e => updateRecommendation(i, 'inputTerm', e.target.value)}
                    onBlur={() => setEditingRec(null)}
                    style={{padding: 4, width: '200px'}}
                    autoFocus
                  />
                ) : (
                  <span onClick={() => setEditingRec(i)} style={{cursor: 'pointer', textDecoration: 'underline'}}>
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
                    onChange={e => updateRecommendation(i, 'recommendedTerm', e.target.value)}
                    onBlur={() => setEditingRec(null)}
                    style={{padding: 4, width: '300px'}}
                  />
                ) : (
                  <span onClick={() => setEditingRec(i)} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                    {r.recommendedTerm}
                  </span>
                )}{' '}
                <code>({r.recommendedSnomedId})</code>
              </div>
              <div><strong>FSN:</strong> {r.fsn}</div>
              <div><strong>Confidence:</strong> {(r.confidence * 100).toFixed(0)}%</div>
              <div><strong>Reason:</strong> {r.reason}</div>
              {r.definition && (
                <div>
                  <strong>Definition:</strong>{' '}
                  {editingRec === i ? (
                    <textarea 
                      value={r.definition} 
                      onChange={e => updateRecommendation(i, 'definition', e.target.value)}
                      onBlur={() => setEditingRec(null)}
                      style={{padding: 4, width: '100%', minHeight: '60px'}}
                    />
                  ) : (
                    <span onClick={() => setEditingRec(i)} style={{cursor: 'pointer', textDecoration: 'underline'}}>
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
              onClick={() => toggleSuggested(i)}
              style={{
                border: selectedSuggested.has(i) ? '2px solid #28a745' : '1px solid #ddd',
                padding: 12,
                marginBottom: 12,
                borderRadius: 4,
                backgroundColor: selectedSuggested.has(i) ? '#d4edda' : '#f8f9fa',
                cursor: 'pointer',
                position: 'relative'
              }}
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

      {(recommendations.length > 0 || suggestedAdditional.length > 0) && (
        <button 
          onClick={onBuild} 
          style={{marginTop:16, padding: '10px 20px', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: '1em'}}
        >
          Build Code System
        </button>
      )}
    </div>
  )
}

