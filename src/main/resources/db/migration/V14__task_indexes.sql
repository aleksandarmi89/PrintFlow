-- Indexes to speed up worker task list filtering/sorting
CREATE INDEX idx_tasks_assigned_to_id ON tasks (assigned_to_id);
CREATE INDEX idx_tasks_company_id ON tasks (company_id);
CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_priority ON tasks (priority);
CREATE INDEX idx_tasks_due_date ON tasks (due_date);
