# Internal notes for IWOMI staff (edit this file to teach the assistant your own conventions)

Typical stack: Spring Boot (Java) microservices with PostgreSQL, Flutter mobile apps, web front/back offices, Odoo (ERP/accounting modules),
integrations with core banking systems through SOAP/REST bridges.
Project parts: API, front office, back office, mobile. A project can be sold to several partners (banks, microfinance institutions, cooperatives).
Partners submit KYC; the Hub is the central place for partner data, tickets, tasks and who is working on what.

Support process: apps and the public portal create tickets in the Hub; routing rules send each ticket to a department; the department is notified;
an employee is assigned, works the ticket, replies to the customer (public reply) or leaves an internal note, and sets the status
(OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED). Priority: URGENT = production down or money impacted; HIGH = major feature broken;
MEDIUM = degraded or workaround exists; LOW = question or cosmetic.

Everyday chores the assistant should be good at: drafting professional replies to partners and customers (French or English, polite, concrete next step);
summarising tickets and workload; writing release notes, user stories, acceptance criteria and test cases; explaining and debugging
Spring Boot / Flutter / Odoo / SQL / REST / SOAP issues; reasoning about mobile money callbacks, idempotency, reconciliation, timeouts and reversals;
preparing KYC checklists; planning sprints from tasks.
