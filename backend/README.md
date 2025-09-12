# codesys-backend

Run with Java 17 + Maven:
```
cd backend
mvn spring-boot:run
```
Swagger UI: http://localhost:8080/swagger-ui.html

Enable real LLM:
- Set `ai.enabled=true` in `src/main/resources/application.yml`
- Export `OPENAI_API_KEY`
