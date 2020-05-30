SELECT DISTINCT
        p1.id AS prod_id1,
        p1.generic_name AS prod_name1,
        p2.id AS prod_id2,
        p2.generic_name AS prod_name2,
        s1.id AS struct_id1,
        s1.name AS drugname1,
        s2.id AS struct_id2,
        s2.name AS drugname2,
        ddi.drug_class1,
        ddi.drug_class2,
        drug_class1.id drug_class_id1,
        drug_class1.source source1,
        drug_class1.is_group is_group1,
        drug_class2.id drug_class_id2,
        drug_class2.source source2,
        drug_class2.is_group is_group2,
        ddi.id AS ddi_id,
        ddi.source_id
FROM
        ddi
JOIN drug_class drug_class1 ON drug_class1.name = ddi.drug_class1
JOIN drug_class drug_class2 ON drug_class2.name = ddi.drug_class2
JOIN struct2drgclass s2dc1 ON s2dc1.drug_class_id = drug_class1.id
JOIN struct2drgclass s2dc2 ON s2dc2.drug_class_id = drug_class2.id
JOIN structures s1 ON s1.id = s2dc1.struct_id
JOIN structures s2 ON s2.id = s2dc2.struct_id
JOIN active_ingredient ai1 ON ai1.struct_id = s1.id
JOIN product p1 ON p1.ndc_product_code = ai1.ndc_product_code
JOIN active_ingredient ai2 ON ai2.struct_id = s2.id
JOIN product p2 ON p2.ndc_product_code = ai2.ndc_product_code
;