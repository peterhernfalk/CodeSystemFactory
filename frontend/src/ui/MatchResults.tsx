
import React, { useState } from 'react'

interface MatchedTerm {
  inputTerm: string
  snomedId: string
  preferredTerm: string
  fsn: string
  similarity: number
  description: string | null
  matchedByServer: string
}

interface UnmatchedTerm {
  inputTerm: string
  reason: string
}

interface Props {
  matched: MatchedTerm[]
  unmatched: UnmatchedTerm[]
  onMatchedChange: (matched: MatchedTerm[]) => void
  onUnmatchedChange: (unmatched: UnmatchedTerm[]) => void
  onRecommend: () => void
  recommendLoading?: boolean
}

export function MatchResults({
  matched,
  unmatched,
  onMatchedChange,
  onUnmatchedChange,
  onRecommend,
  recommendLoading = false
}: Props){
  const [editingMatched, setEditingMatched] = useState<number | null>(null)
  const [editingUnmatched, setEditingUnmatched] = useState<number | null>(null)

  const updateMatched = (index: number, field: keyof MatchedTerm, value: string | number) => {
    const updated = [...matched]
    updated[index] = { ...updated[index], [field]: value }
    onMatchedChange(updated)
  }

  const removeMatched = (index: number) => {
    onMatchedChange(matched.filter((_, i) => i !== index))
  }

  const updateUnmatched = (index: number, value: string) => {
    const updated = [...unmatched]
    updated[index] = { ...updated[index], inputTerm: value }
    onUnmatchedChange(updated)
  }

  const removeUnmatched = (index: number) => {
    onUnmatchedChange(unmatched.filter((_, i) => i !== index))
  }

  return (
    <div style={{marginTop:24, border: '1px solid #ddd', padding: 16, borderRadius: 8}}>
      <h2>Matching Results</h2>
      
      {matched.length > 0 && (
        <div style={{marginTop:16}}>
          <h3 style={{color: 'green'}}>Matched Terms ({matched.length})</h3>
          <table border={1} cellPadding={8} style={{width:'100%', borderCollapse:'collapse', marginTop:8}}>
            <thead>
              <tr style={{backgroundColor: '#f0f0f0'}}>
                <th>Input Term</th><th>SNOMED ID</th><th>Preferred Term</th><th>FSN</th><th>Similarity</th><th>Matched By</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {matched.map((m,i)=>(
                <tr key={i}>
                  <td>
                    {editingMatched === i ? (
                      <input 
                        type="text" 
                        value={m.inputTerm} 
                        onChange={e => updateMatched(i, 'inputTerm', e.target.value)}
                        onBlur={() => setEditingMatched(null)}
                        style={{width: '100%', padding: 4}}
                        autoFocus
                      />
                    ) : (
                      <span onClick={() => setEditingMatched(i)} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                        {m.inputTerm}
                      </span>
                    )}
                  </td>
                  <td><code>{m.snomedId}</code></td>
                  <td>
                    {editingMatched === i ? (
                      <input 
                        type="text" 
                        value={m.preferredTerm} 
                        onChange={e => updateMatched(i, 'preferredTerm', e.target.value)}
                        onBlur={() => setEditingMatched(null)}
                        style={{width: '100%', padding: 4}}
                      />
                    ) : (
                      <span onClick={() => setEditingMatched(i)} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                        {m.preferredTerm}
                      </span>
                    )}
                  </td>
                  <td>{m.fsn}</td>
                  <td>{(m.similarity * 100).toFixed(0)}%</td>
                  <td>{m.matchedByServer}</td>
                  <td>
                    <button 
                      onClick={() => removeMatched(i)}
                      style={{padding: '4px 8px', backgroundColor: '#dc3545', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: '0.85em'}}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {unmatched.length > 0 && (
        <div style={{marginTop:24}}>
          <h3 style={{color: 'orange'}}>Unmatched Terms ({unmatched.length})</h3>
          <table border={1} cellPadding={8} style={{width:'100%', borderCollapse:'collapse', marginTop:8}}>
            <thead>
              <tr style={{backgroundColor: '#fff3cd'}}>
                <th>Input Term</th><th>Reason</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {unmatched.map((u,i)=>(
                <tr key={i}>
                  <td>
                    {editingUnmatched === i ? (
                      <input 
                        type="text" 
                        value={u.inputTerm} 
                        onChange={e => updateUnmatched(i, e.target.value)}
                        onBlur={() => setEditingUnmatched(null)}
                        style={{width: '100%', padding: 4}}
                        autoFocus
                      />
                    ) : (
                      <span onClick={() => setEditingUnmatched(i)} style={{cursor: 'pointer', textDecoration: 'underline'}}>
                        {u.inputTerm}
                      </span>
                    )}
                  </td>
                  <td>{u.reason}</td>
                  <td>
                    <button 
                      onClick={() => removeUnmatched(i)}
                      style={{padding: '4px 8px', backgroundColor: '#dc3545', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: '0.85em'}}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button 
            onClick={onRecommend} 
            disabled={recommendLoading}
            style={{
              marginTop:12,
              padding: '8px 16px',
              backgroundColor: recommendLoading ? '#6c757d' : '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: 4,
              cursor: recommendLoading ? 'not-allowed' : 'pointer'
            }}
          >
            {recommendLoading ? 'Getting Editorial Guide modeling...' : 'Model unmatched terms (SNOMED Editorial Guide)'}
          </button>
        </div>
      )}
    </div>
  )
}
