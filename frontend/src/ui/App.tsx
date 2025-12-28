
import React, { useState, useEffect } from 'react'
import { MatchResults } from './MatchResults'
import { AiRecommendations } from './AiRecommendations'
import { CodeSystemBuilder } from './CodeSystemBuilder'
import { API_BASE_URL } from '../config/api'
import { FRONTEND_VERSION, getBackendVersion } from '../config/version'

interface MatchedTerm {
  inputTerm: string
  snomedId: string
  preferredTerm: string
  fsn: string
  similarity: number
  description: string | null
}

interface UnmatchedTerm {
  inputTerm: string
  reason: string
}

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

interface CodeSystemMetadata {
  name: string
  version: string
  description: string
  publisher: string
  contact: string
}

export default function App(){
  const [termsText, setTermsText] = useState('MRI Heart\nDiabetes\nHeart attack')
  const [matchedTerms, setMatchedTerms] = useState<MatchedTerm[]>([])
  const [unmatchedTerms, setUnmatchedTerms] = useState<UnmatchedTerm[]>([])
  const [recommendations, setRecommendations] = useState<Recommendation[]>([])
  const [suggestedAdditional, setSuggestedAdditional] = useState<SuggestedTerm[]>([])
  const [selectedSuggested, setSelectedSuggested] = useState<Set<number>>(new Set())
  const [codeSystem, setCodeSystem] = useState<any>(null)
  const [metadata, setMetadata] = useState<CodeSystemMetadata>({
    name: 'Swedish Cardiology Terms',
    version: '1.0.0',
    description: '',
    publisher: '',
    contact: ''
  })
  const [showMetadataForm, setShowMetadataForm] = useState(false)
  const [backendVersion, setBackendVersion] = useState<string | null>(null)

  // Fetch backend version on mount
  useEffect(() => {
    getBackendVersion().then(setBackendVersion)
  }, [])

  const callMatch = async () => {
    const terms = termsText.split(/\n+/).map(t => t.trim()).filter(Boolean)
    const res = await fetch(`${API_BASE_URL}/terms/match`, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ terms })
    })
    const data = await res.json()
    setMatchedTerms(data.matched || [])
    setUnmatchedTerms(data.unmatched || [])
    setRecommendations([])
    setSuggestedAdditional([])
  }

  const callAiRecommend = async () => {
    // Allow AI recommendations even if all terms are matched (for additional suggestions)
    if(matchedTerms.length === 0) return
    
    const unmatchedTermList = unmatchedTerms.map(u => u.inputTerm)
    const matchedSnomedIds = matchedTerms.map(m => m.snomedId)
    
    const res = await fetch(`${API_BASE_URL}/ai/recommend`, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ 
        unmatchedTerms: unmatchedTermList,
        matchedSnomedIds: matchedSnomedIds,
        context: 'Swedish healthcare terminology'
      })
    })
    const data = await res.json()
    setRecommendations(data.recommendations || [])
    setSuggestedAdditional(data.suggestedAdditional || [])
  }

  const buildCodeSystem = async () => {
    if (!showMetadataForm) {
      setShowMetadataForm(true)
      return
    }

    const matchedForBuild = matchedTerms.map(m => ({
      inputTerm: m.inputTerm,
      snomedId: m.snomedId,
      preferredTerm: m.preferredTerm,
      fsn: m.fsn,
      description: m.description
    }))

    const recommendedForBuild = recommendations.map(r => ({
      inputTerm: r.inputTerm,
      snomedId: r.recommendedSnomedId,
      recommendedTerm: r.recommendedTerm,
      fsn: r.fsn,
      definition: r.definition,
      relations: r.relations
    }))

    const selectedSuggestedForBuild = Array.from(selectedSuggested).map(i => ({
      snomedId: suggestedAdditional[i].snomedId,
      term: suggestedAdditional[i].term,
      fsn: suggestedAdditional[i].fsn,
      reason: suggestedAdditional[i].reason,
      definition: suggestedAdditional[i].definition,
      relations: suggestedAdditional[i].relations
    }))

    const res = await fetch(`${API_BASE_URL}/codesystems/build`, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({
        metadata,
        matchedTerms: matchedForBuild,
        recommendedTerms: recommendedForBuild,
        suggestedTerms: selectedSuggestedForBuild
      })
    })
    const data = await res.json()
    setCodeSystem(data.codeSystem)
    setShowMetadataForm(false)
  }

  const exportCodeSystem = async (format: 'FHIR' | 'CSV' | 'EXCEL') => {
    if(!codeSystem) return

    const res = await fetch(`${API_BASE_URL}/codesystems/export`, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({
        codeSystem,
        format
      })
    })

    const blob = await res.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${codeSystem.name}_${codeSystem.version}.${format === 'FHIR' ? 'json' : format === 'CSV' ? 'csv' : 'xlsx'}`
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
  }

  return (
    <div style={{maxWidth: 1200, margin: '2rem auto', fontFamily: 'system-ui, sans-serif', padding: '0 1rem'}}>
      <h1>SNOMED Code System Builder</h1>
      <p>Enter terms, match with SNOMED CT, get AI recommendations, and export your code system.</p>
      
      <div style={{marginTop: 16}}>
        <label><strong>Enter terms (one per line):</strong></label>
        <textarea 
          value={termsText} 
          onChange={e=>setTermsText(e.target.value)} 
          rows={8} 
          style={{width:'100%', marginTop: 8, padding: 8, fontFamily: 'monospace'}} 
        />
        <button onClick={callMatch} style={{marginTop: 8, padding: '8px 16px'}}>
          Match Terms
        </button>
      </div>

      {(matchedTerms.length > 0 || unmatchedTerms.length > 0) && (
        <>
          <MatchResults 
            matched={matchedTerms} 
            unmatched={unmatchedTerms}
            onMatchedChange={setMatchedTerms}
            onUnmatchedChange={setUnmatchedTerms}
            onRecommend={callAiRecommend}
          />
          
          {/* Show build button if we have matched terms, even without recommendations */}
          {matchedTerms.length > 0 && (recommendations.length === 0 && suggestedAdditional.length === 0) && (
            <div style={{marginTop: 24, padding: 16, border: '1px solid #ddd', borderRadius: 8, backgroundColor: '#f8f9fa'}}>
              <h3>Ready to Build Code System</h3>
              <p style={{fontSize: '0.9em', color: '#666', marginBottom: 16}}>
                You have {matchedTerms.length} matched term{matchedTerms.length !== 1 ? 's' : ''}. 
                {unmatchedTerms.length > 0 && (
                  <> You can get AI recommendations for {unmatchedTerms.length} unmatched term{unmatchedTerms.length !== 1 ? 's' : ''}, or build the code system with just the matched terms.</>
                )}
                {unmatchedTerms.length === 0 && (
                  <> All terms are matched. You can build the code system now or get AI recommendations for additional suggestions.</>
                )}
              </p>
              <div style={{display: 'flex', gap: 8}}>
                <button
                  onClick={callAiRecommend}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: '#007bff',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '1em'
                  }}
                >
                  {unmatchedTerms.length > 0 
                    ? 'Get AI Recommendations for Unmatched Terms'
                    : 'Get AI Recommendations for Additional Suggestions'}
                </button>
                <button
                  onClick={buildCodeSystem}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: '#28a745',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '1em'
                  }}
                >
                  Build Code System with Matched Terms
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {(recommendations.length > 0 || suggestedAdditional.length > 0) && (
        <>
          <AiRecommendations 
            recommendations={recommendations}
            suggestedAdditional={suggestedAdditional}
            selectedSuggested={selectedSuggested}
            onRecommendationsChange={setRecommendations}
            onSuggestedChange={setSuggestedAdditional}
            onSelectedSuggestedChange={setSelectedSuggested}
            onBuild={buildCodeSystem}
          />
          
          {showMetadataForm && (
            <div style={{marginTop: 24, border: '2px solid #007bff', padding: 16, borderRadius: 8, backgroundColor: '#f0f8ff'}}>
              <h3>Code System Metadata</h3>
              <p style={{fontSize: '0.9em', color: '#666', marginBottom: 16}}>
                Enter metadata for your code system before building
              </p>
              <div style={{display: 'grid', gap: 12}}>
                <div>
                  <label style={{display: 'block', marginBottom: 4, fontWeight: 'bold'}}>
                    Name <span style={{color: 'red'}}>*</span>
                  </label>
                  <input
                    type="text"
                    value={metadata.name}
                    onChange={e => setMetadata({...metadata, name: e.target.value})}
                    style={{width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc'}}
                    required
                  />
                </div>
                <div>
                  <label style={{display: 'block', marginBottom: 4, fontWeight: 'bold'}}>
                    Version <span style={{color: 'red'}}>*</span>
                  </label>
                  <input
                    type="text"
                    value={metadata.version}
                    onChange={e => setMetadata({...metadata, version: e.target.value})}
                    style={{width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc'}}
                    placeholder="1.0.0"
                    required
                  />
                </div>
                <div>
                  <label style={{display: 'block', marginBottom: 4, fontWeight: 'bold'}}>Description</label>
                  <textarea
                    value={metadata.description}
                    onChange={e => setMetadata({...metadata, description: e.target.value})}
                    style={{width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc', minHeight: '80px'}}
                    placeholder="Description of the code system"
                  />
                </div>
                <div>
                  <label style={{display: 'block', marginBottom: 4, fontWeight: 'bold'}}>Publisher</label>
                  <input
                    type="text"
                    value={metadata.publisher}
                    onChange={e => setMetadata({...metadata, publisher: e.target.value})}
                    style={{width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc'}}
                    placeholder="Organization or individual"
                  />
                </div>
                <div>
                  <label style={{display: 'block', marginBottom: 4, fontWeight: 'bold'}}>Contact</label>
                  <input
                    type="text"
                    value={metadata.contact}
                    onChange={e => setMetadata({...metadata, contact: e.target.value})}
                    style={{width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc'}}
                    placeholder="Email or contact information"
                  />
                </div>
              </div>
              <div style={{marginTop: 16, display: 'flex', gap: 8}}>
                <button
                  onClick={buildCodeSystem}
                  disabled={!metadata.name || !metadata.version}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: metadata.name && metadata.version ? '#28a745' : '#ccc',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: metadata.name && metadata.version ? 'pointer' : 'not-allowed',
                    fontSize: '1em'
                  }}
                >
                  Build Code System
                </button>
                <button
                  onClick={() => setShowMetadataForm(false)}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: '#6c757d',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: '1em'
                  }}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {codeSystem && (
        <CodeSystemBuilder 
          codeSystem={codeSystem}
          onExport={exportCodeSystem}
        />
      )}

      <footer style={{marginTop: 24, padding: '16px 0', borderTop: '1px solid #ddd', opacity: 0.7, fontSize: '0.9em'}}>
        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8}}>
          <div>
            Backend API at <code>{API_BASE_URL}</code>
          </div>
          <div style={{display: 'flex', gap: 12, alignItems: 'center'}}>
            <span>Frontend: <strong>v{FRONTEND_VERSION}</strong></span>
            {backendVersion && (
              <span>Backend: <strong>v{backendVersion.replace('-SNAPSHOT', '')}</strong></span>
            )}
          </div>
        </div>
      </footer>
    </div>
  )
}
