
import React from 'react'
export function MatchResults({items}:{items:any[]}){
  return (
    <div style={{marginTop:16}}>
      <h2>Matches</h2>
      <table border={1} cellPadding={6} style={{width:'100%', borderCollapse:'collapse'}}>
        <thead>
          <tr>
            <th>Input</th><th>SNOMED CT ID</th><th>Preferred (sv)</th><th>FSN (sv)</th><th>Similarity</th><th>Status</th>
          </tr>
        </thead>
        <tbody>
          {items.map((m,i)=>(
            <tr key={i}>
              <td>{m.input}</td>
              <td>{m.matchedSctId || '—'}</td>
              <td>{m.preferredTermSv || '—'}</td>
              <td>{m.fsnSv || '—'}</td>
              <td>{m.similarity.toFixed(2)}</td>
              <td>{m.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
