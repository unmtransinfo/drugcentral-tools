SELECT
        atf.act_id,
        atf.struct_id,
        s.name AS struct_name,
        atf.target_id,
        atf.target_name,
        atf.target_class,
        atf.accession,
        atf.gene,
        atf.action_type,
        atf.moa_source,
        atf.moa_source_url,
        atf.moa_ref_id,
        r.pmid,
        r.doi,
        r.title,
        r.authors,
        r.dp_year
FROM
        act_table_full atf
JOIN structures s ON s.id = atf.struct_id
JOIN reference r ON r.id = atf.moa_ref_id
WHERE
        moa > 0
;
