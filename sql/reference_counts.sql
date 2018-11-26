--
SELECT
	COUNT(DISTINCT ref.id) "reference_count"
FROM
	public.reference AS ref
	;
--
SELECT
	COUNT(DISTINCT ref.id) "activity_reference_count"
FROM
	public.reference AS ref,
	public.activities AS a
WHERE
	ref.id = a.ref_id
	;
--
SELECT
	COUNT(DISTINCT ref.id) "moa_reference_count"
FROM
	public.reference AS ref,
	public.activities AS a
WHERE
	ref.id = a.moa_ref_id
	;
--
--
