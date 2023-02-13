SELECT
        id_type,
        COUNT(struct_id)
FROM
        identifier
GROUP BY id_type
        ;