-- Users and roles, public portal, sales amounts, ticket visibility

CREATE TABLE app_user (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(60) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(200),
  email VARCHAR(200),
  role VARCHAR(30) NOT NULL,
  employee_id BIGINT REFERENCES employee(id) ON DELETE SET NULL,
  partner_id BIGINT REFERENCES partner(id) ON DELETE SET NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE project ADD COLUMN public_visible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE project_partner ADD COLUMN amount NUMERIC(16,2);
ALTER TABLE project_partner ADD COLUMN currency VARCHAR(10) NOT NULL DEFAULT 'XAF';

ALTER TABLE ticket ADD COLUMN requester_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL;
ALTER TABLE ticket ADD COLUMN tracking_code VARCHAR(20);

ALTER TABLE ticket_message ADD COLUMN is_internal BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ticket_message ADD COLUMN author_role VARCHAR(30);

CREATE INDEX idx_ticket_requester ON ticket(requester_user_id);

-- Demo data: amounts on existing sales, and one project hidden from the public portal
UPDATE project_partner pp SET amount = CASE (SELECT name FROM project WHERE id = pp.project_id)
    WHEN 'Loan 360' THEN 12000000
    WHEN 'IWOMI Pay' THEN 8000000
    WHEN 'Agency Banking' THEN 15000000
    WHEN 'BKWIKI' THEN 3500000
    ELSE 0 END
WHERE amount IS NULL;

UPDATE project SET public_visible = FALSE WHERE name = 'Agency Banking';
