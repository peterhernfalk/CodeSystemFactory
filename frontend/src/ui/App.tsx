
import React, { useState } from 'react'
import { MatchResults } from './MatchResults'
import { AiResults } from './AiResults'

export default function App(){
  const [termsText, setTermsText] = useState('MRI Heart\nDiabetes\nHeart attack')
  const [matches, setMatches] = useState<any[] | null>(null)
  const [ai, setAi] = useState<any | null>(null)

  const callMatch = async () => {
    const terms = termsText.split(/\n+/).map(t => t.trim()).filter(Boolean)
    const res = await fetch('/api/terms/match', {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ terms })
    })
    const data = await res.json()
    setMatches(data.matches)
  }

  const callAi = async () => {
    if(!matches) return
    const terms = matches.map(m => ({term: m.input, snomedId: m.matchedSctId || null}))
    const res = await fetch('/api/ai/definitions', {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ terms, context: 'Svensk vårdterminologi' })
    })
    const data = await res.json()
    setAi(data)
  }

  return (
    <div style={{maxWidth: 900, margin: '2rem auto', fontFamily: 'system-ui, sans-serif'}}>
      <h1>SNOMED Code System Builder</h1>
      <p>Paste one term per line. Click Match to find Swedish SNOMED concepts, then Ask AI for definitions, relations and use cases.</p>
      <textarea value={termsText} onChange={e=>setTermsText(e.target.value)} rows={8} style={{width:'100%'}} />
      <div style={{marginTop: 8, display:'flex', gap:8}}>
        <button onClick={callMatch}>Match terms</button>
        <button onClick={callAi} disabled={!matches}>Ask AI</button>
      </div>
      {matches && <MatchResults items={matches} />}
      {ai && <AiResults data={ai} />}
      <footer style={{marginTop: 24, opacity: 0.7}}>Backend at <code>http://localhost:8080</code></footer>
    </div>
  )
}
