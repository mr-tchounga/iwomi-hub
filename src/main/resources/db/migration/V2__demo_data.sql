-- Fictional demo data (no real customers or partners)
INSERT INTO department (name, email, description) VALUES
 ('Backend', 'backend@example.com', 'Spring Boot services and APIs'),
 ('Mobile', 'mobile@example.com', 'Flutter mobile applications'),
 ('ERP', 'erp@example.com', 'Odoo modules and accounting'),
 ('Support', 'support@example.com', 'First-line support and onboarding');

INSERT INTO employee (full_name, email, job_title) VALUES
 ('Alice Nguema', 'alice@example.com', 'Senior Backend Developer'),
 ('Brice Tchoua', 'brice@example.com', 'Backend and ERP Developer'),
 ('Carine Fotso', 'carine@example.com', 'Mobile Developer'),
 ('David Essomba', 'david@example.com', 'Support Lead'),
 ('Estelle Kamga', 'estelle@example.com', 'Full-stack Developer');

INSERT INTO employee_department (employee_id, department_id)
SELECT e.id, d.id FROM employee e JOIN department d
  ON (e.email, d.name) IN (
    ('alice@example.com', 'Backend'), ('brice@example.com', 'Backend'), ('brice@example.com', 'ERP'),
    ('carine@example.com', 'Mobile'), ('david@example.com', 'Support'),
    ('estelle@example.com', 'Mobile'), ('estelle@example.com', 'Backend'));

INSERT INTO project (name, description, status) VALUES
 ('Loan 360', 'Loan collection and tracking for banks and financial institutions', 'ACTIVE'),
 ('IWOMI Pay', 'Online payment gateway for purchasing packages and services', 'ACTIVE'),
 ('BKWIKI', 'Knowledge and regulatory information about banks in Africa', 'ACTIVE'),
 ('IWAKAP', 'Web platform for fundraising campaigns', 'MAINTENANCE'),
 ('Agency Banking', 'Agency banking microservice and agent applications', 'ACTIVE');

INSERT INTO project_component (project_id, type, name, repo_url, stack, version, environment_urls)
SELECT p.id, c.type, c.name, c.repo, c.stack, c.version, c.env
FROM project p JOIN (VALUES
 ('Loan 360', 'API', 'Loan 360 API', 'https://git.example.com/loan360-api', 'Spring Boot 3, PostgreSQL', '2.4.0', 'prod: https://api.loan360.example.com'),
 ('Loan 360', 'BACKOFFICE', 'Loan 360 Back Office', 'https://git.example.com/loan360-bo', 'Web admin', '2.3.1', 'prod: https://bo.loan360.example.com'),
 ('Loan 360', 'MOBILE', 'Loan 360 Mobile', 'https://git.example.com/loan360-mobile', 'Flutter', '1.8.0', 'stores: Android and iOS'),
 ('IWOMI Pay', 'API', 'IWOMI Pay API', 'https://git.example.com/pay-api', 'Spring Boot 3, PostgreSQL', '1.6.2', 'prod: https://api.pay.example.com'),
 ('IWOMI Pay', 'FRONTOFFICE', 'IWOMI Pay Checkout', 'https://git.example.com/pay-checkout', 'Web checkout widget', '1.6.0', 'prod: https://checkout.pay.example.com'),
 ('BKWIKI', 'API', 'BKWIKI API', 'https://git.example.com/bkwiki-api', 'Spring Boot 3', '3.0.1', 'prod: https://api.bkwiki.example.com'),
 ('BKWIKI', 'FRONTOFFICE', 'BKWIKI Portal', 'https://git.example.com/bkwiki-web', 'Web', '3.0.0', 'prod: https://bkwiki.example.com'),
 ('IWAKAP', 'FRONTOFFICE', 'IWAKAP Web', 'https://git.example.com/iwakap-web', 'Web', '1.2.0', 'prod: https://iwakap.example.com'),
 ('Agency Banking', 'API', 'Agency Banking MS', 'https://git.example.com/agency-ms', 'Spring Boot 3, PostgreSQL', '0.9.0', 'staging: https://agency-stg.example.com'),
 ('Agency Banking', 'MOBILE', 'Agent App', 'https://git.example.com/agent-app', 'Flutter', '0.9.0', 'stores: Android')
) AS c(project, type, name, repo, stack, version, env) ON c.project = p.name;

