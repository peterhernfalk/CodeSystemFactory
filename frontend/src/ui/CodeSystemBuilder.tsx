
import React from 'react'

interface CodeSystem {
  name: string
  version: string
  description: string | null
  publisher: string | null
  contact: string | null
  items: Array<{
    code: string
    display: string
    definition: string | null
    relations: string[]
    source: string
  }>
}

interface Props {
  codeSystem: CodeSystem
  onExport: (format: 'FHIR' | 'CSV' | 'EXCEL') => void
}

export function CodeSystemBuilder({codeSystem, onExport}: Props){
  return (
    <div style={{marginTop:24, border: '2px solid #28a745', padding: 16, borderRadius: 8, backgroundColor: '#f8fff9'}}>
      <h2>Code System Created</h2>
      
      <div style={{marginTop:12}}>
        <div><strong>Name:</strong> {codeSystem.name}</div>
        <div><strong>Version:</strong> {codeSystem.version}</div>
        {codeSystem.description && <div><strong>Description:</strong> {codeSystem.description}</div>}
        {codeSystem.publisher && <div><strong>Publisher:</strong> {codeSystem.publisher}</div>}
        {codeSystem.contact && <div><strong>Contact:</strong> {codeSystem.contact}</div>}
        <div><strong>Items:</strong> {codeSystem.items.length}</div>
      </div>

      <div style={{marginTop:16}}>
        <h3>Code System Items</h3>
        <table border={1} cellPadding={8} style={{width:'100%', borderCollapse:'collapse', marginTop:8}}>
          <thead>
            <tr style={{backgroundColor: '#e9ecef'}}>
              <th>Code</th><th>Display</th><th>Definition</th><th>Source</th>
            </tr>
          </thead>
          <tbody>
            {codeSystem.items.map((item, i) => (
              <tr key={i}>
                <td><code>{item.code}</code></td>
                <td>{item.display}</td>
                <td>{item.definition || '—'}</td>
                <td>
                  <span style={{
                    padding: '2px 8px',
                    borderRadius: 4,
                    fontSize: '0.85em',
                    backgroundColor: item.source === 'SNOMED_MATCH' ? '#d4edda' : '#fff3cd',
                    color: item.source === 'SNOMED_MATCH' ? '#155724' : '#856404'
                  }}>
                    {item.source}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{marginTop:20}}>
        <h3>Export Code System</h3>
        <div style={{display: 'flex', gap: 8, marginTop: 8}}>
          <button 
            onClick={() => onExport('FHIR')}
            style={{padding: '10px 20px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer'}}
          >
            Export as FHIR JSON
          </button>
          <button 
            onClick={() => onExport('CSV')}
            style={{padding: '10px 20px', backgroundColor: '#17a2b8', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer'}}
          >
            Export as CSV
          </button>
          <button 
            onClick={() => onExport('EXCEL')}
            style={{padding: '10px 20px', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer'}}
          >
            Export as Excel
          </button>
        </div>
      </div>
    </div>
  )
}

