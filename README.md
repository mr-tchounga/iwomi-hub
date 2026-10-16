# IWOMI Hub

Central app for IWOMI Technologies: projects and their components, partners and KYC, support tickets
(replacing the WhatsApp links in our apps), employees and departments, task tracking, a public support portal
with a voice-capable AI assistant, and an AI delivery accelerator.

**Stack:** Spring Boot 3.3 (Java 17) · PostgreSQL · Flyway · plain HTML/JS front end (served by Spring Boot) · any chat AI provider (Cursor, OpenAI-compatible APIs, Gemini, Groq, OpenRouter, Ollama, or Claude).

## Run it

### Docker (no Java or Maven needed)
```bash
export HUB_AI_API_KEY=your-key   # see "AI provider" below (Cursor, Gemini, Groq, OpenRouter, OpenAI, Claude)
docker compose up --build
```
Open http://localhost:8080.

### Local
```bash
docker compose up -d db               # PostgreSQL on localhost:5432
export HUB_AI_API_KEY=...   # see "AI provider"
mvn spring-boot:run
```

**Upgrading from the first version?** Just run it again: Flyway applies `V3__roles_portal.sql` on top of your data.

### Settings (environment variables)
| Variable | Default | Purpose |
|---|---|---|
| `HUB_AI_API_KEY` | none | Key of your AI provider (see "AI provider"). Without it the AI features show a setup message. |
| `HUB_ADMIN_USER` / `HUB_ADMIN_PASSWORD` | admin / admin | Administrator account (created on first start) |
| `HUB_TOKEN_SECRET` | dev value | Secret signing login tokens. **Set a long random value in production.** |
| `HUB_DEMO` | true | Creates one demo account per role and shows them on the login form. **Set to false in production.** |
| `HUB_DEMO_PASSWORD` | Demo2026! | Password of the demo accounts |

## AI provider (one variable)

Set **only `HUB_AI_API_KEY`**. The Hub recognises the provider from the key and picks its address and a default model:

| Key starts with | Provider | Notes |
|---|---|---|
| `crsr_` or `cursor_` | Cursor | Cloud Agents API. Only the key is required |
| `AIza` | Google Gemini | Has a free tier; very good in French |
| `gsk_` | Groq | Very fast, free tier |
| `sk-or-` | OpenRouter | One key, many models, some free |
| `sk-ant-` | Claude | Paid |
| `sk-` | OpenAI | Paid |

```bash
export HUB_AI_API_KEY=AIza...     # that is all
docker compose up --build
```

Optional overrides: `HUB_AI_MODEL` (if a default model is ever retired), `HUB_AI_BASE_URL`, `HUB_AI_PROVIDER` (`cursor`, `gemini`, `groq`, `openrouter`, `openai`, `ollama`, `anthropic`),
and `HUB_AI_MAX_TOKENS_PARAM=max_completion_tokens` for OpenAI models that reject `max_tokens`.

**Cursor (`crsr_...`):** the key is the only setting. The Hub calls `https://api.cursor.com` and leaves the model unset so Cursor uses your account default. Each reply is a no-repo Cloud Agent that is deleted when the answer finishes, so the first words take longer than a chat API. Set `HUB_AI_MODEL` only if you want a specific model id from Cursor's model list.

**Local and free (Ollama):** run Ollama, then set `HUB_AI_PROVIDER=ollama` (no key needed; from Docker also `HUB_AI_BASE_URL=http://host.docker.internal:11434/v1`)
and `HUB_AI_MODEL=llama3.1`. Give it a context of at least 8192 tokens (`OLLAMA_CONTEXT_LENGTH=8192`), because the assistant sends live Hub data.

## The assistants