INSERT INTO partner (name, contact_email, country) VALUES
 ('Demo Microfinance Alpha', 'contact@alpha.example.com', 'Cameroon'),
 ('Demo Bank Beta', 'contact@beta.example.com', 'Cameroon'),
 ('Demo Cooperative Gamma', 'contact@gamma.example.com', 'Cameroon');

INSERT INTO project_partner (project_id, partner_id, sold_at, status)
SELECT p.id, pa.id, s.sold::date, 'ACTIVE'
FROM (VALUES
 ('Loan 360', 'Demo Microfinance Alpha', '2025-03-10'),
 ('Loan 360', 'Demo Bank Beta', '2025-09-01'),
 ('IWOMI Pay', 'Demo Bank Beta', '2025-11-20'),
 ('Agency Banking', 'Demo Microfinance Alpha', '2026-02-15'),
 ('Agency Banking', 'Demo Cooperative Gamma', '2026-05-05'),
 ('BKWIKI', 'Demo Bank Beta', '2024-12-01')
) AS s(project, partner, sold)
JOIN project p ON p.name = s.project JOIN partner pa ON pa.name = s.partner;

INSERT INTO partner_kyc (partner_id, legal_name, registration_number, address, representative_name, status, submitted_at, reviewed_at)
SELECT pa.id, k.legal, k.reg, k.addr, k.rep, k.status,
       CASE WHEN k.status = 'DRAFT' THEN NULL ELSE now() - interval '30 days' END,
       CASE WHEN k.status = 'APPROVED' THEN now() - interval '25 days' ELSE NULL END
FROM (VALUES
 ('Demo Microfinance Alpha', 'Alpha Microfinance SA', 'RC/DLA/2012/B/0001', 'Akwa, Douala', 'Jean Mballa', 'APPROVED'),
 ('Demo Bank Beta', 'Beta Bank PLC', 'RC/YAO/2008/B/0042', 'Bastos, Yaounde', 'Marie Ndjock', 'SUBMITTED'),
 ('Demo Cooperative Gamma', 'Gamma Cooperative', 'RC/BAF/2019/B/0107', 'Bafoussam', 'Paul Tagne', 'DRAFT')
) AS k(partner, legal, reg, addr, rep, status) JOIN partner pa ON pa.name = k.partner;

INSERT INTO kyc_document (kyc_id, doc_type, file_name, url)
SELECT kc.id, d.doc_type, d.file_name, d.url
FROM (VALUES
 ('Demo Microfinance Alpha', 'Registration certificate', 'alpha-rc.pdf', 'https://files.example.com/alpha-rc.pdf'),
 ('Demo Microfinance Alpha', 'Representative ID', 'alpha-id.pdf', 'https://files.example.com/alpha-id.pdf'),
 ('Demo Bank Beta', 'Registration certificate', 'beta-rc.pdf', 'https://files.example.com/beta-rc.pdf')
) AS d(partner, doc_type, file_name, url)
JOIN partner pa ON pa.name = d.partner JOIN partner_kyc kc ON kc.partner_id = pa.id;

INSERT INTO ticket_routing_rule (project_id, component_type, category, department_id, priority) VALUES
 ((SELECT id FROM project WHERE name = 'Loan 360'), 'MOBILE', NULL, (SELECT id FROM department WHERE name = 'Mobile'), 10),
 (NULL, 'API', NULL, (SELECT id FROM department WHERE name = 'Backend'), 5),
 (NULL, 'MOBILE', NULL, (SELECT id FROM department WHERE name = 'Mobile'), 5),
 (NULL, NULL, 'ACCOUNTING', (SELECT id FROM department WHERE name = 'ERP'), 5),
 (NULL, NULL, 'ONBOARDING', (SELECT id FROM department WHERE name = 'Support'), 5);

INSERT INTO setting (setting_key, setting_value)
VALUES ('default_ticket_department', (SELECT id::text FROM department WHERE name = 'Support'));

