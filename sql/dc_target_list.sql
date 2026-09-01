SELECT
        tdict.id AS target_id,
        tdict.name AS target_name,
        tdict.target_class,
        tdict.protein_type,
        tdict.protein_components,
        tcomp.id AS component_id,
        tcomp.accession AS target_uniprot,
        tcomp.swissprot,
        tcomp.organism AS target_organism,
        tcomp.name AS component_name,
        tcomp.gene AS gene_symbol,
        tcomp.geneid,
        tcomp.tdl
FROM
        target_component tcomp
        JOIN td2tc ON td2tc.component_id = tcomp.id
        JOIN target_dictionary tdict ON tdict.id = td2tc.target_id
ORDER BY
        target_id, component_id
        ;