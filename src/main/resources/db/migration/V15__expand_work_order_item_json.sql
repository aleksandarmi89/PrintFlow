ALTER TABLE work_order_items
    MODIFY COLUMN breakdown_json LONGTEXT NOT NULL,
    MODIFY COLUMN attributes_json LONGTEXT NULL;