INSERT INTO app_client (name, api_key, project_id) VALUES
 ('Loan 360 Mobile', 'demo-loan360-key', (SELECT id FROM project WHERE name = 'Loan 360')),
 ('IWOMI Pay Checkout', 'demo-pay-key', (SELECT id FROM project WHERE name = 'IWOMI Pay'));

INSERT INTO ticket (reference, project_id, component_id, partner_id, reporter_name, reporter_contact, category, subject, description, priority, status, department_id, source) VALUES
 ('TCK-00001', (SELECT id FROM project WHERE name = 'Loan 360'), (SELECT id FROM project_component WHERE name = 'Loan 360 Mobile'),
  (SELECT id FROM partner WHERE name = 'Demo Microfinance Alpha'), 'Agent Mireille', '+237600000001', 'BUG',
  'App crashes when opening loan schedule', 'Crash on Android 12 when tapping Repayment schedule.', 'HIGH', 'OPEN',
  (SELECT id FROM department WHERE name = 'Mobile'), 'Loan 360 Mobile'),
 ('TCK-00002', (SELECT id FROM project WHERE name = 'IWOMI Pay'), (SELECT id FROM project_component WHERE name = 'IWOMI Pay API'),
  (SELECT id FROM partner WHERE name = 'Demo Bank Beta'), 'Operations Beta', 'ops@beta.example.com', 'PAYMENT',
  'Callback not received after successful payment', 'Webhook for transaction T-1042 never arrived.', 'URGENT', 'IN_PROGRESS',
  (SELECT id FROM department WHERE name = 'Backend'), 'IWOMI Pay Checkout'),
 ('TCK-00003', NULL, NULL, (SELECT id FROM partner WHERE name = 'Demo Cooperative Gamma'), 'Paul Tagne', 'contact@gamma.example.com', 'ONBOARDING',
  'Need help completing KYC submission', 'We are unsure which documents are required.', 'MEDIUM', 'OPEN',
  (SELECT id FROM department WHERE name = 'Support'), 'Manual');

UPDATE ticket SET assignee_id = (SELECT id FROM employee WHERE email = 'alice@example.com') WHERE reference = 'TCK-00002';

INSERT INTO ticket_message (ticket_id, author, body)
SELECT id, 'Alice Nguema', 'Looking into the webhook retry queue now.' FROM ticket WHERE reference = 'TCK-00002';

INSERT INTO task (title, description, project_id, assignee_id, status, start_date, due_date, completed_at)
SELECT t.title, t.descr, p.id, e.id, t.status, CURRENT_DATE + t.s, CURRENT_DATE + t.d,
       CASE WHEN t.status = 'DONE' THEN now() + make_interval(days => t.d) ELSE NULL END
FROM (VALUES
 ('Migrate Loan 360 API to Spring Boot 3', 'Upgrade dependencies and fix breaking changes', 'Loan 360', 'alice@example.com', 'DONE', -30, -12),
 ('Fix repayment schedule crash', 'Investigate TCK-00001', 'Loan 360', 'carine@example.com', 'IN_PROGRESS', -2, 3),
 ('Webhook retry queue for IWOMI Pay', 'Retry failed callbacks with backoff', 'IWOMI Pay', 'alice@example.com', 'IN_PROGRESS', -4, 2),
 ('Odoo accounting module: bank reconciliation', 'Custom reconciliation view', 'Agency Banking', 'brice@example.com', 'BLOCKED', -6, 5),
 ('Agent app offline mode', 'Queue transactions when offline', 'Agency Banking', 'estelle@example.com', 'PLANNED', 5, 20),
 ('BKWIKI regulatory content import', 'Import new regulation documents', 'BKWIKI', 'estelle@example.com', 'PLANNED', 7, 25),
 ('Partner onboarding checklist', 'Document KYC requirements for partners', 'Loan 360', 'david@example.com', 'DONE', -20, -9),
 ('IWAKAP security review', 'Dependency and access audit', 'IWAKAP', 'brice@example.com', 'PLANNED', 10, 30)
) AS t(title, descr, project, email, status, s, d)
JOIN project p ON p.name = t.project JOIN employee e ON e.email = t.email;
