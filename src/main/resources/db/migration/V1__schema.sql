CREATE TABLE department (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  email VARCHAR(200),
  description VARCHAR(500)
);

CREATE TABLE employee (
  id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR(200) NOT NULL,
  email VARCHAR(200) UNIQUE,
  job_title VARCHAR(150),
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE employee_department (
  employee_id BIGINT NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
  department_id BIGINT NOT NULL REFERENCES department(id) ON DELETE CASCADE,
  PRIMARY KEY (employee_id, department_id)
);

CREATE TABLE project (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(200) NOT NULL UNIQUE,
  description TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project_component (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  type VARCHAR(30) NOT NULL,
  name VARCHAR(200),
  repo_url VARCHAR(500),
  stack VARCHAR(300),
  version VARCHAR(50),
  environment_urls TEXT
);

CREATE TABLE partner (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(200) NOT NULL UNIQUE,
  contact_email VARCHAR(200),
  country VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project_partner (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  partner_id BIGINT NOT NULL REFERENCES partner(id) ON DELETE CASCADE,
  sold_at DATE,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE (project_id, partner_id)
);

CREATE TABLE partner_kyc (
  id BIGSERIAL PRIMARY KEY,
  partner_id BIGINT NOT NULL UNIQUE REFERENCES partner(id) ON DELETE CASCADE,
  legal_name VARCHAR(250),
  registration_number VARCHAR(100),
  address VARCHAR(400),
  representative_name VARCHAR(200),
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  notes TEXT,
  submitted_at TIMESTAMPTZ,
  reviewed_at TIMESTAMPTZ
);

CREATE TABLE kyc_document (
  id BIGSERIAL PRIMARY KEY,
  kyc_id BIGINT NOT NULL REFERENCES partner_kyc(id) ON DELETE CASCADE,
  doc_type VARCHAR(100),
  file_name VARCHAR(250),
  url VARCHAR(500)
);

CREATE TABLE ticket (
  id BIGSERIAL PRIMARY KEY,
  reference VARCHAR(20) UNIQUE,
  project_id BIGINT REFERENCES project(id) ON DELETE SET NULL,
  component_id BIGINT REFERENCES project_component(id) ON DELETE SET NULL,
  partner_id BIGINT REFERENCES partner(id) ON DELETE SET NULL,
  reporter_name VARCHAR(200),
  reporter_contact VARCHAR(200),
  category VARCHAR(100),
  subject VARCHAR(300) NOT NULL,
  description TEXT,
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  department_id BIGINT REFERENCES department(id) ON DELETE SET NULL,
  assignee_id BIGINT REFERENCES employee(id) ON DELETE SET NULL,
  source VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ticket_message (
  id BIGSERIAL PRIMARY KEY,
  ticket_id BIGINT NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
  author VARCHAR(200),
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ticket_routing_rule (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
  component_type VARCHAR(30),
  category VARCHAR(100),
  department_id BIGINT NOT NULL REFERENCES department(id) ON DELETE CASCADE,
  priority INT NOT NULL DEFAULT 0
);

CREATE TABLE task (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(300) NOT NULL,
  description TEXT,
  project_id BIGINT REFERENCES project(id) ON DELETE SET NULL,
  component_id BIGINT REFERENCES project_component(id) ON DELETE SET NULL,
  assignee_id BIGINT REFERENCES employee(id) ON DELETE SET NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
  start_date DATE,
  due_date DATE,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification (
  id BIGSERIAL PRIMARY KEY,
  department_id BIGINT NOT NULL REFERENCES department(id) ON DELETE CASCADE,
  title VARCHAR(300) NOT NULL,
  body TEXT,
  ref_type VARCHAR(50),
  ref_id BIGINT,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_client (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  api_key VARCHAR(100) NOT NULL UNIQUE,
  project_id BIGINT REFERENCES project(id) ON DELETE SET NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE setting (
  setting_key VARCHAR(100) PRIMARY KEY,
  setting_value VARCHAR(500)
);

CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_department ON ticket(department_id);
CREATE INDEX idx_task_assignee ON task(assignee_id);
CREATE INDEX idx_notification_department ON notification(department_id);
