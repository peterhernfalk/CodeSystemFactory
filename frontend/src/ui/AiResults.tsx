
import React from 'react'
export function AiResults({data}:{data:any}){
  return (
    <div style={{marginTop:16}}>
      <h2>AI Results</h2>
      {data.results?.map((r:any, idx:number)=>(
        <div key={idx} style={{border:'1px solid #ccc', padding:12, marginBottom:12}}>
          <div><strong>Term:</strong> {r.term} {r.snomedId ? `(SNOMED: ${r.snomedId})` : ''}</div>
          <div><strong>Definition:</strong> {r.definition}</div>
          <div><strong>Relations:</strong> {(r.relations||[]).join(', ')}</div>
          <div><strong>Motivation:</strong> {r.motivation}</div>
          <div><strong>Use cases:</strong>
            <ul>
              {(r.useCases||[]).map((u:string,i:number)=>(<li key={i}>{u}</li>))}
            </ul>
          </div>
        </div>
      ))}
    </div>
  )
}
