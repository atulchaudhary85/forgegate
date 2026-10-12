CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(40) NOT NULL UNIQUE CHECK (name IN ('EMPLOYEE','MANAGER','SECURITY_REVIEWER','ADMIN'))
);
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  department VARCHAR(100) NOT NULL,
  manager_id BIGINT REFERENCES users(id),
  role_id BIGINT NOT NULL REFERENCES roles(id),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_manager ON users(manager_id);
CREATE TABLE resources (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(160) NOT NULL UNIQUE,
  type VARCHAR(40) NOT NULL CHECK (type IN ('GITHUB_REPO','DATABASE','DASHBOARD','CLOUD','OTHER')),
  sensitivity VARCHAR(16) NOT NULL CHECK (sensitivity IN ('LOW','MEDIUM','HIGH')),
  owner_department VARCHAR(100) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE policies (
  id BIGSERIAL PRIMARY KEY,
  resource_id BIGINT NOT NULL REFERENCES resources(id),
  allowed_department VARCHAR(100),
  max_duration_days INTEGER NOT NULL CHECK (max_duration_days IN (1,7,30)),
  requires_security_approval BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_policies_resource_active ON policies(resource_id, is_active);
CREATE TABLE access_requests (
  id BIGSERIAL PRIMARY KEY,
  requester_id BIGINT NOT NULL REFERENCES users(id),
  resource_id BIGINT NOT NULL REFERENCES resources(id),
  reason TEXT NOT NULL CHECK (length(trim(reason)) >= 8),
  duration_days INTEGER NOT NULL CHECK (duration_days IN (1,7,30)),
  status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING_MANAGER','PENDING_SECURITY','APPROVED','ACTIVE','REJECTED','EXPIRED','REVOKED')),
  idempotency_key VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(requester_id, idempotency_key)
);
CREATE INDEX idx_requests_status_created ON access_requests(status, created_at DESC);
CREATE INDEX idx_requests_requester_created ON access_requests(requester_id, created_at DESC);
CREATE INDEX idx_requests_resource_status ON access_requests(resource_id, status);
CREATE TABLE approvals (
  id BIGSERIAL PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES access_requests(id),
  approver_id BIGINT NOT NULL REFERENCES users(id),
  stage VARCHAR(16) NOT NULL CHECK (stage IN ('MANAGER','SECURITY')),
  decision VARCHAR(16) NOT NULL CHECK (decision IN ('PENDING','APPROVED','REJECTED')),
  comment TEXT,
  decided_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(request_id, stage)
);
CREATE TABLE access_grants (
  id BIGSERIAL PRIMARY KEY,
  request_id BIGINT NOT NULL UNIQUE REFERENCES access_requests(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  resource_id BIGINT NOT NULL REFERENCES resources(id),
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
  revoked_at TIMESTAMPTZ,
  CHECK (expires_at > granted_at)
);
CREATE UNIQUE INDEX one_active_grant_per_user_resource ON access_grants(user_id, resource_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_grants_expires_at ON access_grants(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_grants_user_status ON access_grants(user_id, status);
CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT REFERENCES users(id),
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id BIGINT,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs(action, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  type VARCHAR(80) NOT NULL,
  message TEXT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC);
