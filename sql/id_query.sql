--
SELECT DISTINCT
	id.struct_id,
	id.identifier,
	id.id_type,
	s.name
FROM
	public.structures s
JOIN
	public.identifier id ON s.id = id.struct_id
WHERE
	id.identifier = '3386'
	;
--
--
