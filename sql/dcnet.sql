SELECT DISTINCT
	s.id AS "struct_id",
	s.name AS "struct_name",
	s.smiles,
	s.cd_formula AS "formula",
	a.relation,
	a.act_type,
	a.act_value,
	a.act_unit,
	td.id AS "target_id",
	td.name AS "target_name",
	td.target_class,
	td.protein_type,
	tc.id AS "tc_id",
	tc.accession,
	tc.swissprot,
	tc.organism,
	tc.gene,
	tc.geneid,
	tc.name AS "tc_name"
FROM
	structures s
JOIN
	act_table_full a ON a.struct_id = s.id
JOIN
	target_dictionary td ON td.id = a.target_id
JOIN
	td2tc ON td2tc.target_id = td.id
JOIN
	target_component tc ON tc.id = td2tc.component_id
WHERE
	tc.organism = 'Homo sapiens'
	;
