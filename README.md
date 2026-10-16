# IWOMI Hub

Central app for IWOMI Technologies: projects and their components, partners and KYC, support tickets
(replacing the WhatsApp links in our apps), employees and departments, task tracking, a public support portal
with a voice-capable AI assistant, and an AI delivery accelerator.

The [use case specifications](#use-case-specifications) are the behavioral contract: who does what, and what the Hub does in response.

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

**Upgrading an existing database?** On startup, Flyway applies any migration that is not yet recorded. If startup stops with a checksum mismatch, that migration file was edited after it had already been applied. For a local demo database, reset it with `docker compose down -v` and start again (this deletes the Postgres volume and reloads the demo data). Do not edit a migration that has already run; add a new `V4__….sql` instead.

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
| Opportunities | manage | read + advice | read + advice | read + advice | read + advice | – | – |
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
`/api/dashboard`, `/api/chat`, `/api/accelerator/generate`, `/api/opportunities` (+ `POST /advice`).
Portal: `/api/portal/projects`, `/api/portal/requests`, `/api/portal/chat`. Customer: `/api/me/tickets`.

## Demo script (about 5 minutes)

1. Visitor: open the portal, speak a problem to the assistant in French ("mon transfert mobile money a échoué"), let it create a ticket and note the reference + code.
2. Log in as `carine` (Employee): the live notification arrives, open the ticket, reply publicly and add an internal note.
3. Log in as `client`, link the request with reference + code, see the reply arrive in the conversation.
4. Log in as `cto`: open **Accelerator**, load the sample, generate, show stories, API docs, test report, create tasks in a project.
5. Log in as `compta`: show sales amounts and the AI KYC review; log in as `rh`: show employees and departments only.

## Use case specifications

Actors are the columns of the [Roles](#roles) table, plus an **IWOMI app** (an external product calling the Hub with `X-API-Key`). A **Visitor** has no account. A **Customer** is a logged-in external user.

Rules that apply to every use case:

- The server enforces the role. The screen only offers actions that role may perform.
- Login tokens last 12 hours. There is no email verification or password reset.
- AI steps need a configured provider (`HUB_AI_API_KEY`, or Ollama). Without one, the Hub shows a setup message and does not invent an answer.
- Repeated calls from the same address are refused with a "try again later" message (login, registration, portal chat, anonymous requests, claiming a request).

| ID | Use case | Primary actor |
|---|---|---|
| [UC-01](#uc-01--browse-the-solution-catalog) | Browse the solution catalog | Visitor |
| [UC-02](#uc-02--ask-the-public-assistant) | Ask the public assistant | Visitor |
| [UC-03](#uc-03--hand-a-problem-to-the-team) | Hand a problem to the team | Visitor |
| [UC-04](#uc-04--request-help-without-an-account) | Request help without an account | Visitor |
| [UC-05](#uc-05--create-a-customer-account) | Create a customer account | Visitor |
| [UC-06](#uc-06--log-in) | Log in | Visitor, Customer, Staff |
| [UC-07](#uc-07--link-an-anonymous-request) | Link an anonymous request | Customer |
| [UC-08](#uc-08--follow-a-request) | Follow a request | Customer |
| [UC-09](#uc-09--open-a-ticket-from-an-app) | Open a ticket from an app | IWOMI app |
| [UC-10](#uc-10--read-a-ticket-from-an-app) | Read a ticket from an app | IWOMI app |
| [UC-11](#uc-11--read-a-partner-from-an-app) | Read a partner from an app | IWOMI app |
| [UC-12](#uc-12--handle-a-support-ticket) | Handle a support ticket | Employee |
| [UC-13](#uc-13--get-notified-of-ticket-activity) | Get notified of ticket activity | Employee |
| [UC-14](#uc-14--see-the-operations-dashboard) | See the operations dashboard | Staff |
| [UC-15](#uc-15--maintain-projects-and-components) | Maintain projects and components | Admin, CTO |
| [UC-16](#uc-16--record-a-sale) | Record a sale | Admin, Accountant |
| [UC-17](#uc-17--review-a-partners-kyc) | Review a partner's KYC | Admin, Accountant |
| [UC-18](#uc-18--maintain-the-team) | Maintain the team | Admin, HR |
| [UC-19](#uc-19--track-delivery-tasks) | Track delivery tasks | Admin, CTO, Employee |
| [UC-20](#uc-20--generate-a-delivery-pack) | Generate a delivery pack | Admin, CTO, Employee |
| [UC-21](#uc-21--ask-the-staff-assistant) | Ask the staff assistant | Staff |
| [UC-22](#uc-22--administer-the-hub) | Administer the Hub | Admin, CTO |
| [UC-23](#uc-23--use-a-stored-opportunity) | Use a stored opportunity | Employee |

### UC-01 — Browse the solution catalog

**Actors:** Visitor, Customer.  
**Goal:** See which IWOMI solutions are published.  
**Preconditions:** None.

1. The actor opens the portal.
2. The Hub lists projects marked "Shown on the public portal", with status, description, and components (type and version).
3. The actor can ask the assistant about a solution (UC-02) or request help for it (UC-04).

**Extensions**

- **2a.** A project with public visibility turned off (UC-15) is absent from the catalog. It remains visible to staff.

**Result:** The actor sees only published solutions. No login is required.

### UC-02 — Ask the public assistant

**Actors:** Visitor, Customer.  
**Goal:** Get an answer about an IWOMI solution or an everyday fintech question (a failed mobile-money transfer, KYC documents, a loan).  
**Preconditions:** An AI provider is configured.

1. The actor opens **Ask IWOMI** and chooses French, English, or Pidgin.
2. The actor types a question. The Hub streams the reply as it is written. **Stop** cancels the reply.
3. The answer uses only the public knowledge file and the live public catalog. It does not invent features, prices, or delivery promises.
4. The actor may continue the conversation. Later replies use the messages already exchanged.

**Extensions**

- **2a. Voice.** The actor presses the microphone and speaks. The reply is read aloud sentence by sentence while it is still being written. The speaker button turns voice replies on or off. The gear sets read-aloud, voice, volume, and speed; the browser remembers those choices. Speech uses the browser engine (Chrome, Edge, Safari).
- **2b. Pidgin.** The assistant writes in Pidgin. Browsers have no Pidgin speech engine: the actor speaks English, and an English voice reads the Pidgin reply.
- **2c. The question needs a person.** The assistant offers to pass the problem to the team (UC-03).
- **2d. No provider.** The Hub shows how to set `HUB_AI_API_KEY` and does not answer.

**Result:** The actor has an answer grounded in published IWOMI information, or a path to a human.

### UC-03 — Hand a problem to the team

**Actors:** Visitor, Customer. The public assistant starts this from UC-02.  
**Goal:** Turn a conversation the assistant cannot close into a support ticket.  
**Preconditions:** UC-02 is in progress, and the assistant has decided a human is needed.

1. The assistant asks the actor to confirm.
2. The Hub creates a ticket with a full summary of the conversation, category QUESTION, BUG, HELP, or OTHER, and source of the portal.
3. Routing (see [Routing rule logic](#routing-rule-logic)) assigns a department. That department is notified (UC-13).
4. A visitor receives a reference (`TCK-#####`) and a secret tracking code. A logged-in customer receives the reference only; the ticket is already on their account.

**Extensions**

- **4a.** The visitor later links the request after creating an account (UC-05, UC-07).

**Result:** A routed ticket exists. The customer can follow it (UC-08). Staff can handle it (UC-12).

### UC-04 — Request help without an account

**Actors:** Visitor. A Customer uses the same form and skips the claim step.  
**Goal:** Ask a question, report a bug, or request help on a solution.  
**Preconditions:** None.

1. The actor opens **Request help**.
2. A visitor enters name and a contact (email or phone). A customer is already identified.
3. The actor chooses a published solution or "General / not sure", a type (Question, Something is not working, I need help), a subject, and a description.
4. The Hub creates an OPEN ticket, routes it, and notifies the department.
5. A visitor is shown the reference and the tracking code, and is told to save both. A customer's ticket appears under **My requests**.

**Extensions**

- **3a.** An unpublished project cannot be selected.
- **5a.** Five anonymous requests per address per hour are refused.

**Result:** The team has a ticket. The visitor can read replies only after UC-07.

### UC-05 — Create a customer account

**Actors:** Visitor.  
**Goal:** Obtain a customer login, so the actor can follow requests.  
**Preconditions:** The username is not already taken.

1. The actor opens **Create account** and enters full name, username, password (at least 8 characters), and an optional email.
2. The Hub creates an active user with the Customer role only. Staff roles cannot be self-assigned.
3. The actor is logged in (UC-06).

**Extensions**

- **1a.** The username must be 3–40 characters: letters, digits, dot, dash, or underscore. Anything else is rejected.
- **2a.** A taken username is rejected. More than five registrations per address per hour are refused.

**Result:** A customer session exists. The account can claim anonymous requests (UC-07) and open new ones (UC-04).

### UC-06 — Log in

**Actors:** Visitor with an account, Customer, any staff role.  
**Goal:** Open a session for the account's role.  
**Preconditions:** The account exists and is active.

1. The actor enters username and password. In demo mode the login form lists the demo accounts and fills the form when one is clicked.
2. The Hub checks the password and returns a token plus the user profile (name, role).
3. A customer stays on the portal, with **My requests**. A staff user enters the staff application on the dashboard (UC-14).

**Extensions**

- **2a.** An unknown user, a wrong password, or an inactive account is rejected as "Invalid username or password". Twenty failures per address per 15 minutes are refused.
- **3a.** The token is sent as `Authorization: Bearer` on later calls. After 12 hours it expires and the actor must log in again.
- **3b.** **Log out** drops the token immediately.

**Result:** Later use cases run as that role. Demo mode is off in production (`HUB_DEMO=false`), so the account list is hidden.

### UC-07 — Link an anonymous request

**Actors:** Customer.  
**Goal:** Attach a ticket created before login (UC-03 or UC-04) to this account.  
**Preconditions:** The actor is logged in. The ticket has no owner yet, and the actor has its reference and tracking code.

1. The actor opens **My requests** and enters the reference and the tracking code.
2. The Hub matches both, sets the actor as the requester, and clears the tracking code so it cannot be reused.
3. The request appears in **My requests**.

**Extensions**

- **2a.** A wrong pair, a ticket already claimed, or a ticket that never had a code is reported as not found. The response does not say which part was wrong.
- **2b.** Ten attempts per account per hour are refused.

**Result:** The customer can read the thread and reply (UC-08). The code is spent.

### UC-08 — Follow a request

**Actors:** Customer.  
**Goal:** Read the team's replies and continue the conversation.  
**Preconditions:** The actor is logged in, and the ticket belongs to them (created while logged in, or linked in UC-07).

1. The actor opens **My requests** and selects a ticket.
2. The Hub shows subject, description, status, solution name, and public messages. Department, assignee, and internal notes are omitted.
3. The thread refreshes about every 10 seconds.
4. The actor writes a message. The Hub stores it as a customer message and notifies the ticket's department.

**Extensions**

- **4a.** A reply on a RESOLVED or CLOSED ticket sets the status back to OPEN.
- **2a.** Another customer's ticket is not found.

**Result:** The customer and the team share one public thread. Internal notes stay on the staff side (UC-12).

### UC-09 — Open a ticket from an app

**Actors:** IWOMI app.  
**Goal:** Replace a "contact us on WhatsApp" link with a ticket in the Hub.  
**Preconditions:** An Admin or CTO has created an active API key for the app (UC-22).

1. The app calls `POST /api/public/tickets` with header `X-API-Key` and a subject, description, category, priority, reporter name, and reporter contact. It may pass a component and a partner.
2. The Hub creates an OPEN ticket. The project is the one in the body, or the app's default project when the body omits it. The source is the app's name unless the body sets one.
3. The Hub routes the ticket, notifies the department, and returns the id, reference, and status.

**Extensions**

- **1a.** A missing or unknown key is rejected. The app is not a staff user and cannot call staff endpoints.

**Result:** Staff handle the ticket in UC-12. The app can show status with UC-10.

### UC-10 — Read a ticket from an app

**Actors:** IWOMI app.  
**Goal:** Show the reporter the status and the public replies.  
**Preconditions:** UC-09 succeeded. The app still has a valid API key. The app knows the reference and the reporter contact stored on the ticket.

1. The app calls `GET /api/public/tickets/{reference}?contact=...`.
2. The Hub returns the reference, subject, status, priority, creation time, and public messages only when the contact matches the reporter, ignoring case.

**Extensions**

- **2a.** A wrong contact, or an unknown reference, is not found. Internal notes are never included.

**Result:** The app can display progress without a Hub login.

### UC-11 — Read a partner from an app

**Actors:** IWOMI app.  
**Goal:** Show a partner's identity, KYC status, document list, and the solutions sold to them.  
**Preconditions:** A valid API key. The partner exists in the Hub.

1. The app calls `GET /api/public/partners/{id}`, or `GET /api/public/partners?email=...` to find the partner by contact email.
2. The Hub returns the profile, KYC status and document links, and the related projects.

**Extensions**

- **1a.** An unknown id or email is not found.

**Result:** The app reads the central record. It does not approve KYC; that stays in UC-17.

### UC-12 — Handle a support ticket

**Actors:** Employee (tickets in their departments, or assigned to them). Admin and CTO (every ticket).  
**Goal:** Answer the reporter and move the ticket to a conclusion.  
**Preconditions:** The actor is logged in. A ticket exists (UC-03, UC-04, UC-09, or created by staff).

1. The actor opens **Tickets**, filters if needed, and opens a ticket. The thread includes internal notes.
2. The actor may change priority, department, assignee, and status (`OPEN`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`).
3. The actor writes a public reply, or marks the message as an internal note.
4. A public reply is visible to the customer (UC-08) and to an app (UC-10). An internal note is visible only to staff who can open the ticket.

**Extensions**

- **1a.** An employee does not see tickets outside their departments and not assigned to them. HR and Accountant have no ticket access.
- **2a.** Assigning someone while the status is OPEN sets the status to ASSIGNED.
- **2b.** Moving the ticket to another department notifies that department (UC-13).
- **3a.** Only an Admin can delete a ticket.

**Result:** The reporter has a public answer, or the ticket is resolved or closed. Internal discussion stays off the customer thread.

### UC-13 — Get notified of ticket activity

**Actors:** Employee, Admin, CTO.  
**Goal:** Learn that a department has a new ticket, a transfer, or a customer reply.  
**Preconditions:** The actor belongs to the notified department (or is Admin or CTO, who receive department notifications through the same stream). The browser stays on the staff app.

1. A ticket is created, transferred, or answered by the customer.
2. The Hub pushes an in-app notification over the live stream and shows the unread count on the bell.
3. The actor opens the bell, reads the item, and jumps to the ticket.

**Extensions**

- **2a.** Email, WhatsApp, and SMS are not sent.

**Result:** The department sees the item without refreshing. Marking it read clears it from the unread count.

### UC-14 — See the operations dashboard

**Actors:** Any staff role.  
**Goal:** See the current shape of the Hub at a glance.  
**Preconditions:** The actor is logged in as staff.

1. After login, or from **Dashboard**, the Hub shows the number of projects, partners, and employees, and how many tasks are in progress or blocked.
2. Admin, CTO, and Employee also see open tickets by department, and the latest tickets that actor is allowed to open. An employee linked to a person also sees their own planned, in-progress, and blocked tasks.
3. Admin, CTO, and Accountant also see the sales total.

**Extensions**

- **2a.** The open-ticket counts cover every department. The ticket list itself still hides tickets an employee cannot open (UC-12).

**Result:** The actor has a starting point for UC-12, UC-15, UC-19, and the rest of the staff work. HR sees the counts in step 1 only.

### UC-15 — Maintain projects and components

**Actors:** Admin, CTO (create and edit). Every staff role (read).  
**Goal:** Keep the catalog of solutions and the technical components of each one.  
**Preconditions:** The actor is logged in as staff.

1. The actor opens **Projects** and creates or edits a project: name, description, and status.
2. The actor adds components. Each component has a type (`API`, `FRONTOFFICE`, `BACKOFFICE`, `MOBILE`), name, repository, stack, version, and environment URLs.
3. The actor sets whether the project is shown on the public portal.

**Extensions**

- **3a.** Turning public visibility off removes the project from UC-01 and from the public help form. Staff still see it.

**Result:** The public catalog and the staff project list match what Admin or CTO last saved.

### UC-16 — Record a sale

**Actors:** Admin, Accountant (create and edit). CTO (read).  
**Goal:** Record that a solution was sold to a partner, with an amount.  
**Preconditions:** The project and the partner exist.

1. The actor opens the partner or the sales list and records the project, the partner, the date, the status, the amount, and the currency (XAF by default).
2. The Hub stores one sale per project and partner.

**Extensions**

- **1a.** HR, Employee, customers, and visitors cannot see amounts.

**Result:** The amount is included in the dashboard sales total and in the partner profile an app can read (UC-11).

### UC-17 — Review a partner's KYC

**Actors:** Admin, Accountant (edit and decide). CTO (read, including the AI review result).  
**Goal:** Decide whether a partner's identity file is acceptable.  
**Preconditions:** The partner exists. The file may be DRAFT or SUBMITTED. Documents are links (name, type, URL), not uploaded files.

1. The actor opens the partner's KYC file: legal name, registration number, address, representative, notes, and the document list.
2. The actor submits the file. The status becomes SUBMITTED.
3. Optionally, the actor runs **AI review**. The Hub checks completeness and consistency of the fields and of the document list, and returns a risk level (LOW, MEDIUM, HIGH), issues, missing items, and a recommendation (APPROVE, REQUEST_MORE_INFO, REJECT). It does not read document images and it does not change the status.
4. The actor sets the decision to APPROVED or REJECTED and writes notes. The Hub records the review time.

**Extensions**

- **3a.** With no AI provider, the review button explains the setup. The manual decision in step 4 still works.
- **4a.** Any status other than APPROVED or REJECTED is rejected.

**Result:** A person has decided. The AI recommendation is advice. Apps can read the resulting status (UC-11).

### UC-18 — Maintain the team

**Actors:** Admin, HR (create and edit). Every staff role (read).  
**Goal:** Keep departments and employees, so tickets and tasks can be assigned.  
**Preconditions:** The actor is logged in as staff.

1. The actor opens **Team** and creates or edits departments (name, email, description) and employees (name, email, job title, active flag, departments).
2. An employee can belong to several departments. Ticket visibility for that person follows those departments (UC-12).

**Result:** Routing, assignment, and notifications use the current team.

### UC-19 — Track delivery tasks

**Actors:** Admin, CTO (create, edit, delete). Employee (change the status of tasks assigned to them). HR (read).  
**Goal:** See who is doing what on a project.  
**Preconditions:** The actor is logged in. A project exists for new tasks.

1. Admin or CTO creates a task: title, project, assignee, status (`PLANNED`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `CANCELLED`), and the rest of the task fields.
2. The assignee, if an employee, updates the status of that task.
3. The task board shows the work by status.

**Extensions**

- **1a.** An employee cannot create or delete tasks, and cannot edit a task assigned to someone else.
- **1b.** Tasks can also be created from a delivery pack (UC-20).

**Result:** Project progress is visible to Admin, CTO, HR, and employees. Accountant has no task access.

### UC-20 — Generate a delivery pack

**Actors:** Admin, CTO, Employee.  
**Goal:** Turn a client's requirements or an API specification into stories, documentation, and tests.  
**Preconditions:** An AI provider is configured. The actor is logged in with access to **Accelerator**.

1. The actor pastes requirements or a specification (OpenAPI/JSON, SOAP/WSDL, or plain text), or loads the sample. The text is longer than a short fragment and at most 30,000 characters.
2. The Hub asks the model for one delivery pack in the language of the input: a summary, at most 6 user stories with acceptance criteria, API documentation (empty when the input has no API), at most 12 test cases, and open risks and questions. Anything not in the source is listed as a question, not invented.
3. The actor reads the stories, the API documentation, and the test cases.
4. The actor executes the tests and sets each case to Not run, Pass, Fail, or Blocked. The pass rate updates from the cases that have been run.
5. The actor downloads the pack and the test report as Markdown.

**Extensions**

- **5a.** Admin or CTO chooses a project and creates one task per user story (UC-19). An employee can generate the pack but cannot create those tasks.
- **2a.** A malformed model answer asks the actor to try again, with a shorter specification if the answer was cut.

**Result:** The team has a reviewable pack. Creating tasks is a separate, explicit step.

### UC-21 — Ask the staff assistant

**Actors:** Any staff role.  
**Goal:** Ask about live Hub data the role may see, or get help with daily work (a reply to a partner, a user story, a test case, SQL, Spring Boot, Flutter, Odoo, mobile money, core banking).  
**Preconditions:** An AI provider is configured. The actor is logged in as staff.

1. The actor opens **Ask AI**.
2. The actor types a question. The Hub streams the reply. **Stop** cancels it.
3. The prompt contains the staff knowledge file plus a snapshot of the data that role may see: all data for Admin and CTO; people, tasks, and projects for HR; partners, KYC, and sales for Accountant; projects, own tickets, and tasks for Employee.
4. The actor may continue the conversation.

**Extensions**

- **3a.** The assistant has no channel to data outside that snapshot, so it cannot be used to bypass UC-12, UC-16, or UC-17.
- **2a.** With no provider, the Hub shows the setup message.

**Result:** The answer stays inside the actor's permissions.

### UC-22 — Administer the Hub

**Actors:** Admin (users, settings, routing, connected apps). CTO (settings, routing, connected apps; not users).  
**Goal:** Control who can sign in, where tickets go, and which apps may call the Hub.  
**Preconditions:** The actor is logged in as Admin or CTO.

1. **Users (Admin only).** The actor creates or edits an account: username, password, full name, email, role, linked employee or partner, and active flag. Deactivating an account blocks UC-06 for that person.
2. **Routing rules (Admin, CTO).** The actor adds rules with an optional project, component type, and category, a target department, and a priority. Empty fields mean "any". On the next ticket (UC-03, UC-04, UC-09, UC-12), the most specific matching rule wins (project, then component type, then category); ties go to the higher priority. No match uses the default department from Settings.
3. **Connected apps (Admin, CTO).** The actor creates an app name, an API key, an optional default project, and an active flag. That key is what UC-09, UC-10, and UC-11 require.

**Extensions**

- **1a.** Self-registration (UC-05) cannot create a staff role. Only this use case can.
- **3a.** An inactive key is rejected on the next app call.

**Result:** Access, routing, and app integration match the administration just saved.

### UC-23 — Use a stored opportunity

**Actors:** CTO, HR, Accountant, Employee. Admin maintains the catalog and does not receive personal advice. Customers and visitors have no access.  
**Goal:** See concrete ways to use a salary or company cash, each one backed by a page the actor can open.  
**Preconditions:** The actor is logged in as internal staff. Opportunities are stored with an https source.

1. The actor opens **Opportunities** and filters Personal, Company, or both.
2. Each card shows the idea and a source link. The actor opens the link to read the bank or BEAC page itself.
3. The actor either asks about one card, or enters a monthly salary in XAF and a question (how to save, spend, or put money to work).
4. The Hub answers only from the stored, active opportunities. Every recommendation names its source URL. It does not invent a rate, a minimum, or a product.

**Extensions**

- **4a.** With no AI provider, the catalog and the links still work. The advice step shows how to configure the provider.
- **1a.** Admin creates, edits, or retires opportunities. An inactive row disappears from the staff list. Advice is refused for Admin.

**Result:** The actor has a sourced suggestion, not an advertisement. The decision and the current figures stay on the source page.

## Known limits

- Without a configured AI provider the assistants show a setup message instead of answering. Opportunity links still open.
- Opportunity advice is not a regulated recommendation. Rates and conditions are whatever the source page says.
- Login tokens last 12 hours; there is no email verification or password reset yet.
- Notifications are in-app and live (SSE). Email, WhatsApp and SMS sending are not implemented.
- KYC documents are stored as links, not uploaded files; AI KYC review does not read images.
- Per-IP rate limits use the connection address; behind a reverse proxy, configure forwarded headers.
- List endpoints filter in memory: fine for thousands of rows, add DB queries/paging beyond that.
