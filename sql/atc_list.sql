SELECT DISTINCT
	id,
	code,
	l1_code,
	l1_name,
	l2_code,
	l2_name,
	l3_code,
	l3_name,
	l4_code,
	l4_name
FROM
	public.atc
ORDER BY
	l1_code,
	l2_code,
	l3_code,
	l4_code
	;
