# GitHub Search Server

Lightweight local server to search GitHub repositories via GitHub API for research purposes.

## Prerequisites
- Node.js (>=14.x)
- npm
- A GitHub Personal Access Token with `public_repo` scope

## Setup

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Edit `.env` and set your GitHub token:
   ```properties
   GITHUB_TOKEN=your_token_here
   ```
3. Install dependencies:
   ```bash
   npm install
   ```

## Running

Start the server:
```bash
npm start
```
By default it listens on port `3000`.

## API

OpenAPI specification is available in `openapi.yaml`.

### Search Repositories
`GET /search?q={query}`
- **q**: search keywords (required)

Example:
```bash
curl "http://localhost:3000/search?q=android+password+manager"
```

## Integration with Android Studio

1. In Android Studio, enable the built-in HTTP Client.
2. Create a `.http` file (e.g., `github-search.http`) in your project root.

```http
### Search GitHub Repositories
GET http://localhost:3000/search?q=PASSWORD_MANAGER
```

3. Send requests directly from the editor and inspect JSON responses.
4. Optionally install the Swagger plugin and load `openapi.yaml` to get auto-complete and UI browsing.

