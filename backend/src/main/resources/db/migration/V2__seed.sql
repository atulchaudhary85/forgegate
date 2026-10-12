INSERT INTO roles(name) VALUES ('EMPLOYEE'),('MANAGER'),('SECURITY_REVIEWER'),('ADMIN') ON CONFLICT DO NOTHING;
-- Development-only password for all seeded accounts: password. BCrypt hash, never a plaintext password.
INSERT INTO users(name,email,password_hash,department,manager_id,role_id) VALUES
 ('Rahul Employee','employee@forgegate.local','$2a$10$fQzuoAn6LD0j6W1BaKGegeQ5FG5JewIY/50ZTAhqxI7i.X8lFFq7K','Engineering',2,(SELECT id FROM roles WHERE name='EMPLOYEE')),
 ('Meera Manager','manager@forgegate.local','$2a$10$fQzuoAn6LD0j6W1BaKGegeQ5FG5JewIY/50ZTAhqxI7i.X8lFFq7K','Engineering',4,(SELECT id FROM roles WHERE name='MANAGER')),
 ('Sameer Security','security@forgegate.local','$2a$10$fQzuoAn6LD0j6W1BaKGegeQ5FG5JewIY/50ZTAhqxI7i.X8lFFq7K','Security',4,(SELECT id FROM roles WHERE name='SECURITY_REVIEWER')),
 ('Aditi Admin','admin@forgegate.local','$2a$10$fQzuoAn6LD0j6W1BaKGegeQ5FG5JewIY/50ZTAhqxI7i.X8lFFq7K','IT',NULL,(SELECT id FROM roles WHERE name='ADMIN'))
ON CONFLICT(email) DO NOTHING;
INSERT INTO resources(name,type,sensitivity,owner_department) VALUES
 ('payments-repo','GITHUB_REPO','HIGH','Engineering'),
 ('Finance Database','DATABASE','HIGH','Finance'),
 ('Engineering Dashboard','DASHBOARD','LOW','Engineering') ON CONFLICT(name) DO NOTHING;
INSERT INTO policies(resource_id,allowed_department,max_duration_days,requires_security_approval)
SELECT id, CASE name WHEN 'Finance Database' THEN 'Finance' ELSE NULL END, CASE name WHEN 'payments-repo' THEN 7 ELSE 30 END, sensitivity='HIGH' FROM resources
WHERE name IN ('payments-repo','Finance Database','Engineering Dashboard')
AND NOT EXISTS (SELECT 1 FROM policies p WHERE p.resource_id=resources.id AND p.is_active=true);
