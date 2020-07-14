SELECT DISTINCT
        omop.concept_name AS omop_concept,
        omop.umls_cui,
        atf.struct_id,
        s.name AS struct_name,
        atf.target_id,
        atf.target_name,
        atf.gene,
        atf.action_type,
        atf.act_source,
        atf.act_type,
        atf.act_comment,
        atf.relation,
        atf.moa,
        atf.moa_source,
        atf.moa_source_url,
        atf.moa_ref_id,
        r.pmid,
        r.doi,
        r.title,
        r.dp_year
FROM
        act_table_full atf
JOIN structures s ON s.id = atf.struct_id
JOIN omop_relationship omop ON omop.struct_id = s.id
LEFT OUTER JOIN reference r ON r.id = atf.moa_ref_id
WHERE
        --atf.moa > 0
        --AND 
        omop.relationship_name = 'indication'
;
