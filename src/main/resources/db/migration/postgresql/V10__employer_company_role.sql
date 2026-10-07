-- Company teams (#71): the person's role in their company. Everyone who already has a company is its owner.
alter table employers add column company_role varchar(20);
update employers set company_role = 'OWNER' where company_id is not null;
