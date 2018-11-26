--
SELECT DISTINCT
	s.smiles,
	s.name "struct_name",
	syn.name "synonym",
	s.id
FROM
	structures s,
	synonyms syn
WHERE
	syn.id = s.id
	AND LOWER(syn.name) IN (
		'meperidine',
		'tramadol',
		'codeine',
		'heroin',
		'morphine',
		'pentazocine'
		'dihydrocodeine',
		'tapentadol',
		'hydrocodone',
		'oxycodone',
		'methadone',
		'oxymorphone'
		'hydromorphone',
		'butorphanol',
		'buprenorphine',
		'fentanyl',
		'alfentanil',
		'remifentanil'
	)
	;
--
