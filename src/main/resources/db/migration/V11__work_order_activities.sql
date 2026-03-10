CREATE TABLE IF NOT EXISTS work_order_activities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    work_order_id BIGINT NOT NULL,
    type VARCHAR(60) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_by_user_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_work_order_activities_tenant FOREIGN KEY (tenant_id) REFERENCES companies(id),
    CONSTRAINT fk_work_order_activities_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id)
);

CREATE INDEX idx_work_order_activities_tenant ON work_order_activities(tenant_id);
CREATE INDEX idx_work_order_activities_order ON work_order_activities(work_order_id);
