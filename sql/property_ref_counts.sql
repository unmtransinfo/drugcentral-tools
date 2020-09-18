SELECT
	p.property_type_symbol,
	p.source,
	r.authors,
	p.reference_id,
	COUNT(*)
FROM
	property p,
	reference r
WHERE
	p.reference_id = r.id
GROUP BY
	p.property_type_symbol,
	p.source,
	p.reference_id,
	r.authors
;
