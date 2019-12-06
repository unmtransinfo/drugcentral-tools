--
SELECT
	id.struct_id,
	'http://drugcentral.org/drugcard/'||id.struct_id AS "url",
	id.identifier AS "pubchem_cid",
	id.id_type,
	s.name
FROM
	structures s
JOIN
	identifier id ON s.id = id.struct_id
WHERE
	id_type = 'PUBCHEM_CID'
	AND id.identifier IN ( '3017', '6083', '444795', '6758', '68841', '124886', '3054', '445154', '5870')
	;
--