Both assistants **stream**: words appear as they are written, with a typing indicator, a **Stop** button, and no waiting for the full answer.
They receive the live context in a single request (company knowledge in `src/main/resources/ai/*.md` plus a snapshot of the data the user's role may see),
so answers follow what the user types, the conversation so far, and the real projects, tickets and tasks. Edit the two `.md` files to teach them your own
products, processes and tone.

- **Public assistant (portal):** answers about IWOMI solutions and everyday fintech questions (failed mobile money transfer, KYC documents, loans...),
  in French, English or Pidgin, and can open a support ticket with a full summary when a human is needed.
- **Staff assistant (Ask AI):** answers from live Hub data limited to the user's role, and also helps with daily chores: replies to partners and customers,
  user stories, test cases, SQL, Spring Boot / Flutter / Odoo questions, mobile money and core banking reasoning.

### Voice
Press the microphone and speak; the answer is spoken sentence by sentence while it is still being written. The **speaker button** switches voice replies on or off,
and the **gear** opens: read-aloud on/off, choice of voice (per language), volume, speed, test and stop. Settings are remembered in the browser.
Speech uses the browser's built-in engines (Chrome, Edge, Safari), so the available voices depend on the device.

## Roles

| | Admin | CTO | HR (RH) | Accountant | Employee | Customer (external) | Visitor (no login) |
|---|---|---|---|---|---|---|---|
| Projects and components | R/W | R/W | R | R | R | catalog only | catalog only |
| Partners | R/W | R/W | R | R/W | R | – | – |
| Sales and amounts | R/W | R | – | R/W | – | – | – |
| Partner KYC (+ AI review) | R/W | R | – | R/W | – | – | – |
| Employees and departments | R/W | R | R/W | R | R | – | – |
| Tickets | all | all | – | – | own departments | own requests | create only |
| Tasks | R/W | R/W | R | – | R, update own status | – | – |
| AI accelerator | yes | yes | – | – | yes | – | – |
| Settings, routing, app keys | yes | yes | – | – | – | – | – |
| Users and roles | yes | – | – | – | – | – | – |
| Staff AI assistant (data it can use) | all | all | people, tasks, projects | partners, KYC, sales | projects, own tickets, tasks | – | – |
| Public AI assistant (voice) | – | – | – | – | – | yes | yes |

Rules are enforced on the server (`SecurityConfig`, plus checks in the ticket, task and notification controllers).
The staff AI assistant only receives the data its role may see, so it cannot be used to bypass permissions.

Demo accounts (password `Demo2026!`): `cto`, `rh`, `compta`, `brice`, `carine`, `estelle`, `david` (employees), `client` (customer).
The administrator is `admin` with `HUB_ADMIN_PASSWORD`. Admins manage users in **Users**.

## Public portal (no login needed)

- Catalog of solutions (hide a project with "Shown on the public portal = No").
- **AI assistant in French, English and Pidgin, voice or text** (see above). Pidgin is written by the AI, but browsers have no Pidgin
  speech engine: choose "Pidgin", speak English, and replies are written in Pidgin and read with an English voice.
- IWOMI facts come from the public catalog and knowledge file only. When it cannot solve something, it offers to **escalate to the team**: it creates a ticket
  with a full summary, routed to the right department by the routing rules.
- **Request help** form: anonymous visitors get a reference and a secret tracking code. To read replies they must log in (or create a customer
  account) and link the request with reference + code under **My requests**.
- Public endpoints are rate limited per IP.

Logged-in **customers** see their own requests, chat with the team (messages refresh every 10 seconds), and never see internal notes,
departments or assignees. Staff can mark a reply as an **internal note** (hidden from the customer).

## AI delivery accelerator (CTO, Admin, Employee)

Paste client requirements or an API specification (OpenAPI/JSON, SOAP/WSDL, text). You get user stories with acceptance criteria,
API documentation, test cases and an executable **test report** (set Pass/Fail per case, pass rate updates, download as Markdown).
Admin/CTO can create the user stories as tasks in a project with one click.

## AI KYC review (Admin, Accountant)

On a partner's KYC file, **AI review** checks completeness and consistency of the submitted data and attached document list, and returns a risk level,
issues and a recommendation. It assists the officer; approval stays manual. (It does not read document images.)

## Sending tickets from an app (instead of WhatsApp)

```bash
curl -X POST http://localhost:8080/api/public/tickets \
  -H "X-API-Key: demo-loan360-key" -H "Content-Type: application/json" \
  -d '{"subject":"Cannot see my repayment schedule","description":"Blank screen after login",
       "category":"BUG","priority":"HIGH","reporterName":"Mireille","reporterContact":"mireille@example.com","componentId":3}'
```
Other app endpoints (same `X-API-Key`):
- `GET /api/public/tickets/{reference}?contact=...` ticket status and public replies (the contact must match the reporter).
- `GET /api/public/partners/{id}` partner profile, KYC status/documents and projects.
- `GET /api/public/partners?email=...` find a partner by contact email.

Create one API key per app in **Settings > Connected apps**.

## Routing rule logic

Rules whose project, component type and category match (empty = any) are candidates. The most specific wins
(project beats component type beats category); ties go to the higher rule priority. No match: the default department.
This applies to tickets from apps, the portal form and the AI assistant (categories: QUESTION, BUG, HELP, OTHER).

## API overview

Login: `POST /api/auth/login`, `POST /api/auth/register` (customer), `GET /api/auth/me`. Send `Authorization: Bearer <token>`.
Staff: `/api/projects`, `/api/components`, `/api/partners`, `/api/sales`, `/api/kyc`, `/api/kyc-documents`, `/api/departments`, `/api/employees`,
`/api/tickets`, `/api/tasks`, `/api/routing-rules`, `/api/app-clients`, `/api/settings`, `/api/users`, `/api/notifications` (+ SSE `/stream`),
`/api/dashboard`, `/api/chat`, `/api/accelerator/generate`.
Portal: `/api/portal/projects`, `/api/portal/requests`, `/api/portal/chat`. Customer: `/api/me/tickets`.

## Demo script (about 5 minutes)

1. Visitor: open the portal, speak a problem to the assistant in French ("mon transfert mobile money a échoué"), let it create a ticket and note the reference + code.
2. Log in as `carine` (Employee): the live notification arrives, open the ticket, reply publicly and add an internal note.
3. Log in as `client`, link the request with reference + code, see the reply arrive in the conversation.
4. Log in as `cto`: open **Accelerator**, load the sample, generate, show stories, API docs, test report, create tasks in a project.
5. Log in as `compta`: show sales amounts and the AI KYC review; log in as `rh`: show employees and departments only.

## Known limits

- Without a configured AI provider the assistants show a setup message instead of answering.
- Login tokens last 12 hours; there is no email verification or password reset yet.
- Notifications are in-app and live (SSE). Email, WhatsApp and SMS sending are not implemented.
- KYC documents are stored as links, not uploaded files; AI KYC review does not read images.
- Per-IP rate limits use the connection address; behind a reverse proxy, configure forwarded headers.
- List endpoints filter in memory: fine for thousands of rows, add DB queries/paging beyond that.
