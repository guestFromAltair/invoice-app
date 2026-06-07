-- Demo user
INSERT INTO users (id, email, password, role, created_at, updated_at, version)
VALUES (
           '00000000-0000-0000-0000-000000000001',
           'demo@invoiceapp.com',
           '$2a$10$eZ3UHSbzgbffv3P4GQLljunYHpZQ8u2N83MLKsq22q7tmPYvH8idu',
           'USER',
           now() - INTERVAL '6 months',
           now() - INTERVAL '6 months',
           0
       )
    ON CONFLICT (email) DO NOTHING;

INSERT INTO clients (id, owner_id, name, email, phone, address, vat_number, created_at, updated_at, version)
VALUES
    (
        '00000000-0000-0000-0001-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'Meridian Technologies',
        'accounts@meridiantech.io',
        '+33 1 42 68 53 00',
        '14 Avenue des Champs-Élysées, 75008 Paris',
        'FR12345678901',
        now() - INTERVAL '5 months',
        now() - INTERVAL '5 months',
        0
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        '00000000-0000-0000-0000-000000000001',
        'Luminary Group',
        'billing@luminarygroup.eu',
        '+44 20 7946 0831',
        '27 Old Broad Street, London EC2N 1HQ',
        'GB987654321',
        now() - INTERVAL '4 months',
        now() - INTERVAL '4 months',
        0
    ),
    (
        '00000000-0000-0000-0001-000000000003',
        '00000000-0000-0000-0000-000000000001',
        'Nexus Capital Partners',
        'finance@nexuscapital.com',
        '+33 1 56 43 21 00',
        '8 Place Vendôme, 75001 Paris',
        'FR98765432109',
        now() - INTERVAL '3 months',
        now() - INTERVAL '3 months',
        0
    ),
    (
        '00000000-0000-0000-0001-000000000004',
        '00000000-0000-0000-0000-000000000001',
        'Apex Digital Studio',
        'hello@apexdigital.fr',
        '+33 6 12 34 56 78',
        '3 Rue de la Paix, 75002 Paris',
        'FR11223344556',
        now() - INTERVAL '2 months',
        now() - INTERVAL '2 months',
        0
    ),
    (
        '00000000-0000-0000-0001-000000000005',
        '00000000-0000-0000-0000-000000000001',
        'Strata Consulting',
        'invoices@strataconsulting.de',
        '+49 30 12345678',
        'Unter den Linden 10, 10117 Berlin',
        'DE123456789',
        now() - INTERVAL '1 month',
        now() - INTERVAL '1 month',
        0
    )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO invoices (
    id, invoice_number, client_id, created_by,
    status, issue_date, due_date,
    subtotal, tax_rate, tax_amount, discount_amount, total,
    notes, created_at, updated_at, version
)
VALUES
    (
        '00000000-0000-0000-0002-000000000001',
        'INV-2025-00001',
        '00000000-0000-0000-0001-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'PAID',
        now()::date - 150,
        now()::date - 120,
        8416.6667, 0.2000, 1683.3333, 0.0000, 10100.0000,
        'Q1 software development retainer — Meridian Technologies',
        now() - INTERVAL '150 days',
        now() - INTERVAL '120 days',
        2
    ),
    (
        '00000000-0000-0000-0002-000000000002',
        'INV-2025-00002',
        '00000000-0000-0000-0001-000000000002',
        '00000000-0000-0000-0000-000000000001',
        'PAID',
        now()::date - 130,
        now()::date - 100,
        5200.0000, 0.2000, 1040.0000, 0.0000, 6240.0000,
        'UX audit and design system — Luminary Group',
        now() - INTERVAL '130 days',
        now() - INTERVAL '100 days',
        2
    ),
    (
        '00000000-0000-0000-0002-000000000003',
        'INV-2025-00003',
        '00000000-0000-0000-0001-000000000003',
        '00000000-0000-0000-0000-000000000001',
        'PAID',
        now()::date - 110,
        now()::date - 80,
        12000.0000, 0.2000, 2400.0000, 0.0000, 14400.0000,
        'Due diligence technical assessment — Nexus Capital Partners',
        now() - INTERVAL '110 days',
        now() - INTERVAL '80 days',
        2
    ),
    (
        '00000000-0000-0000-0002-000000000004',
        'INV-2025-00004',
        '00000000-0000-0000-0001-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'PAID',
        now()::date - 90,
        now()::date - 60,
        6750.0000, 0.2000, 1350.0000, 0.0000, 8100.0000,
        'API integration and backend development — Meridian Technologies',
        now() - INTERVAL '90 days',
        now() - INTERVAL '60 days',
        2
    ),
    (
        '00000000-0000-0000-0002-000000000005',
        'INV-2025-00005',
        '00000000-0000-0000-0001-000000000004',
        '00000000-0000-0000-0000-000000000001',
        'PAID',
        now()::date - 75,
        now()::date - 45,
        3600.0000, 0.2000, 720.0000, 0.0000, 4320.0000,
        'Brand identity and logo design — Apex Digital Studio',
        now() - INTERVAL '75 days',
        now() - INTERVAL '45 days',
        2
    ),
    (
        '00000000-0000-0000-0002-000000000006',
        'INV-2025-00006',
        '00000000-0000-0000-0001-000000000002',
        '00000000-0000-0000-0000-000000000001',
        'SENT',
        now()::date - 5,
        now()::date + 30,
        9500.0000, 0.2000, 1900.0000, 0.0000, 11400.0000,
        'Q3 product development milestone — Luminary Group',
        now() - INTERVAL '20 days',
        now() - INTERVAL '20 days',
        1
    ),
    (
        '00000000-0000-0000-0002-000000000007',
        'INV-2025-00007',
        '00000000-0000-0000-0001-000000000005',
        '00000000-0000-0000-0000-000000000001',
        'SENT',
        now()::date - 2,
        now()::date + 12,
        4800.0000, 0.2000, 960.0000, 0.0000, 5760.0000,
        'Strategic consulting engagement — Strata Consulting',
        now() - INTERVAL '10 days',
        now() - INTERVAL '10 days',
        1
    ),
    (
        '00000000-0000-0000-0002-000000000008',
        'INV-2025-00008',
        '00000000-0000-0000-0001-000000000003',
        '00000000-0000-0000-0000-000000000001',
        'OVERDUE',
        now()::date - 60,
        now()::date - 15,
        7200.0000, 0.2000, 1440.0000, 0.0000, 8640.0000,
        'Security audit and penetration testing — Nexus Capital Partners',
        now() - INTERVAL '60 days',
        now() - INTERVAL '15 days',
        1
    ),
    (
        '00000000-0000-0000-0002-000000000009',
        'INV-2025-00009',
        '00000000-0000-0000-0001-000000000001',
        '00000000-0000-0000-0000-000000000001',
        'OVERDUE',
        now()::date - 45,
        now()::date - 5,
        3200.0000, 0.2000, 640.0000, 0.0000, 3840.0000,
        'Emergency infrastructure support — Meridian Technologies',
        now() - INTERVAL '45 days',
        now() - INTERVAL '5 days',
        1
    ),
    (
        '00000000-0000-0000-0002-000000000010',
        'INV-2025-00010',
        '00000000-0000-0000-0001-000000000004',
        '00000000-0000-0000-0000-000000000001',
        'DRAFT',
        now()::date,
        now()::date + 30,
        5500.0000, 0.2000, 1100.0000, 0.0000, 6600.0000,
        'Mobile app development phase 1 — Apex Digital Studio',
        now(),
        now(),
        0
    ),
    (
        '00000000-0000-0000-0002-000000000011',
        'INV-2025-00011',
        '00000000-0000-0000-0001-000000000005',
        '00000000-0000-0000-0000-000000000001',
        'CANCELLED',
        now()::date - 100,
        now()::date - 70,
        2800.0000, 0.2000, 560.0000, 0.0000, 3360.0000,
        'Project cancelled by client — Strata Consulting',
        now() - INTERVAL '100 days',
        now() - INTERVAL '95 days',
        1
    )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO line_items (
    id, invoice_id, description,
    quantity, unit_price, discount_pct, line_total,
    position, created_at
)
VALUES
    ('00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0002-000000000001',
     'Senior backend engineering (Java/Spring Boot)', 40.00, 150.0000, 0.0000, 6000.0000, 1, now() - INTERVAL '150 days'),
    ('00000000-0000-0000-0003-000000000002', '00000000-0000-0000-0002-000000000001',
     'Architecture review and technical documentation', 10.00, 175.0000, 0.0000, 1750.0000, 2, now() - INTERVAL '150 days'),
    ('00000000-0000-0000-0003-000000000003', '00000000-0000-0000-0002-000000000001',
     'DevOps and CI/CD pipeline setup', 5.00, 150.0000, 0.0000, 750.0000, 3, now() - INTERVAL '150 days'),
    ('00000000-0000-0000-0003-000000000004', '00000000-0000-0000-0002-000000000002',
     'UX audit — user flows and accessibility review', 8.00, 200.0000, 0.0000, 1600.0000, 1, now() - INTERVAL '130 days'),
    ('00000000-0000-0000-0003-000000000005', '00000000-0000-0000-0002-000000000002',
     'Design system creation (components, tokens, guidelines)', 16.00, 175.0000, 0.0000, 2800.0000, 2, now() - INTERVAL '130 days'),
    ('00000000-0000-0000-0003-000000000006', '00000000-0000-0000-0002-000000000002',
     'Figma prototyping and stakeholder presentation', 4.00, 200.0000, 0.0000, 800.0000, 3, now() - INTERVAL '130 days'),
    ('00000000-0000-0000-0003-000000000007', '00000000-0000-0000-0002-000000000003',
     'Technical due diligence assessment', 20.00, 300.0000, 0.0000, 6000.0000, 1, now() - INTERVAL '110 days'),
    ('00000000-0000-0000-0003-000000000008', '00000000-0000-0000-0002-000000000003',
     'Codebase quality report and risk analysis', 8.00, 300.0000, 0.0000, 2400.0000, 2, now() - INTERVAL '110 days'),
    ('00000000-0000-0000-0003-000000000009', '00000000-0000-0000-0002-000000000003',
     'Executive summary and investment recommendations', 4.00, 225.0000, 0.1000, 810.0000, 3, now() - INTERVAL '110 days'),
    ('00000000-0000-0000-0003-000000000010', '00000000-0000-0000-0002-000000000004',
     'REST API design and implementation', 25.00, 150.0000, 0.0000, 3750.0000, 1, now() - INTERVAL '90 days'),
    ('00000000-0000-0000-0003-000000000011', '00000000-0000-0000-0002-000000000004',
     'Third-party integrations (Stripe, SendGrid, AWS S3)', 15.00, 150.0000, 0.0000, 2250.0000, 2, now() - INTERVAL '90 days'),
    ('00000000-0000-0000-0003-000000000012', '00000000-0000-0000-0002-000000000004',
     'Integration testing and documentation', 5.00, 150.0000, 0.0000, 750.0000, 3, now() - INTERVAL '90 days'),
    ('00000000-0000-0000-0003-000000000013', '00000000-0000-0000-0002-000000000005',
     'Brand strategy workshop', 2.00, 400.0000, 0.0000, 800.0000, 1, now() - INTERVAL '75 days'),
    ('00000000-0000-0000-0003-000000000014', '00000000-0000-0000-0002-000000000005',
     'Logo design (3 concepts + revisions)', 1.00, 1800.0000, 0.0000, 1800.0000, 2, now() - INTERVAL '75 days'),
    ('00000000-0000-0000-0003-000000000015', '00000000-0000-0000-0002-000000000005',
     'Brand guidelines document', 1.00, 1000.0000, 0.0000, 1000.0000, 3, now() - INTERVAL '75 days'),
    ('00000000-0000-0000-0003-000000000016', '00000000-0000-0000-0002-000000000006',
     'Product roadmap implementation — sprint 7 and 8', 30.00, 175.0000, 0.0000, 5250.0000, 1, now() - INTERVAL '20 days'),
    ('00000000-0000-0000-0003-000000000017', '00000000-0000-0000-0002-000000000006',
     'Performance optimisation and load testing', 10.00, 175.0000, 0.0000, 1750.0000, 2, now() - INTERVAL '20 days'),
    ('00000000-0000-0000-0003-000000000018', '00000000-0000-0000-0002-000000000006',
     'Code review and knowledge transfer session', 4.00, 125.0000, 0.0000, 500.0000, 3, now() - INTERVAL '20 days'),
    ('00000000-0000-0000-0003-000000000019', '00000000-0000-0000-0002-000000000007',
     'Digital transformation strategy (phase 1)', 12.00, 250.0000, 0.0000, 3000.0000, 1, now() - INTERVAL '10 days'),
    ('00000000-0000-0000-0003-000000000020', '00000000-0000-0000-0002-000000000007',
     'Stakeholder interviews and requirements gathering', 6.00, 200.0000, 0.0000, 1200.0000, 2, now() - INTERVAL '10 days'),
    ('00000000-0000-0000-0003-000000000021', '00000000-0000-0000-0002-000000000007',
     'Delivery roadmap and executive presentation', 3.00, 200.0000, 0.0000, 600.0000, 3, now() - INTERVAL '10 days'),
    ('00000000-0000-0000-0003-000000000022', '00000000-0000-0000-0002-000000000008',
     'Infrastructure security audit', 16.00, 225.0000, 0.0000, 3600.0000, 1, now() - INTERVAL '60 days'),
    ('00000000-0000-0000-0003-000000000023', '00000000-0000-0000-0002-000000000008',
     'Penetration testing (web + API + database)', 12.00, 225.0000, 0.0000, 2700.0000, 2, now() - INTERVAL '60 days'),
    ('00000000-0000-0000-0003-000000000024', '00000000-0000-0000-0002-000000000008',
     'Vulnerability report and remediation plan', 4.00, 225.0000, 0.0000, 900.0000, 3, now() - INTERVAL '60 days'),
    ('00000000-0000-0000-0003-000000000025', '00000000-0000-0000-0002-000000000009',
     'Emergency on-call infrastructure support', 8.00, 250.0000, 0.0000, 2000.0000, 1, now() - INTERVAL '45 days'),
    ('00000000-0000-0000-0003-000000000026', '00000000-0000-0000-0002-000000000009',
     'Database performance investigation and fix', 4.00, 250.0000, 0.0000, 1000.0000, 2, now() - INTERVAL '45 days'),
    ('00000000-0000-0000-0003-000000000027', '00000000-0000-0000-0002-000000000009',
     'Post-incident report and prevention recommendations', 2.00, 100.0000, 0.0000, 200.0000, 3, now() - INTERVAL '45 days'),
    ('00000000-0000-0000-0003-000000000028', '00000000-0000-0000-0002-000000000010',
     'React Native mobile app development — phase 1', 20.00, 175.0000, 0.0000, 3500.0000, 1, now()),
    ('00000000-0000-0000-0003-000000000029', '00000000-0000-0000-0002-000000000010',
     'UI/UX implementation from Figma designs', 8.00, 175.0000, 0.0000, 1400.0000, 2, now()),
    ('00000000-0000-0000-0003-000000000030', '00000000-0000-0000-0002-000000000010',
     'App Store and Play Store submission setup', 2.00, 150.0000, 0.1000, 270.0000, 3, now()),
    ('00000000-0000-0000-0003-000000000031', '00000000-0000-0000-0002-000000000011',
     'Cloud migration assessment', 8.00, 200.0000, 0.0000, 1600.0000, 1, now() - INTERVAL '100 days'),
    ('00000000-0000-0000-0003-000000000032', '00000000-0000-0000-0002-000000000011',
     'AWS architecture design', 6.00, 200.0000, 0.0000, 1200.0000, 2, now() - INTERVAL '100 days')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO payments (
    id, invoice_id, amount, paid_at, method, notes, created_at, version
)
VALUES
    (
        '00000000-0000-0000-0004-000000000001',
        '00000000-0000-0000-0002-000000000001',
        10200.0000,
        now() - INTERVAL '118 days',
        'BANK_TRANSFER',
        'Wire transfer received — ref: MT-2025-Q1-001',
        now() - INTERVAL '118 days',
        0
    ),
    (
        '00000000-0000-0000-0004-000000000002',
        '00000000-0000-0000-0002-000000000002',
        6240.0000,
        now() - INTERVAL '98 days',
        'BANK_TRANSFER',
        'Payment received in full — ref: LG-2025-0087',
        now() - INTERVAL '98 days',
        0
    ),
    (
        '00000000-0000-0000-0004-000000000003',
        '00000000-0000-0000-0002-000000000003',
        14400.0000,
        now() - INTERVAL '77 days',
        'BANK_TRANSFER',
        'Due diligence fee settled — ref: NC-DD-2025-004',
        now() - INTERVAL '77 days',
        0
    ),
    (
        '00000000-0000-0000-0004-000000000004',
        '00000000-0000-0000-0002-000000000004',
        8100.0000,
        now() - INTERVAL '58 days',
        'BANK_TRANSFER',
        'API project payment — ref: MT-2025-API-002',
        now() - INTERVAL '58 days',
        0
    ),
    (
        '00000000-0000-0000-0004-000000000005',
        '00000000-0000-0000-0002-000000000005',
        4320.0000,
        now() - INTERVAL '43 days',
        'CREDIT_CARD',
        'Card payment processed online',
        now() - INTERVAL '43 days',
        0
    ),
    (
        '00000000-0000-0000-0004-000000000006',
        '00000000-0000-0000-0002-000000000008',
        3000.0000,
        now() - INTERVAL '12 days',
        'BANK_TRANSFER',
        'Partial payment received — balance outstanding. Ref: NC-SEC-2025-DEP',
        now() - INTERVAL '12 days',
        0
    )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_log (
    id, entity_type, entity_id, action,
    old_value, new_value,
    performed_by, performed_at
)
VALUES
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000001',
        'INVOICE_CREATED',
        NULL,
        '{"invoiceNumber":"INV-2025-00001","status":"DRAFT","total":"10200.0000"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '150 days'
    ),
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000001',
        'INVOICE_SENT',
        '{"status":"DRAFT"}',
        '{"status":"SENT"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '149 days'
    ),
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000001',
        'PAYMENT_RECORDED',
        NULL,
        '{"amount":"10200.0000","method":"BANK_TRANSFER"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '118 days'
    ),
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000001',
        'INVOICE_PAID',
        '{"status":"SENT"}',
        '{"status":"PAID"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '118 days'
    ),
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000008',
        'INVOICE_CREATED',
        NULL,
        '{"invoiceNumber":"INV-2025-00008","status":"DRAFT","total":"8640.0000"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '60 days'
    ),
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000008',
        'INVOICE_SENT',
        '{"status":"DRAFT"}',
        '{"status":"SENT"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '59 days'
    ),
    (
        gen_random_uuid(),
        'INVOICE', '00000000-0000-0000-0002-000000000008',
        'INVOICE_OVERDUE',
        '{"status":"SENT"}',
        '{"status":"OVERDUE"}',
        '00000000-0000-0000-0000-000000000001',
        now() - INTERVAL '15 days'
    )
    ON CONFLICT (id) DO NOTHING;