#!/usr/bin/env python3
from pathlib import Path
from collections import Counter
import csv,re,sys
root=Path(__file__).resolve().parents[1]
seed=root/'src/main/resources/db/production-ready/catalog.sql'
script=seed.read_text(encoding='utf-8')
checks={
 'categories':len(re.findall(r"\('VN-CAT-\d{2}'",script)),
 'services':len(re.findall(r"\('VN-SVC-\d{3}'",script)),
 'activities':len(re.findall(r"\('VN-ACT-\d{3}'",script)),
 'packages':len(re.findall(r"\('VN-PKG-\d{3}'",script)),
 'policies':len(re.findall(r"\('VN-POL-\d{3}'",script)),
 'tours':len(re.findall(r"\('VN-SHORE-\d{3}'",script)),
 'employees':len(re.findall(r"\('VN-EMP-\d{4}'",script)),
}
expected={'categories':12,'services':60,'activities':35,'packages':24,'policies':30,'tours':20,'employees':120}
for k,n in expected.items():
 if checks[k]!=n:sys.exit(f'FAIL {k}: {checks[k]} expected {n}')
csvpath=root/'src/main/resources/db/production-ready/employees.csv'
with csvpath.open(encoding='utf-8-sig',newline='') as f: rows=list(csv.DictReader(f))
if len(rows)!=120 or len(set(row['employee_code'] for row in rows))!=120:sys.exit('Employee CSV invalid')
for f in ['V110__vi_operational_catalog_staging_and_i18n.sql','V111__package_activity_quotas_verified_tour_view.sql']:
 if not (root/'src/main/resources/db/migration'/f).exists():sys.exit('Missing migration: '+f)
print('PASS static catalogue counts:',checks,'(not PostgreSQL/Flyway tested)')
