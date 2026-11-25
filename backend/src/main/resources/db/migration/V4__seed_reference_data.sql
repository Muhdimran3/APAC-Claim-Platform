-- Demo reference data (fictional people and policies). In a real system users would come
-- from an identity provider and policies from a policy admin system.
-- Fixed UUIDs so the frontend, Swagger examples and tests can refer to them.

INSERT INTO app_user (id, username, display_name, role) VALUES
    ('00000000-0000-0000-0000-000000000101', 'alice',  'Alice Tan',    'CLAIMANT'),
    ('00000000-0000-0000-0000-000000000102', 'ben',    'Ben Wong',     'CLAIMANT'),
    ('00000000-0000-0000-0000-000000000201', 'olivia', 'Olivia Lim',   'OFFICER'),
    ('00000000-0000-0000-0000-000000000202', 'oscar',  'Oscar Reyes',  'OFFICER'),
    ('00000000-0000-0000-0000-000000000301', 'maya',   'Maya Chen',    'MANAGER');

-- One policy per market so exposure shows six currencies.
INSERT INTO policy (id, policy_number, holder_id, market, product, currency, sum_insured, effective_from, effective_to) VALUES
    ('00000000-0000-0000-0000-000000001001', 'SG-MOT-0001', '00000000-0000-0000-0000-000000000101', 'SG', 'MOTOR',    'SGD',     80000.00, '2026-01-01', '2027-12-31'),
    ('00000000-0000-0000-0000-000000001002', 'AU-PRP-0001', '00000000-0000-0000-0000-000000000101', 'AU', 'PROPERTY', 'AUD',    750000.00, '2026-01-01', '2027-12-31'),
    ('00000000-0000-0000-0000-000000001003', 'HK-MOT-0001', '00000000-0000-0000-0000-000000000102', 'HK', 'MOTOR',    'HKD',    400000.00, '2026-01-01', '2027-12-31'),
    ('00000000-0000-0000-0000-000000001004', 'MY-PRP-0001', '00000000-0000-0000-0000-000000000102', 'MY', 'PROPERTY', 'MYR',   1200000.00, '2026-01-01', '2027-12-31'),
    ('00000000-0000-0000-0000-000000001005', 'TH-MOT-0001', '00000000-0000-0000-0000-000000000102', 'TH', 'MOTOR',    'THB',   1500000.00, '2026-01-01', '2027-12-31'),
    ('00000000-0000-0000-0000-000000001006', 'ID-PRP-0001', '00000000-0000-0000-0000-000000000101', 'ID', 'PROPERTY', 'IDR', 5000000000.00, '2026-01-01', '2027-12-31');
