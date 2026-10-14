-- ============================================================================
-- VSMS - Vehicle Service Management System: table definitions (MySQL 8)
--
-- Generated from the JPA entities with Hibernate and verified by running the
-- application with  spring.jpa.hibernate.ddl-auto=validate  against this schema.
-- You normally do NOT need this file: the application creates the same tables
-- automatically on first start (VSMS_DDL_AUTO=update). Use it if you prefer to
-- create the tables yourself:
--
--   mysql -u vsms_user -p vsms_db -e "source database/02-schema.sql"
--   (name the database on the command line; then start the application with  VSMS_DDL_AUTO=validate )
--
-- Money columns are DECIMAL(12,2); status/type columns are VARCHAR with CHECK
-- constraints; every relationship has a named foreign key.
-- ============================================================================

CREATE TABLE appointments (
    id bigint not null auto_increment,
    appointment_date date not null,
    appointment_time time(6) not null,
    created_at datetime(6) not null,
    customer_id bigint not null,
    service_item_id bigint not null,
    vehicle_id bigint not null,
    status varchar(20) not null check (status in ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    notes varchar(500),
    primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customers (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    phone varchar(20) not null,
    name varchar(100) not null,
    email varchar(120),
    address varchar(255),
    primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoices (
    id bigint not null auto_increment,
    amount_paid decimal(12,2) not null,
    grand_total decimal(12,2) not null,
    labor_charge decimal(12,2) not null,
    parts_total decimal(12,2) not null,
    subtotal decimal(12,2) not null,
    tax_amount decimal(12,2) not null,
    tax_percent decimal(5,2) not null,
    created_at datetime(6) not null,
    issued_at datetime(6) not null,
    service_job_id bigint not null,
    version bigint,
    payment_status varchar(20) not null check (payment_status in ('UNPAID','PARTIALLY_PAID','PAID')),
    issued_by varchar(50),
    primary key (id),
    check (grand_total >= 0 AND amount_paid >= 0 AND amount_paid <= grand_total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mechanics (
    id bigint not null auto_increment,
    active bit not null,
    created_at datetime(6) not null,
    phone varchar(20),
    name varchar(100) not null,
    specialization varchar(100) not null,
    primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE part_usages (
    id bigint not null auto_increment,
    quantity integer not null,
    stock_deducted bit not null,
    unit_price_at_use decimal(12,2) not null,
    created_at datetime(6) not null,
    service_job_id bigint not null,
    spare_part_id bigint not null,
    used_at datetime(6) not null,
    primary key (id),
    check (quantity > 0 AND unit_price_at_use >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id bigint not null auto_increment,
    amount decimal(12,2) not null,
    created_at datetime(6) not null,
    invoice_id bigint not null,
    paid_at datetime(6) not null,
    reversed_at datetime(6),
    method varchar(20) not null check (method in ('CASH','UPI','CARD','OTHER')),
    status varchar(20) not null check (status in ('SUCCESSFUL','REVERSED')),
    recorded_by varchar(50),
    reference varchar(100),
    reversal_reason varchar(200),
    primary key (id),
    check (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_catalog_items (
    id bigint not null auto_increment,
    active bit not null,
    standard_price decimal(12,2) not null,
    created_at datetime(6) not null,
    name varchar(100) not null,
    description varchar(255),
    primary key (id),
    check (standard_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_jobs (
    id bigint not null auto_increment,
    labor_charge decimal(12,2) not null,
    appointment_id bigint not null,
    completed_at datetime(6),
    created_at datetime(6) not null,
    delivered_at datetime(6),
    mechanic_id bigint,
    started_at datetime(6),
    version bigint,
    status varchar(20) not null check (status in ('CREATED','ASSIGNED','IN_PROGRESS','ON_HOLD','COMPLETED','INVOICED','DELIVERED','CANCELLED')),
    customer_complaint varchar(1000) not null,
    diagnosis varchar(2000),
    repair_notes varchar(2000),
    primary key (id),
    check (labor_charge >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_tasks (
    id bigint not null auto_increment,
    completed bit not null,
    completed_at datetime(6),
    created_at datetime(6) not null,
    service_job_id bigint not null,
    description varchar(200) not null,
    primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE spare_parts (
    id bigint not null auto_increment,
    reorder_level integer not null,
    stock_quantity integer not null,
    unit_price decimal(12,2) not null,
    created_at datetime(6) not null,
    version bigint,
    sku varchar(40) not null,
    name varchar(120) not null,
    primary key (id),
    check (stock_quantity >= 0 AND unit_price >= 0 AND reorder_level >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_accounts (
    id bigint not null auto_increment,
    enabled bit not null,
    created_at datetime(6) not null,
    mechanic_id bigint,
    role varchar(20) not null check (role in ('ADMIN','SERVICE_ADVISOR','MECHANIC')),
    username varchar(50) not null,
    full_name varchar(100) not null,
    password_hash varchar(100) not null,
    primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vehicles (
    id bigint not null auto_increment,
    manufacturing_year integer not null,
    created_at datetime(6) not null,
    customer_id bigint not null,
    fuel_type varchar(20) not null check (fuel_type in ('PETROL','DIESEL','CNG','ELECTRIC','HYBRID')),
    registration_number varchar(20) not null,
    manufacturer varchar(60) not null,
    model varchar(60) not null,
    primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- indexes
CREATE INDEX idx_appt_service on appointments (service_item_id);
CREATE INDEX idx_appt_slot on appointments (appointment_date, appointment_time);
CREATE INDEX idx_appt_status on appointments (status);
CREATE INDEX idx_appt_vehicle on appointments (vehicle_id);
CREATE INDEX idx_customer_name on customers (name);
CREATE INDEX idx_customer_phone on customers (phone);
CREATE INDEX idx_invoice_issued on invoices (issued_at);
CREATE INDEX idx_invoice_status on invoices (payment_status);
CREATE INDEX idx_job_mechanic on service_jobs (mechanic_id);
CREATE INDEX idx_job_status on service_jobs (status);
CREATE INDEX idx_mechanic_active on mechanics (active);
CREATE INDEX idx_payment_invoice on payments (invoice_id);
CREATE INDEX idx_payment_paid_at on payments (paid_at);
CREATE INDEX idx_payment_status on payments (status);
CREATE INDEX idx_usage_job on part_usages (service_job_id);
CREATE INDEX idx_usage_part on part_usages (spare_part_id);
CREATE INDEX idx_vehicle_customer on vehicles (customer_id);

-- ------------------------------------------------------- unique constraints
ALTER TABLE invoices ADD CONSTRAINT uk_invoice_job UNIQUE (service_job_id);
ALTER TABLE service_catalog_items ADD CONSTRAINT uk_catalog_name UNIQUE (name);
ALTER TABLE service_jobs ADD CONSTRAINT uk_job_appointment UNIQUE (appointment_id);
ALTER TABLE spare_parts ADD CONSTRAINT uk_part_sku UNIQUE (sku);
ALTER TABLE user_accounts ADD CONSTRAINT uk_user_mechanic UNIQUE (mechanic_id);
ALTER TABLE user_accounts ADD CONSTRAINT uk_user_username UNIQUE (username);
ALTER TABLE vehicles ADD CONSTRAINT uk_vehicle_registration UNIQUE (registration_number);

-- ------------------------------------------------------------ foreign keys
ALTER TABLE appointments ADD CONSTRAINT fk_appt_customer FOREIGN KEY (customer_id) REFERENCES customers (id);
ALTER TABLE appointments ADD CONSTRAINT fk_appt_service FOREIGN KEY (service_item_id) REFERENCES service_catalog_items (id);
ALTER TABLE appointments ADD CONSTRAINT fk_appt_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id);
ALTER TABLE invoices ADD CONSTRAINT fk_invoice_job FOREIGN KEY (service_job_id) REFERENCES service_jobs (id);
ALTER TABLE part_usages ADD CONSTRAINT fk_usage_job FOREIGN KEY (service_job_id) REFERENCES service_jobs (id);
ALTER TABLE part_usages ADD CONSTRAINT fk_usage_part FOREIGN KEY (spare_part_id) REFERENCES spare_parts (id);
ALTER TABLE payments ADD CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id);
ALTER TABLE service_jobs ADD CONSTRAINT fk_job_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id);
ALTER TABLE service_jobs ADD CONSTRAINT fk_job_mechanic FOREIGN KEY (mechanic_id) REFERENCES mechanics (id);
ALTER TABLE service_tasks ADD CONSTRAINT fk_task_job FOREIGN KEY (service_job_id) REFERENCES service_jobs (id);
ALTER TABLE user_accounts ADD CONSTRAINT fk_user_mechanic FOREIGN KEY (mechanic_id) REFERENCES mechanics (id);
ALTER TABLE vehicles ADD CONSTRAINT fk_vehicle_customer FOREIGN KEY (customer_id) REFERENCES customers (id);
