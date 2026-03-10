-- Work orders
CREATE INDEX idx_work_orders_tenant_created_at ON work_orders (tenant_id, created_at);
CREATE INDEX idx_work_orders_tenant_status ON work_orders (tenant_id, status);
CREATE INDEX idx_work_orders_tenant_assigned_to ON work_orders (tenant_id, assigned_to);
CREATE INDEX idx_work_orders_tenant_deadline ON work_orders (tenant_id, deadline);

-- Tasks
CREATE INDEX idx_tasks_tenant_created_at ON tasks (tenant_id, created_at);
CREATE INDEX idx_tasks_tenant_status ON tasks (tenant_id, status);
CREATE INDEX idx_tasks_tenant_assigned_to ON tasks (tenant_id, assigned_to_id);
CREATE INDEX idx_tasks_tenant_work_order ON tasks (tenant_id, work_order_id);

-- Clients
CREATE INDEX idx_clients_tenant_active_created_at ON clients (tenant_id, active, created_at);

-- Attachments
CREATE INDEX idx_attachments_tenant_work_order_active ON attachments (tenant_id, work_order_id, active);
CREATE INDEX idx_attachments_tenant_task_active ON attachments (tenant_id, task_id, active);
CREATE INDEX idx_attachments_tenant_type_active ON attachments (tenant_id, attachment_type, active);

-- Audit logs
CREATE INDEX idx_audit_logs_tenant_created_at ON audit_logs (tenant_id, created_at);
CREATE INDEX idx_audit_logs_tenant_entity ON audit_logs (tenant_id, entity_type, entity_id);

-- Users
CREATE INDEX idx_users_tenant_role_active ON users (tenant_id, role, active);
