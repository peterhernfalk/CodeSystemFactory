
# SNOMED CT + AI Code System Builder

- backend: Spring Boot API
- frontend: React (Vite)

Quick start:
1) Backend: `cd backend && mvn spring-boot:run`
2) Frontend: `cd frontend && npm install && npm run dev`

## Backend
After Warp made adjustments:
You can now run:
•  mvn spring-boot:run (uses PostgreSQL - requires DB setup)
•  mvn spring-boot:run -Dspring-boot.run.profiles=dev (uses H2 in-memory database)
Start command: mvn spring-boot:run -Dspring-boot.run.profiles=dev

How it works
•  Development Environment: Uses a mock API key (sk-mock-dev-key) with AI features disabled (ai.enabled: false). This allows the Spring AI beans to be created without making actual API calls.
•  Production Environment: The main application.yml still expects a real OPENAI_API_KEY environment variable when ai.enabled is true (default).
•  Mock responses: When AI is disabled in dev, the SpringAiService returns mock responses instead of calling OpenAI.

## Frontend
I've successfully fixed the module not found error by:
1. Installing the missing module: npm install @vitejs/plugin-react
2. Fixing the ESM module loading issue: Added "type": "module" to package.json to ensure the project is treated as an ES module project, allowing the ES6 import syntax in vite.config.ts to work properly
The development server is now running on http://localhost:5173/ and should be accessible in your browser. The proxy configuration in your vite.config.ts will forward API requests to http://localhost:8080 as intended.
Start command: npm run dev