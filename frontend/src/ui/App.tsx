
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
  matchedByServer: string
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

type RecommendationMode = 'UNMATCHED' | 'ADDITIONAL' | 'BOTH'

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
  const [selectedServer, setSelectedServer] = useState<'snowstorm' | 'ontoserver' | 'inera' | 'fallback_chain'>('fallback_chain')
  const [isMatchLoading, setIsMatchLoading] = useState(false)
  const [isAiLoading, setIsAiLoading] = useState(false)
  const [aiError, setAiError] = useState<string | null>(null)

  // Fetch backend version on mount
  useEffect(() => {
    getBackendVersion().then(setBackendVersion)
  }, [])

  const callMatch = async () => {
    const terms = termsText.split(/\n+/).map(t => t.trim()).filter(Boolean)

    // Clear previous results immediately while new matching is running
    setMatchedTerms([])
    setUnmatchedTerms([])
    setRecommendations([])
    setSuggestedAdditional([])
    setAiError(null)
    setIsMatchLoading(true)

    try {
      const res = await fetch(`${API_BASE_URL}/terms/match`, {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify(
          selectedServer === 'fallback_chain'
            ? { terms, serverChain: ['snowstorm', 'ontoserver', 'inera'] }
            : { terms, server: selectedServer }
        )
      })

      if (!res.ok) {
        throw new Error(`Term matching request failed (${res.status})`)
      }

      const data = await res.json()
      setMatchedTerms(data.matched || [])
      setUnmatchedTerms(data.unmatched || [])
    } catch (error) {
      setAiError(error instanceof Error ? error.message : 'Failed to match terms.')
      setMatchedTerms([])
      setUnmatchedTerms([])
    } finally {
      setIsMatchLoading(false)
    }
  }

  const callAiRecommend = async (mode: RecommendationMode) => {
    // Allow AI recommendations even if all terms are matched (for additional suggestions)
    if (matchedTerms.length === 0) {
      setAiError('Match at least one term before requesting AI recommendations.')
      return
    }
    
    const unmatchedTermList = mode === 'ADDITIONAL'
      ? []
      : unmatchedTerms.map(u => u.inputTerm)
    const matchedSnomedIds = matchedTerms.map(m => m.snomedId)

    setAiError(null)
    setIsAiLoading(true)

    try {
      const res = await fetch(`${API_BASE_URL}/ai/recommend`, {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({ 
          unmatchedTerms: unmatchedTermList,
          matchedSnomedIds: matchedSnomedIds,
          context: 'Swedish healthcare terminology',
          recommendationMode: mode
        })
      })

      if (!res.ok) {
        let message = `AI recommendation request failed (${res.status})`
        try {
          const errorText = await res.text()
          if (errorText) {
            message = `${message}: ${errorText}`
          }
        } catch {
          // Keep the default message if error payload cannot be read.
        }
        throw new Error(message)
      }

      const data = await res.json()
      const nextRecommendations = data.recommendations || []
      const nextSuggestedAdditional = data.suggestedAdditional || []
      setRecommendations(nextRecommendations)
      setSuggestedAdditional(nextSuggestedAdditional)

      if (nextRecommendations.length === 0 && nextSuggestedAdditional.length === 0) {
        setAiError(
          mode === 'UNMATCHED'
            ? 'No recommendations were found for unmatched terms.'
            : 'No additional suggestions were found for the current matched terms.'
        )
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to fetch AI recommendations.'
      setAiError(message)
      setRecommendations([])
      setSuggestedAdditional([])
    } finally {
      setIsAiLoading(false)
    }
  }

  const callAiRecommendForUnmatched = async () => {
    await callAiRecommend('UNMATCHED')
  }

  const callAiRecommendForAdditional = async () => {
    await callAiRecommend('ADDITIONAL')
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

  const isLoading = isMatchLoading || isAiLoading

  return (
    <div style={{maxWidth: 1200, margin: '2rem auto', fontFamily: 'system-ui, sans-serif', padding: '0 1rem'}}>
      {isLoading && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(255, 255, 255, 0.6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 2000
          }}
        >
          <div style={{textAlign: 'center'}}>
            <div
              style={{
                width: 56,
                height: 56,
                border: '6px solid #d9e2ef',
                borderTop: '6px solid #007bff',
                borderRadius: '50%',
                animation: 'spin 0.8s linear infinite',
                margin: '0 auto'
              }}
            />
            <p style={{marginTop: 12, fontWeight: 600, color: '#1f2937'}}>
              {isMatchLoading ? 'Matching terms...' : 'Getting AI recommendations...'}
            </p>
          </div>
        </div>
      )}
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
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
        <div style={{marginTop: 12, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap'}}>
          <label style={{display: 'flex', alignItems: 'center', gap: 8}}>
            <strong>SNOMED CT Server:</strong>
            <select 
              value={selectedServer} 
              onChange={e => setSelectedServer(e.target.value as 'snowstorm' | 'ontoserver' | 'inera' | 'fallback_chain')}
              style={{
                padding: '6px 12px', 
                fontSize: '0.95em', 
                borderRadius: 4, 
                border: '1px solid #ddd',
                backgroundColor: 'white',
                cursor: 'pointer'
              }}
            >
              <option value="fallback_chain">Fallback: Snowstorm → Ontoserver → Inera</option>
              <option value="snowstorm">Snowstorm (Default)</option>
              <option value="ontoserver">Ontoserver (FHIR)</option>
              <option value="inera">Inera Terminologitjänsten (Swedish)</option>
            </select>
          </label>
          <button 
            onClick={callMatch} 
            disabled={isLoading}
            style={{
              padding: '8px 16px',
              backgroundColor: isLoading ? '#6c757d' : '#007bff',
              color: 'white',
              border: 'none',
              borderRadius: 4,
              cursor: isLoading ? 'not-allowed' : 'pointer',
              fontSize: '1em',
              fontWeight: 'bold'
            }}
          >
            {isMatchLoading ? 'Matching Terms...' : 'Match Terms'}
          </button>
        </div>
      </div>

      {(matchedTerms.length > 0 || unmatchedTerms.length > 0) && (
        <>
          <MatchResults 
            matched={matchedTerms} 
            unmatched={unmatchedTerms}
            onMatchedChange={setMatchedTerms}
            onUnmatchedChange={setUnmatchedTerms}
            onRecommend={callAiRecommendForUnmatched}
            recommendLoading={isAiLoading}
          />

          {aiError && (
            <div style={{marginTop: 12, padding: 12, borderRadius: 6, backgroundColor: '#fff3cd', color: '#856404', border: '1px solid #ffeeba'}}>
              {aiError}
            </div>
          )}
          
          {/* Show build button if we have matched terms, even without recommendations */}
          {matchedTerms.length > 0 && (recommendations.length === 0 && suggestedAdditional.length === 0) && !showMetadataForm && (
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
                  onClick={callAiRecommendForAdditional}
                  disabled={isAiLoading}
                  style={{
                    padding: '10px 20px',
                    backgroundColor: isAiLoading ? '#6c757d' : '#007bff',
                    color: 'white',
                    border: 'none',
                    borderRadius: 4,
                    cursor: isAiLoading ? 'not-allowed' : 'pointer',
                    fontSize: '1em'
                  }}
                >
                  {isAiLoading
                    ? 'Getting AI Recommendations...'
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

          {/* Show metadata form when showMetadataForm is true, even without recommendations */}
          {showMetadataForm && (recommendations.length === 0 && suggestedAdditional.length === 0) && (
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
