ALTER TABLE work_order_items
    MODIFY attributes_json LONGTEXT NULL,
    MODIFY breakdown_json LONGTEXT NOT NULL;
