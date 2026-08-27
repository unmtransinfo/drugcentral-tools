#!/usr/bin/env python
"""Export NCATS Inxight Activity -> Targets annotations.

This is a standalone handoff script. It does not import project-specific code or
read project-specific configuration. It calls the public Inxight substance API
directly, optionally resolves ChEMBL target IDs through the public ChEMBL API,
and writes flat TSV files plus run metadata/audit files.

Install:
    python -m pip install requests

Fast relationship export:
    python export_inxight_activity_targets_standalone.py --skip-chembl --workers 8

UniProt-enriched export:
    python export_inxight_activity_targets_standalone.py --workers 8

Smoke test:
    python export_inxight_activity_targets_standalone.py --limit-targets 1 --limit-substances 5

    Contributed by: Jessica Maine, NCATS Informatics Core
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import logging
import re
import sys
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import requests


LOGGER = logging.getLogger("inxight_activity_targets")

CHEMBL_ID_RE = re.compile(r"\b(CHEMBL\d+)\b", re.IGNORECASE)
UNIPROT_ACCESSION_RE = re.compile(
    r"^(?:[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9][A-Z][A-Z0-9]{2}[0-9]|"
    r"[A-NR-Z][0-9][A-Z][A-Z0-9]{2}[0-9][A-Z][A-Z0-9]{2}[0-9])(?:-\d+)?$"
)
UNII_RE = re.compile(r"^[A-Z0-9]{10}$")

NULL_STRINGS = {"", "-", ".", "na", "n/a", "nan", "none", "null", "not provided"}

ACTIVITY_TARGET_COLUMNS = [
    "unii",
    "drug_source_id",
    "drug_name",
    "substance_uuid",
    "substance_class",
    "substance_status",
    "substance_deprecated",
    "substance_url",
    "substance_lookup_status",
    "substance_lookup_error",
    "raw_target_id",
    "target_label",
    "target_id",
    "target_source",
    "target_chembl_id",
    "target_uniprot_id",
    "target_gene_symbol",
    "target_gene_id",
    "target_organism",
    "target_category",
    "chembl_lookup_status",
    "chembl_lookup_entity_type",
    "chembl_last_active",
    "chembl_resource_url",
    "chembl_target_type",
    "chembl_target_name",
    "chembl_target_organism",
    "chembl_tax_id",
    "chembl_component_count",
    "chembl_lookup_error",
    "pharmacology",
    "potency_type",
    "potency_value",
    "potency_unit",
    "potency_uri",
    "source_urls",
    "condition_labels",
    "stitcher_id",
    "target_facet_count",
]

EDGE_COLUMNS = [
    "source",
    "drug_source_id",
    "drug_label",
    "predicate",
    "target_id",
    "target_label",
    "target_category",
    "target_symbol",
    "target_uniprot_id",
    "target_gene_id",
    "activity_type",
    "activity_value",
    "activity_unit",
    "mechanism_of_action",
    "action_type",
    "evidence_source",
    "source_url",
]

DRUGCENTRAL_CANDIDATE_COLUMNS = [
    "unii",
    "drug_name",
    "drug_url",
    "target_uniprot_id",
    "target_label",
    "target_gene_symbol",
    "target_gene_id",
    "target_organism",
    "relationship_type",
    "pharmacology",
    "potency_type",
    "potency_value",
    "potency_unit",
    "evidence_url",
    "target_source",
    "target_chembl_id",
    "chembl_target_type",
    "chembl_lookup_status",
    "target_mapping_status",
    "stitcher_id",
]


class NonRetryableRequestError(RuntimeError):
    """Raised for API responses that should not be retried."""


def now_utc() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def clean(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return "" if text.lower() in NULL_STRINGS else text


def tsv_cell(value: Any) -> str:
    return re.sub(r"[\t\r\n]+", " ", clean(value)).strip()


def ensure_parent(path: str | Path) -> Path:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    return p


def read_json(path: str | Path) -> Any:
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return None


def write_json(path: str | Path, data: Any) -> None:
    p = ensure_parent(path)
    tmp = p.with_suffix(p.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, sort_keys=False)
    tmp.replace(p)


def write_compact_json(path: str | Path, data: Any) -> None:
    p = ensure_parent(path)
    tmp = p.with_suffix(p.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, separators=(",", ":"))
    tmp.replace(p)


def file_stats(path: str | Path) -> dict[str, Any]:
    p = Path(path)
    data = p.read_bytes() if p.exists() else b""
    return {
        "path": str(p),
        "bytes": len(data),
        "md5": hashlib.md5(data).hexdigest(),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def write_tsv(path: str | Path, rows: Iterable[dict[str, Any]], columns: list[str]) -> None:
    p = ensure_parent(path)
    tmp = p.with_suffix(p.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=columns, delimiter="\t", lineterminator="\n", extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({col: tsv_cell(row.get(col, "")) for col in columns})
    tmp.replace(p)


def join_unique(values: Iterable[Any], sep: str = "|") -> str:
    out: list[str] = []
    for value in values:
        text = clean(value)
        if text and text not in out:
            out.append(text)
    return sep.join(out)


def normalize_unii(value: Any) -> str:
    text = re.sub(r"[^A-Za-z0-9]", "", clean(value)).upper()
    return text if text and len(text) <= 12 else ""


def cache_key(value: str) -> str:
    return hashlib.sha1(value.encode("utf-8")).hexdigest()


def extract_chembl_id(value: str) -> str:
    match = CHEMBL_ID_RE.search(clean(value))
    return match.group(1).upper() if match else ""


def direct_uniprot_ids(raw_target_id: str) -> list[str]:
    accessions: list[str] = []
    for part in re.split(r"\s*(?:\|{1,3}|[,;])\s*", clean(raw_target_id)):
        accession = clean(part)
        if accession and UNIPROT_ACCESSION_RE.match(accession) and accession not in accessions:
            accessions.append(accession)
    return accessions


def normalize_gene_id(value: Any) -> str:
    text = clean(value)
    if re.fullmatch(r"\d+\.0", text):
        return text[:-2]
    return text


def condition_label(value: Any) -> str:
    if isinstance(value, dict):
        return clean(value.get("label") or value.get("name") or value.get("condition"))
    return clean(value)


class InxightActivityTargetExporter:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.base_url = args.base_url.rstrip("/")
        self.chembl_target_url = args.chembl_target_url
        self.chembl_lookup_url = args.chembl_lookup_url

        self.output_file = Path(args.output_file)
        self.edge_output_file = Path(args.edge_output_file)
        self.drugcentral_candidate_file = Path(args.drugcentral_candidate_file)
        self.metadata_file = Path(args.metadata_file)
        self.qc_file = Path(args.qc_file)
        self.qc_md_file = Path(args.qc_md_file)
        self.candidate_index_file = Path(args.candidate_index_file)
        self.skipped_target_facet_file = Path(args.skipped_target_facet_file)
        self.additional_failure_file = Path(args.additional_failure_file)

        self.cache_dir = Path(args.cache_dir)
        self.facet_cache_dir = self.cache_dir / "target_unii_facets"
        self.facet_failure_cache_dir = self.cache_dir / "target_unii_facet_failures"
        self.drug_name_cache_dir = self.cache_dir / "drug_names"
        self.substance_cache_dir = self.cache_dir / "substances"
        self.additional_cache_dir = self.cache_dir / "additional"
        self.additional_failure_cache_dir = self.cache_dir / "additional_failures"
        self.chembl_cache_dir = self.cache_dir / "chembl_targets"
        self.chembl_lookup_cache_dir = self.cache_dir / "chembl_id_lookup"

        self.timeout = args.request_timeout
        self.name_timeout = args.name_request_timeout
        self.max_attempts = args.max_attempts
        self.name_max_attempts = args.name_max_attempts
        self.sleep_s = args.sleep_seconds
        self.workers = max(1, args.workers)
        self.facet_page_size = args.facet_page_size
        self.unii_facet_page_size = args.unii_facet_page_size
        self.force_refresh = args.force_refresh
        self.enable_chembl = not args.skip_chembl
        self.fetch_drug_names = not args.skip_drug_names
        self.fetch_substance_details = args.fetch_substance_details
        self.base_facets = [clean(v) for v in args.base_facet if clean(v)]
        self.additional_nonretryable_statuses = set(args.additional_nonretryable_statuses)

    def run(self) -> None:
        started = now_utc()
        for path in (
            self.cache_dir,
            self.facet_cache_dir,
            self.facet_failure_cache_dir,
            self.drug_name_cache_dir,
            self.substance_cache_dir,
            self.additional_cache_dir,
            self.additional_failure_cache_dir,
            self.chembl_cache_dir,
            self.chembl_lookup_cache_dir,
        ):
            path.mkdir(parents=True, exist_ok=True)

        target_facets = self.primary_target_facets()
        if self.args.limit_targets:
            target_facets = target_facets[: self.args.limit_targets]
        LOGGER.info("Loaded %s Inxight Primary Target facet values", f"{len(target_facets):,}")

        candidate_index = self.discover_candidate_uniis(target_facets)
        uniis = sorted(candidate_index)
        if self.args.limit_substances:
            uniis = uniis[: self.args.limit_substances]
        LOGGER.info("Discovered %s target-bearing Inxight UNIIs", f"{len(uniis):,}")

        flat_rows, edge_rows = self.build_rows(uniis, candidate_index)
        flat_rows = sorted(
            flat_rows,
            key=lambda row: (row.get("drug_name", ""), row.get("unii", ""), row.get("target_label", "")),
        )
        edge_rows = sorted(
            edge_rows,
            key=lambda row: (row.get("drug_label", ""), row.get("drug_source_id", ""), row.get("target_label", "")),
        )
        drugcentral_rows = self.drugcentral_candidate_rows(flat_rows)

        write_tsv(self.output_file, flat_rows, ACTIVITY_TARGET_COLUMNS)
        write_tsv(self.edge_output_file, edge_rows, EDGE_COLUMNS)
        write_tsv(self.drugcentral_candidate_file, drugcentral_rows, DRUGCENTRAL_CANDIDATE_COLUMNS)
        self.write_candidate_index(candidate_index, uniis)
        self.write_additional_failures(uniis)
        qc = self.write_qc(flat_rows, edge_rows, candidate_index)

        metadata = {
            "processor": "export_inxight_activity_targets_standalone.py",
            "source_name": "NCATS Inxight Drugs",
            "source_version": "api_current",
            "transform_start": started,
            "transform_end": now_utc(),
            "command": " ".join(sys.argv),
            "python_version": sys.version,
            "requests_version": requests.__version__,
            "endpoints": {
                "base_url": self.base_url,
                "primary_target_facets": f"{self.base_url}/api/v1/substances/search/@facets",
                "substance_additional": f"{self.base_url}/api/v1/substances({{UNII}})/@additional",
                "substance_details": f"{self.base_url}/api/v1/substances({{UNII}})?view=key",
                "substance_names": f"{self.base_url}/api/v1/substances({{UNII}})/names",
                "chembl_target": self.chembl_target_url,
                "chembl_id_lookup": self.chembl_lookup_url,
            },
            "parameters": {
                "workers": self.workers,
                "request_timeout": self.timeout,
                "name_request_timeout": self.name_timeout,
                "max_attempts": self.max_attempts,
                "name_max_attempts": self.name_max_attempts,
                "sleep_seconds": self.sleep_s,
                "facet_page_size": self.facet_page_size,
                "unii_facet_page_size": self.unii_facet_page_size,
                "base_facets": self.base_facets,
                "skip_chembl": self.args.skip_chembl,
                "fetch_drug_names": self.fetch_drug_names,
                "fetch_substance_details": self.fetch_substance_details,
                "force_refresh": self.force_refresh,
                "limit_targets": self.args.limit_targets,
                "limit_substances": self.args.limit_substances,
                "additional_nonretryable_statuses": sorted(self.additional_nonretryable_statuses),
            },
            "outputs": [
                {"name": "inxight_activity_targets", "path": str(self.output_file), "records": len(flat_rows)},
                {"name": "inxight_activity_target_edges", "path": str(self.edge_output_file), "records": len(edge_rows)},
                {"name": "inxight_activity_targets_drugcentral_candidate", "path": str(self.drugcentral_candidate_file), "records": len(drugcentral_rows)},
                {"name": "candidate_index", "path": str(self.candidate_index_file), "records": len(uniis)},
                {"name": "skipped_target_facets", "path": str(self.skipped_target_facet_file), "records": qc["counts"]["skipped_target_facets"]},
                {"name": "additional_failures", "path": str(self.additional_failure_file), "records": qc["counts"]["additional_failures"]},
                {"name": "qc", "path": str(self.qc_file), "records": 1},
                {"name": "qc_markdown", "path": str(self.qc_md_file), "records": 1},
            ],
            "notes": [
                "Rows are Inxight GUI Activity -> Targets annotations.",
                "The DrugCentral candidate TSV is filtered to named UNII drugs with clean UniProtKB protein target IDs.",
                "DDI/victim/perpetrator/tox target annotations are excluded by filtering to @additional rows named 'Targets'.",
                "UNII is used as the drug key. Rich substance metadata is optional; display names are fetched from the Inxight names endpoint by default.",
                "Direct UniProt targets are retained in target_uniprot_id and become UniProtKB target IDs when there is a single accession.",
                "ChEMBL target IDs are mapped to UniProtKB target IDs only for clean human SINGLE PROTEIN targets.",
                "Inactive ChEMBL target IDs are retained as CHEMBL.TARGET source assertions and annotated with chembl_id_lookup status.",
            ],
        }
        write_json(self.metadata_file, metadata)
        LOGGER.info(
            "Wrote %s activity rows, %s edge rows, %s DrugCentral candidate rows, and metadata to %s",
            f"{len(flat_rows):,}",
            f"{len(edge_rows):,}",
            f"{len(drugcentral_rows):,}",
            self.metadata_file,
        )

    def primary_target_facets(self) -> list[dict[str, Any]]:
        suffix = f"_{self.facet_context_key()}" if self.base_facets else ""
        cache_file = self.cache_dir / f"primary_target_facets{suffix}.json"
        if cache_file.exists() and not self.force_refresh:
            data = read_json(cache_file)
            content = data.get("content", []) if isinstance(data, dict) else []
            if isinstance(content, list) and content:
                return content

        content: list[dict[str, Any]] = []
        fskip = 0
        while True:
            payload = self.fetch_json(
                f"{self.base_url}/api/v1/substances/search/@facets",
                params={
                    "wait": "false",
                    "kind": "ix.ginas.models.v1.Substance",
                    "skip": 0,
                    "fdim": self.facet_page_size,
                    "sideway": "true",
                    "field": "Primary Target",
                    "top": 1,
                    "fskip": fskip,
                    **self.base_facet_params(),
                },
            )
            batch = payload.get("content", []) if isinstance(payload, dict) else []
            content.extend(batch)
            total = int(payload.get("ftotal") or len(content))
            if not batch or len(content) >= total:
                break
            fskip += len(batch)
        write_json(cache_file, {"content": content, "fetched_at": now_utc()})
        return content

    def discover_candidate_uniis(self, target_facets: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
        candidates: dict[str, dict[str, Any]] = {}
        skipped: list[dict[str, str]] = []
        with ThreadPoolExecutor(max_workers=self.workers) as pool:
            futures = {pool.submit(self.unii_facets_for_target, facet): facet for facet in target_facets}
            for idx, future in enumerate(as_completed(futures), start=1):
                facet = futures[future]
                label = clean(facet.get("label"))
                count = int(facet.get("count") or 0)
                try:
                    uniis = future.result()
                except Exception as exc:
                    LOGGER.warning("Failed to discover UNIIs for target facet %r: %s", label, exc)
                    skipped.append({"target_label": label, "target_count": str(count), "error": str(exc)})
                    continue
                for unii in uniis:
                    entry = candidates.setdefault(
                        unii,
                        {"unii": unii, "target_facets": {}, "target_facet_count_sum": 0},
                    )
                    entry["target_facets"][label] = count
                    entry["target_facet_count_sum"] += count
                if idx % 250 == 0 or idx == len(target_facets):
                    LOGGER.info(
                        "Inxight target facet discovery: %s/%s target facets, %s unique UNIIs",
                        f"{idx:,}",
                        f"{len(target_facets):,}",
                        f"{len(candidates):,}",
                    )
        write_tsv(
            self.skipped_target_facet_file,
            sorted(skipped, key=lambda row: row.get("target_label", "")),
            ["target_label", "target_count", "error"],
        )
        return candidates

    def unii_facets_for_target(self, facet: dict[str, Any]) -> list[str]:
        label = clean(facet.get("label"))
        if not label:
            return []
        cache_file = self.facet_cache_dir / f"{cache_key(self.facet_context_key() + '|' + label)}.json"
        failure_cache_file = self.facet_failure_cache_dir / f"{cache_key(self.facet_context_key() + '|' + label)}.json"
        if cache_file.exists() and not self.force_refresh:
            data = read_json(cache_file)
            cached_uniis = data.get("uniis") if isinstance(data, dict) else None
            if isinstance(cached_uniis, list):
                return [normalize_unii(value) for value in cached_uniis if normalize_unii(value)]
        if failure_cache_file.exists() and not self.force_refresh:
            data = read_json(failure_cache_file)
            raise NonRetryableRequestError(clean(data.get("error")) or "Cached target facet lookup failure")

        uniis: list[str] = []
        fskip = 0
        try:
            while True:
                payload = self.fetch_json(
                    f"{self.base_url}/api/v1/substances/search/@facets",
                    params={
                        "wait": "false",
                        "kind": "ix.ginas.models.v1.Substance",
                        "skip": 0,
                        "fdim": self.unii_facet_page_size,
                        "sideway": "true",
                        "field": "FDA UNII",
                        "top": 1,
                        "fskip": fskip,
                        "facet": [*self.base_facets, f"Primary Target/{label}"],
                    },
                )
                batch = payload.get("content", []) if isinstance(payload, dict) else []
                for item in batch:
                    unii = normalize_unii(item.get("label"))
                    if unii:
                        uniis.append(unii)
                total = int(payload.get("ftotal") or len(uniis))
                if not batch or len(uniis) >= total:
                    break
                fskip += len(batch)
        except Exception as exc:
            write_json(
                failure_cache_file,
                {
                    "target_label": label,
                    "target_count": facet.get("count", ""),
                    "error": str(exc),
                    "failed_at": now_utc(),
                },
            )
            raise
        uniis = sorted(set(uniis))
        write_json(cache_file, {"target_label": label, "target_count": facet.get("count", ""), "uniis": uniis})
        return uniis

    def build_rows(
        self,
        uniis: list[str],
        candidate_index: dict[str, dict[str, Any]],
    ) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
        flat_rows: list[dict[str, str]] = []
        edge_rows: list[dict[str, str]] = []
        with ThreadPoolExecutor(max_workers=self.workers) as pool:
            futures = {pool.submit(self.rows_for_unii, unii, candidate_index.get(unii, {})): unii for unii in uniis}
            for idx, future in enumerate(as_completed(futures), start=1):
                unii = futures[future]
                try:
                    rows, edges = future.result()
                    flat_rows.extend(rows)
                    edge_rows.extend(edges)
                except Exception as exc:
                    LOGGER.warning("Failed to fetch Inxight Activity targets for UNII %s: %s", unii, exc)
                if idx % 250 == 0 or idx == len(uniis):
                    LOGGER.info(
                        "Inxight Activity fetch: %s/%s substances, %s target rows",
                        f"{idx:,}",
                        f"{len(uniis):,}",
                        f"{len(flat_rows):,}",
                    )
        return flat_rows, edge_rows

    def rows_for_unii(self, unii: str, candidate: dict[str, Any]) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
        additional = self.additional(unii)
        if not isinstance(additional, list):
            return [], []
        target_items = [item for item in additional if item.get("name") == "Targets"]
        if not target_items:
            return [], []
        substance = self.substance(unii) if self.fetch_substance_details else {"_lookup_status": "not_requested"}

        rows: list[dict[str, str]] = []
        edges: list[dict[str, str]] = []
        drug_source_id = f"UNII:{unii}"
        drug_label = self.drug_name(unii, substance) or unii
        substance_url = f"{self.base_url}/drug/{unii}"
        target_facets = candidate.get("target_facets", {}) if isinstance(candidate, dict) else {}

        for item in target_items:
            value = item.get("value") or {}
            row = self.flat_target_row(
                unii=unii,
                drug_source_id=drug_source_id,
                drug_label=drug_label,
                substance=substance,
                substance_url=substance_url,
                value=value,
                target_facets=target_facets,
            )
            rows.append(row)
            edges.append(self.edge_row(row))
        return rows, edges

    def drugcentral_candidate_rows(self, flat_rows: list[dict[str, str]]) -> list[dict[str, str]]:
        rows = []
        for row in flat_rows:
            drug_name = row.get("drug_name", "")
            if not drug_name or drug_name == row.get("unii"):
                continue
            target_id = row.get("target_id", "")
            if not target_id.startswith("UniProtKB:"):
                continue
            uniprot = target_id.split(":", 1)[1]
            if not UNIPROT_ACCESSION_RE.match(uniprot):
                continue
            if row.get("target_category") != "biolink:Protein":
                continue
            mapping_status = (
                "direct_uniprot_single_accession"
                if row.get("target_source") == "UniProt"
                else "chembl_human_single_protein_to_uniprot"
            )
            rows.append({
                "unii": row.get("unii", ""),
                "drug_name": drug_name,
                "drug_url": row.get("substance_url", ""),
                "target_uniprot_id": uniprot,
                "target_label": row.get("target_label", ""),
                "target_gene_symbol": row.get("target_gene_symbol", ""),
                "target_gene_id": row.get("target_gene_id", ""),
                "target_organism": row.get("target_organism") or row.get("chembl_target_organism", ""),
                "relationship_type": row.get("pharmacology", ""),
                "pharmacology": row.get("pharmacology", ""),
                "potency_type": row.get("potency_type", ""),
                "potency_value": row.get("potency_value", ""),
                "potency_unit": row.get("potency_unit", ""),
                "evidence_url": row.get("source_urls") or row.get("potency_uri", ""),
                "target_source": row.get("target_source", ""),
                "target_chembl_id": row.get("target_chembl_id", ""),
                "chembl_target_type": row.get("chembl_target_type", ""),
                "chembl_lookup_status": row.get("chembl_lookup_status", ""),
                "target_mapping_status": mapping_status,
                "stitcher_id": row.get("stitcher_id", ""),
            })
        rows = self.collapse_drugcentral_rows(rows)
        return sorted(
            rows,
            key=lambda row: (
                row.get("drug_name", ""),
                row.get("unii", ""),
                row.get("target_uniprot_id", ""),
                row.get("relationship_type", ""),
            ),
        )

    @staticmethod
    def collapse_drugcentral_rows(rows: list[dict[str, str]]) -> list[dict[str, str]]:
        key_cols = [
            "unii",
            "drug_name",
            "drug_url",
            "target_uniprot_id",
            "relationship_type",
            "pharmacology",
            "potency_type",
            "potency_value",
            "potency_unit",
            "evidence_url",
        ]
        merged: dict[tuple[str, ...], dict[str, str]] = {}
        for row in rows:
            key = tuple(row.get(col, "") for col in key_cols)
            if key not in merged:
                merged[key] = row.copy()
                continue
            existing = merged[key]
            for col in DRUGCENTRAL_CANDIDATE_COLUMNS:
                if col in key_cols:
                    continue
                existing[col] = join_unique([existing.get(col, ""), row.get(col, "")])
        return list(merged.values())

    def drug_name(self, unii: str, substance: dict[str, Any]) -> str:
        cached = self.cached_drug_name(unii)
        if cached is not None:
            return cached

        name = self.select_substance_name(substance.get("names") or [])
        if name:
            self.write_drug_name_cache(unii, name, "substance_detail_cache", "")
            return name

        if not self.fetch_drug_names:
            return clean(substance.get("_name"))

        last_error = ""
        for suffix, source in [
            ("(displayName:true)!(name)!limit(1)", "inxight_display_name"),
            ("(preferred:true)!(name)!limit(1)", "inxight_preferred_name"),
            ("(type:of)!(name)!limit(1)", "inxight_official_name"),
            ("(type:cn)!(name)!limit(1)", "inxight_common_name"),
            ("!(name)!limit(1)", "inxight_first_name"),
        ]:
            try:
                name = self.projected_drug_name(unii, suffix)
            except Exception as exc:
                last_error = str(exc)
                continue
            if name:
                self.write_drug_name_cache(unii, name, source, "success")
                return name

        self.write_drug_name_cache(unii, "", "inxight_names_endpoint", last_error or "no_name_returned")
        return clean(substance.get("_name"))

    def projected_drug_name(self, unii: str, suffix: str) -> str:
        url = f"{self.base_url}/api/v1/substances({unii})/names{suffix}"
        data = self.fetch_json(
            url,
            nonretryable_statuses={400, 404},
            timeout=self.name_timeout,
            max_attempts=self.name_max_attempts,
        )
        if isinstance(data, list):
            for value in data:
                name = clean(value)
                if name:
                    return name
        return clean(data)

    def cached_drug_name(self, unii: str) -> str | None:
        cache_file = self.drug_name_cache_dir / f"{unii}.json"
        if not cache_file.exists() or self.force_refresh:
            return None
        data = read_json(cache_file)
        if isinstance(data, dict):
            name = clean(data.get("drug_name"))
            if name and clean(data.get("status")).lower() == "success":
                return name
        return None

    def write_drug_name_cache(self, unii: str, name: str, source: str, status_or_error: str) -> None:
        write_json(
            self.drug_name_cache_dir / f"{unii}.json",
            {
                "unii": unii,
                "drug_name": name,
                "source": source,
                "status": "success" if name else "failed",
                "detail": status_or_error,
                "fetched_at": now_utc(),
            },
        )

    @staticmethod
    def select_substance_name(names: list[Any]) -> str:
        name_objects = [item for item in names if isinstance(item, dict) and clean(item.get("name"))]
        string_names = [clean(item) for item in names if not isinstance(item, dict) and clean(item)]
        if not name_objects:
            return string_names[0] if string_names else ""

        active = [
            item
            for item in name_objects
            if clean(item.get("deprecated")).lower() not in {"true", "1", "yes"}
        ]
        candidates = active or name_objects
        priority_checks = [
            lambda item: clean(item.get("displayName")).lower() in {"true", "1", "yes"} or item.get("displayName") is True,
            lambda item: clean(item.get("type")).lower() == "of",
            lambda item: clean(item.get("type")).lower() == "cn",
            lambda item: clean(item.get("preferred")).lower() in {"true", "1", "yes"} or item.get("preferred") is True,
            lambda item: True,
        ]
        for check in priority_checks:
            for item in candidates:
                if check(item):
                    return clean(item.get("name"))
        return ""

    def flat_target_row(
        self,
        *,
        unii: str,
        drug_source_id: str,
        drug_label: str,
        substance: dict[str, Any],
        substance_url: str,
        value: dict[str, Any],
        target_facets: dict[str, int],
    ) -> dict[str, str]:
        raw_target_id = clean(value.get("id"))
        target_label = clean(value.get("label"))
        target_source = clean(value.get("type"))
        chembl_id = extract_chembl_id(raw_target_id)
        chembl_info = self.chembl_target(chembl_id) if chembl_id and self.enable_chembl else {}
        direct_accessions = direct_uniprot_ids(raw_target_id) if target_source.lower() == "uniprot" else []
        chembl_accessions = self.chembl_uniprot_ids(chembl_info)
        uniprot_ids = direct_accessions or chembl_accessions
        target_id, target_category = self.canonical_target_id(
            chembl_id=chembl_id,
            direct_uniprot_ids=direct_accessions,
            chembl_uniprot_ids=chembl_accessions,
            chembl_info=chembl_info,
            target_label=target_label,
        )
        source_urls = join_unique(value.get("uri") or [])
        conditions = value.get("conditions") or []
        condition_labels = join_unique(condition_label(item) for item in conditions)
        component_count = len(chembl_info.get("target_components") or []) if chembl_info else 0
        substance_lookup_status = self.substance_lookup_status(substance)
        substance_deprecated = (
            str(bool(substance.get("deprecated"))).upper()
            if substance_lookup_status == "success"
            else ""
        )

        return {
            "unii": unii,
            "drug_source_id": drug_source_id,
            "drug_name": drug_label,
            "substance_uuid": clean(substance.get("uuid")),
            "substance_class": clean(substance.get("substanceClass")),
            "substance_status": clean(substance.get("status")),
            "substance_deprecated": substance_deprecated,
            "substance_url": substance_url,
            "substance_lookup_status": substance_lookup_status,
            "substance_lookup_error": clean(substance.get("_lookup_error")),
            "raw_target_id": raw_target_id,
            "target_label": target_label,
            "target_id": target_id,
            "target_source": target_source,
            "target_chembl_id": chembl_id,
            "target_uniprot_id": join_unique(uniprot_ids),
            "target_gene_symbol": clean(value.get("geneSymbol")),
            "target_gene_id": normalize_gene_id(value.get("geneid")),
            "target_organism": clean(value.get("organism")),
            "target_category": target_category,
            "chembl_lookup_status": self.chembl_lookup_status(chembl_id, chembl_info),
            "chembl_lookup_entity_type": clean(
                chembl_info.get("lookup_entity_type")
                or ("TARGET" if chembl_info.get("target_chembl_id") else "")
            ),
            "chembl_last_active": clean(chembl_info.get("lookup_last_active")),
            "chembl_resource_url": clean(chembl_info.get("lookup_resource_url")),
            "chembl_target_type": clean(chembl_info.get("target_type")),
            "chembl_target_name": clean(chembl_info.get("pref_name")),
            "chembl_target_organism": clean(chembl_info.get("organism")),
            "chembl_tax_id": clean(chembl_info.get("tax_id")),
            "chembl_component_count": str(component_count) if component_count else "",
            "chembl_lookup_error": clean(chembl_info.get("lookup_error")),
            "pharmacology": clean(value.get("pharmacology")),
            "potency_type": clean(value.get("potencyType")),
            "potency_value": clean(value.get("potencyValue")),
            "potency_unit": clean(value.get("potencyDimensions")),
            "potency_uri": clean(value.get("potencyUri")),
            "source_urls": source_urls,
            "condition_labels": condition_labels,
            "stitcher_id": clean(value.get("StitcherId")),
            "target_facet_count": str(target_facets.get(target_label, "")),
        }

    @staticmethod
    def edge_row(row: dict[str, str]) -> dict[str, str]:
        predicate = (
            "biolink:physically_interacts_with"
            if row.get("target_category") == "biolink:Protein"
            else "biolink:affects"
        )
        return {
            "source": "Inxight",
            "drug_source_id": row.get("drug_source_id", ""),
            "drug_label": row.get("drug_name", ""),
            "predicate": predicate,
            "target_id": row.get("target_id", ""),
            "target_label": row.get("target_label", ""),
            "target_category": row.get("target_category", ""),
            "target_symbol": row.get("target_gene_symbol", ""),
            "target_uniprot_id": row.get("target_uniprot_id", ""),
            "target_gene_id": row.get("target_gene_id", ""),
            "activity_type": join_unique([row.get("pharmacology"), row.get("potency_type")], sep="; "),
            "activity_value": row.get("potency_value", ""),
            "activity_unit": row.get("potency_unit", ""),
            "mechanism_of_action": row.get("pharmacology", ""),
            "action_type": row.get("pharmacology", ""),
            "evidence_source": "NCATS Inxight Drugs Activity",
            "source_url": row.get("source_urls", "") or row.get("potency_uri", ""),
        }

    def substance(self, unii: str) -> dict[str, Any]:
        cache_file = self.substance_cache_dir / f"{unii}.json"
        if cache_file.exists() and not self.force_refresh:
            data = read_json(cache_file)
            if isinstance(data, dict) and data:
                return data
        try:
            data = self.fetch_json(f"{self.base_url}/api/v1/substances({unii})", params={"view": "key"})
        except Exception as exc:
            LOGGER.warning("Inxight substance metadata lookup failed for UNII %s: %s", unii, exc)
            data = {"_lookup_status": "failed", "_lookup_error": str(exc)}
        if isinstance(data, dict) and data:
            data.setdefault("_lookup_status", "success")
        write_json(cache_file, data if isinstance(data, dict) else {})
        return data if isinstance(data, dict) else {}

    def additional(self, unii: str) -> list[dict[str, Any]]:
        cache_file = self.additional_cache_dir / f"{unii}.json"
        failure_cache_file = self.additional_failure_cache_dir / f"{unii}.json"
        if cache_file.exists() and not self.force_refresh:
            data = read_json(cache_file)
            if isinstance(data, list):
                return data
        if failure_cache_file.exists() and not self.force_refresh:
            return []

        url = f"{self.base_url}/api/v1/substances({unii})/@additional"
        try:
            data = self.fetch_json(url, nonretryable_statuses=self.additional_nonretryable_statuses)
        except Exception as exc:
            LOGGER.warning("Inxight @additional lookup failed for UNII %s: %s", unii, exc)
            write_json(
                failure_cache_file,
                {"unii": unii, "url": url, "error": str(exc), "fetched_at": now_utc()},
            )
            return []
        write_compact_json(cache_file, data if isinstance(data, list) else [])
        return data if isinstance(data, list) else []

    def chembl_target(self, chembl_id: str) -> dict[str, Any]:
        if not chembl_id:
            return {}
        cache_file = self.chembl_cache_dir / f"{chembl_id}.json"
        if cache_file.exists() and not self.force_refresh:
            data = read_json(cache_file)
            if isinstance(data, dict) and data:
                if self.needs_chembl_id_lookup(data):
                    data = self.missing_chembl_target_record(chembl_id, clean(data.get("lookup_error")))
                    write_json(cache_file, data)
                return data
        try:
            data = self.fetch_json(self.chembl_target_url.format(chembl_id=chembl_id))
            if isinstance(data, dict):
                data.setdefault("lookup_status", "ACTIVE")
                data.setdefault("lookup_entity_type", "TARGET")
        except NonRetryableRequestError as exc:
            data = self.missing_chembl_target_record(chembl_id, str(exc))
            status = clean(data.get("lookup_status"))
            if status.upper() == "INACTIVE":
                LOGGER.info("ChEMBL target %s is inactive; retaining original assertion", chembl_id)
            else:
                LOGGER.warning("ChEMBL target lookup skipped for %s: %s", chembl_id, exc)
        except Exception as exc:
            LOGGER.warning("ChEMBL target lookup failed for %s: %s", chembl_id, exc)
            data = {"target_chembl_id": chembl_id, "lookup_status": "failed", "lookup_error": str(exc)}
        write_json(cache_file, data if isinstance(data, dict) else {})
        return data if isinstance(data, dict) else {}

    def missing_chembl_target_record(self, chembl_id: str, target_error: str = "") -> dict[str, str]:
        lookup = self.chembl_id_lookup(chembl_id)
        status = clean(lookup.get("status")) or "not_found"
        lookup_error = clean(lookup.get("lookup_error"))
        if not lookup_error and status.upper() != "INACTIVE":
            lookup_error = clean(target_error)
        return {
            "target_chembl_id": chembl_id,
            "lookup_status": status,
            "lookup_entity_type": clean(lookup.get("entity_type")),
            "lookup_last_active": clean(lookup.get("last_active")),
            "lookup_resource_url": clean(lookup.get("resource_url")),
            "lookup_error": lookup_error,
        }

    def chembl_id_lookup(self, chembl_id: str) -> dict[str, Any]:
        if not chembl_id:
            return {}
        cache_file = self.chembl_lookup_cache_dir / f"{chembl_id}.json"
        if cache_file.exists() and not self.force_refresh:
            data = read_json(cache_file)
            if isinstance(data, dict) and data:
                return data
        try:
            data = self.fetch_json(self.chembl_lookup_url.format(chembl_id=chembl_id))
        except NonRetryableRequestError as exc:
            data = {"chembl_id": chembl_id, "status": "not_found", "lookup_error": str(exc)}
        except Exception as exc:
            LOGGER.warning("ChEMBL ID lookup failed for %s: %s", chembl_id, exc)
            data = {"chembl_id": chembl_id, "status": "lookup_failed", "lookup_error": str(exc)}
        write_json(cache_file, data if isinstance(data, dict) else {})
        return data if isinstance(data, dict) else {}

    @staticmethod
    def chembl_uniprot_ids(chembl_info: dict[str, Any]) -> list[str]:
        components = chembl_info.get("target_components") or []
        accessions: list[str] = []
        for comp in components:
            accession = clean(comp.get("accession"))
            component_type = clean(comp.get("component_type")).upper()
            if component_type and component_type != "PROTEIN":
                continue
            if accession and UNIPROT_ACCESSION_RE.match(accession) and accession not in accessions:
                accessions.append(accession)
        return accessions

    @staticmethod
    def canonical_target_id(
        *,
        chembl_id: str,
        direct_uniprot_ids: list[str],
        chembl_uniprot_ids: list[str],
        chembl_info: dict[str, Any],
        target_label: str,
    ) -> tuple[str, str]:
        if len(direct_uniprot_ids) == 1:
            return f"UniProtKB:{direct_uniprot_ids[0]}", "biolink:Protein"
        if len(direct_uniprot_ids) > 1:
            digest = hashlib.sha1((target_label + "|" + "|".join(direct_uniprot_ids)).encode("utf-8")).hexdigest()[:12]
            return f"INXIGHT.TARGET:{digest}", "biolink:MacromolecularComplex"

        target_type = clean(chembl_info.get("target_type")).upper()
        organism = clean(chembl_info.get("organism")).lower()
        if len(chembl_uniprot_ids) == 1 and organism == "homo sapiens" and target_type == "SINGLE PROTEIN":
            return f"UniProtKB:{chembl_uniprot_ids[0]}", "biolink:Protein"
        if chembl_id:
            category = "biolink:MacromolecularComplex" if "COMPLEX" in target_type else "biolink:NamedThing"
            return f"CHEMBL.TARGET:{chembl_id}", category
        digest = hashlib.sha1(target_label.encode("utf-8")).hexdigest()[:12]
        return f"INXIGHT.TARGET:{digest}", "biolink:NamedThing"

    @staticmethod
    def chembl_lookup_status(chembl_id: str, chembl_info: dict[str, Any]) -> str:
        if not chembl_id:
            return ""
        return clean(chembl_info.get("lookup_status") or ("ACTIVE" if chembl_info.get("target_type") else ""))

    @staticmethod
    def needs_chembl_id_lookup(data: dict[str, Any]) -> bool:
        status = clean(data.get("lookup_status")).lower()
        return status in {"not_found", "target_not_found"} and not clean(data.get("lookup_entity_type"))

    @staticmethod
    def substance_lookup_status(substance: dict[str, Any]) -> str:
        status = clean(substance.get("_lookup_status"))
        if status:
            return status
        return "success" if substance else ""

    def fetch_json(
        self,
        url: str,
        params: dict[str, Any] | None = None,
        nonretryable_statuses: set[int] | None = None,
        timeout: int | None = None,
        max_attempts: int | None = None,
    ) -> Any:
        last_exc: Exception | None = None
        headers = {"User-Agent": "InxightActivityTargetExport/1.0 (+https://drugs.ncats.io)"}
        statuses = nonretryable_statuses or {400, 404}
        attempts = max_attempts or self.max_attempts
        request_timeout = timeout or self.timeout
        for attempt in range(1, attempts + 1):
            try:
                resp = requests.get(url, params=params, timeout=request_timeout, headers=headers)
                if resp.status_code in statuses:
                    raise NonRetryableRequestError(f"{resp.status_code} Client Error for url: {resp.url}")
                resp.raise_for_status()
                if self.sleep_s:
                    time.sleep(self.sleep_s)
                return resp.json()
            except NonRetryableRequestError:
                raise
            except Exception as exc:
                last_exc = exc
                LOGGER.warning("Request failed on attempt %s/%s for %s: %s", attempt, attempts, url, exc)
                if attempt < attempts:
                    time.sleep(min(60.0, 2.0 ** (attempt - 1)))
        raise RuntimeError(f"Request failed after {attempts} attempts for {url}: {last_exc}")

    def base_facet_params(self) -> dict[str, Any]:
        return {"facet": self.base_facets} if self.base_facets else {}

    def facet_context_key(self) -> str:
        return cache_key(json.dumps(sorted(self.base_facets), separators=(",", ":")))

    def write_candidate_index(self, candidate_index: dict[str, dict[str, Any]], uniis: list[str]) -> None:
        selected = set(uniis)
        rows = []
        for unii, data in sorted(candidate_index.items()):
            if unii not in selected:
                continue
            facets = data.get("target_facets", {})
            rows.append({
                "unii": unii,
                "target_facet_count": str(len(facets)),
                "target_facets": join_unique(sorted(facets)),
            })
        write_tsv(self.candidate_index_file, rows, ["unii", "target_facet_count", "target_facets"])

    def write_additional_failures(self, uniis: list[str]) -> int:
        selected = set(uniis)
        rows = []
        if self.additional_failure_cache_dir.exists():
            for cache_file in sorted(self.additional_failure_cache_dir.glob("*.json")):
                data = read_json(cache_file)
                data = data if isinstance(data, dict) else {}
                unii = clean(data.get("unii")) or cache_file.stem
                if selected and unii not in selected:
                    continue
                rows.append({
                    "unii": unii,
                    "url": clean(data.get("url")),
                    "error": clean(data.get("error")),
                    "fetched_at": clean(data.get("fetched_at")),
                })
        write_tsv(self.additional_failure_file, rows, ["unii", "url", "error", "fetched_at"])
        return len(rows)

    def write_qc(
        self,
        flat_rows: list[dict[str, str]],
        edge_rows: list[dict[str, str]],
        candidate_index: dict[str, dict[str, Any]],
    ) -> dict[str, Any]:
        chembl_rows = [row for row in flat_rows if row.get("target_chembl_id")]
        target_uniprots: set[str] = set()
        invalid_uniprots = []
        for row in flat_rows:
            for part in (row.get("target_uniprot_id") or "").split("|"):
                if not part:
                    continue
                if UNIPROT_ACCESSION_RE.match(part):
                    target_uniprots.add(part)
                else:
                    invalid_uniprots.append({"unii": row.get("unii"), "target_uniprot_id": part})

        failure_uniis = set()
        if self.additional_failure_file.exists():
            with open(self.additional_failure_file, encoding="utf-8", newline="") as fh:
                failure_uniis = {row["unii"] for row in csv.DictReader(fh, delimiter="\t") if row.get("unii")}

        unique_uniis_with_rows = {row["unii"] for row in flat_rows if row.get("unii")}
        candidate_uniis = set(candidate_index)
        direct_uniprot_rows = [row for row in flat_rows if row.get("target_source", "").lower() == "uniprot"]
        direct_single_uniprot_rows = [
            row for row in direct_uniprot_rows if row.get("target_uniprot_id") and "|" not in row.get("target_uniprot_id", "")
        ]
        direct_multi_uniprot_rows = [
            row for row in direct_uniprot_rows if "|" in row.get("target_uniprot_id", "")
        ]
        drugcentral_rows = self.drugcentral_candidate_rows(flat_rows)
        flat_output_rows = [
            {col: tsv_cell(row.get(col, "")) for col in ACTIVITY_TARGET_COLUMNS}
            for row in flat_rows
        ]
        edge_output_rows = [
            {col: tsv_cell(row.get(col, "")) for col in EDGE_COLUMNS}
            for row in edge_rows
        ]
        drugcentral_output_rows = [
            {col: tsv_cell(row.get(col, "")) for col in DRUGCENTRAL_CANDIDATE_COLUMNS}
            for row in drugcentral_rows
        ]
        row_key_cols = ["unii", "target_id", "target_label", "pharmacology", "potency_type", "potency_value", "potency_unit", "source_urls"]
        edge_key_cols = ["drug_source_id", "target_id", "target_label", "activity_type", "activity_value", "activity_unit", "source_url"]
        drugcentral_key_cols = [
            "unii",
            "target_uniprot_id",
            "relationship_type",
            "potency_type",
            "potency_value",
            "potency_unit",
            "evidence_url",
        ]
        row_keys = Counter(tuple(row.get(col, "") for col in row_key_cols) for row in flat_rows)
        edge_keys = Counter(tuple(row.get(col, "") for col in edge_key_cols) for row in edge_rows)
        drugcentral_keys = Counter(tuple(row.get(col, "") for col in drugcentral_key_cols) for row in drugcentral_rows)
        label_to_ids: dict[str, set[str]] = {}
        id_to_labels: dict[str, set[str]] = {}
        for row in flat_rows:
            label = row.get("target_label", "")
            target_id = row.get("target_id", "")
            if label and target_id:
                label_to_ids.setdefault(label, set()).add(target_id)
                id_to_labels.setdefault(target_id, set()).add(label)

        completeness_check: bool | str
        if self.args.limit_substances:
            completeness_check = "not_evaluated_for_limited_substance_run"
        else:
            completeness_check = candidate_uniis - unique_uniis_with_rows == failure_uniis

        qc = {
            "counts": {
                "activity_target_records": len(flat_rows),
                "target_edge_records": len(edge_rows),
                "drugcentral_candidate_records": len(drugcentral_rows),
                "candidate_uniis": len(candidate_uniis),
                "unique_uniis_with_rows": len(unique_uniis_with_rows),
                "candidate_uniis_without_rows": len(candidate_uniis - unique_uniis_with_rows),
                "rows_with_resolved_drug_name": sum(
                    1 for row in flat_rows if row.get("drug_name") and row.get("drug_name") != row.get("unii")
                ),
                "raw_rows_with_drug_name_fallback_to_unii": sum(
                    1 for row in flat_rows if row.get("drug_name") == row.get("unii")
                ),
                "unique_drugcentral_candidate_uniis": len({row.get("unii") for row in drugcentral_rows if row.get("unii")}),
                "unique_drugcentral_candidate_uniprots": len(
                    {row.get("target_uniprot_id") for row in drugcentral_rows if row.get("target_uniprot_id")}
                ),
                "unique_drug_target_pairs": len({(row.get("unii"), row.get("target_id")) for row in flat_rows}),
                "unique_drug_target_action_pairs": len({(row.get("unii"), row.get("target_id"), row.get("pharmacology")) for row in flat_rows}),
                "unique_target_ids": len({row.get("target_id") for row in flat_rows if row.get("target_id")}),
                "unique_target_labels": len({row.get("target_label") for row in flat_rows if row.get("target_label")}),
                "unique_chembl_targets": len({row.get("target_chembl_id") for row in chembl_rows if row.get("target_chembl_id")}),
                "unique_uniprot_accessions": len(target_uniprots),
                "direct_uniprot_rows": len(direct_uniprot_rows),
                "direct_single_uniprot_rows": len(direct_single_uniprot_rows),
                "direct_multi_uniprot_rows": len(direct_multi_uniprot_rows),
                "chembl_single_human_protein_rows_promoted_to_uniprotkb": sum(
                    1
                    for row in chembl_rows
                    if row.get("target_id", "").startswith("UniProtKB:")
                    and row.get("chembl_target_type") == "SINGLE PROTEIN"
                    and row.get("chembl_target_organism") == "Homo sapiens"
                ),
                "rows_with_any_uniprot_id": sum(1 for row in flat_rows if row.get("target_uniprot_id")),
                "rows_with_target_id_uniprotkb": sum(1 for row in flat_rows if row.get("target_id", "").startswith("UniProtKB:")),
                "rows_with_target_id_chembl_target": sum(1 for row in flat_rows if row.get("target_id", "").startswith("CHEMBL.TARGET:")),
                "rows_with_target_id_inxight_target": sum(1 for row in flat_rows if row.get("target_id", "").startswith("INXIGHT.TARGET:")),
                "skipped_target_facets": self.count_tsv_rows(self.skipped_target_facet_file),
                "additional_failures": len(failure_uniis),
                "target_labels_mapping_to_multiple_ids": sum(1 for ids in label_to_ids.values() if len(ids) > 1),
                "target_ids_mapping_to_multiple_labels": sum(1 for labels in id_to_labels.values() if len(labels) > 1),
            },
            "validations": {
                "flat_edge_record_count_match": len(flat_rows) == len(edge_rows),
                "drugcentral_output_record_count_match": len(drugcentral_rows) == self.count_tsv_rows(self.drugcentral_candidate_file),
                "drugcentral_candidate_has_no_unii_name_fallback": all(
                    row.get("drug_name") != row.get("unii") for row in drugcentral_rows
                ),
                "drugcentral_candidate_all_uniprot_ids_valid": all(
                    bool(UNIPROT_ACCESSION_RE.match(row.get("target_uniprot_id", "")))
                    for row in drugcentral_rows
                ),
                "invalid_unii_rows": sum(1 for row in flat_rows if row.get("unii") and not UNII_RE.match(row.get("unii", ""))),
                "invalid_chembl_id_rows": sum(
                    1
                    for row in flat_rows
                    if row.get("target_chembl_id") and not CHEMBL_ID_RE.fullmatch(row.get("target_chembl_id", ""))
                ),
                "invalid_uniprot_parts": len(invalid_uniprots),
                "direct_uniprot_rows_without_uniprot_id": sum(
                    1 for row in direct_uniprot_rows if not row.get("target_uniprot_id")
                ),
                "single_uniprot_target_rows_without_uniprotkb_target_id": sum(
                    1
                    for row in direct_single_uniprot_rows
                    if not row.get("target_id", "").startswith("UniProtKB:")
                ),
                "protein_edges_without_physical_predicate": sum(
                    1
                    for row in edge_rows
                    if row.get("target_category") == "biolink:Protein"
                    and row.get("predicate") != "biolink:physically_interacts_with"
                ),
                "nonprotein_edges_with_physical_predicate": sum(
                    1
                    for row in edge_rows
                    if row.get("target_category") != "biolink:Protein"
                    and row.get("predicate") == "biolink:physically_interacts_with"
                ),
                "cells_with_embedded_newline": sum(
                    1
                    for row in [*flat_output_rows, *edge_output_rows, *drugcentral_output_rows]
                    for value in row.values()
                    if "\n" in value or "\r" in value
                ),
                "cells_with_embedded_tab": sum(
                    1
                    for row in [*flat_output_rows, *edge_output_rows, *drugcentral_output_rows]
                    for value in row.values()
                    if "\t" in value
                ),
                "rows_without_source_url_or_potency_uri": sum(
                    1 for row in flat_rows if not row.get("source_urls") and not row.get("potency_uri")
                ),
                "exact_full_duplicate_rows": len(flat_output_rows)
                - len({tuple(row.get(col, "") for col in ACTIVITY_TARGET_COLUMNS) for row in flat_output_rows}),
                "missing_required_flat_fields": {
                    col: sum(1 for row in flat_rows if not row.get(col))
                    for col in ["unii", "drug_source_id", "raw_target_id", "target_id", "target_label", "target_category"]
                },
                "missing_required_edge_fields": {
                    col: sum(1 for row in edge_rows if not row.get(col))
                    for col in ["source", "drug_source_id", "predicate", "target_id", "target_label", "target_category", "evidence_source"]
                },
                "missing_required_drugcentral_candidate_fields": {
                    col: sum(1 for row in drugcentral_rows if not row.get(col))
                    for col in ["unii", "drug_name", "drug_url", "target_uniprot_id", "target_label"]
                },
                "candidate_missing_equals_additional_failures": completeness_check,
                "duplicate_flat_relationship_evidence_keys": sum(count - 1 for count in row_keys.values() if count > 1),
                "duplicate_edge_relationship_evidence_keys": sum(count - 1 for count in edge_keys.values() if count > 1),
                "duplicate_drugcentral_relationship_evidence_keys": sum(
                    count - 1 for count in drugcentral_keys.values() if count > 1
                ),
            },
            "distributions": {
                "target_category": dict(Counter(row.get("target_category") or "blank" for row in flat_rows).most_common()),
                "target_source": dict(Counter(row.get("target_source") or "blank" for row in flat_rows).most_common()),
                "chembl_lookup_status": dict(Counter(row.get("chembl_lookup_status") or "blank" for row in chembl_rows).most_common()),
                "chembl_target_type": dict(Counter(row.get("chembl_target_type") or "blank" for row in chembl_rows).most_common()),
                "pharmacology": dict(Counter(row.get("pharmacology") or "blank" for row in flat_rows).most_common()),
                "potency_type": dict(Counter(row.get("potency_type") or "blank" for row in flat_rows).most_common()),
                "potency_unit": dict(Counter(row.get("potency_unit") or "blank" for row in flat_rows).most_common()),
            },
            "examples": {
                "invalid_uniprots": invalid_uniprots[:20],
            },
            "files": {
                "activity_targets": file_stats(self.output_file),
                "target_edges": file_stats(self.edge_output_file),
                "drugcentral_candidate": file_stats(self.drugcentral_candidate_file),
                "candidate_index": file_stats(self.candidate_index_file),
                "skipped_target_facets": file_stats(self.skipped_target_facet_file),
                "additional_failures": file_stats(self.additional_failure_file),
            },
        }
        write_json(self.qc_file, qc)
        self.write_qc_markdown(qc)
        return qc

    def write_qc_markdown(self, qc: dict[str, Any]) -> None:
        counts = qc["counts"]
        validations = qc["validations"]
        categories = qc["distributions"]["target_category"]
        chembl_status = qc["distributions"]["chembl_lookup_status"]
        total = counts["activity_target_records"] or 1
        lines = [
            "# Inxight Activity Targets QC",
            f"Generated by `export_inxight_activity_targets_standalone.py` from `{self.output_file}` and `{self.edge_output_file}`.",
            "",
            "## Summary",
        ]
        for key in [
            "activity_target_records",
            "target_edge_records",
            "drugcentral_candidate_records",
            "candidate_uniis",
            "unique_uniis_with_rows",
            "candidate_uniis_without_rows",
            "rows_with_resolved_drug_name",
            "raw_rows_with_drug_name_fallback_to_unii",
            "unique_drugcentral_candidate_uniis",
            "unique_drugcentral_candidate_uniprots",
            "unique_drug_target_pairs",
            "unique_target_ids",
            "unique_target_labels",
            "unique_chembl_targets",
            "unique_uniprot_accessions",
        ]:
            lines.append(f"- {key}: {counts[key]:,}")
        lines.extend(["", "## Target ID Assignment"])
        for key in [
            "rows_with_target_id_uniprotkb",
            "rows_with_target_id_chembl_target",
            "rows_with_target_id_inxight_target",
            "rows_with_any_uniprot_id",
            "direct_uniprot_rows",
            "direct_single_uniprot_rows",
            "direct_multi_uniprot_rows",
            "chembl_single_human_protein_rows_promoted_to_uniprotkb",
        ]:
            lines.append(f"- {key}: {counts[key]:,}")
        lines.extend(["", "## Target Categories"])
        for label, value in categories.items():
            lines.append(f"- {label}: {value:,} ({value / total:.1%})")
        lines.extend(["", "## ChEMBL Resolution"])
        for label, value in chembl_status.items():
            lines.append(f"- {label}: {value:,} ChEMBL-backed rows")
        lines.extend(["", "## Validations"])
        for key, value in validations.items():
            lines.append(f"- {key}: {value}")
        lines.extend([
            "",
            "## Caveats",
            "- The DrugCentral candidate TSV omits raw rows without a resolved drug name or clean UniProtKB protein target.",
            "- Full substance display details are optional and disabled by default; drug names are fetched through the lightweight Inxight names endpoint.",
            "- Candidate UNIIs without rows should match the additional-failure audit for full, unlimited runs.",
            "- Skipped target facets are retained as an audit file when the Inxight reverse-facet API returns malformed/400 responses.",
            "- ChEMBL targets are promoted to UniProtKB only for clean human SINGLE PROTEIN targets.",
            "- Do not collapse targets by label alone; the same display label can occur as UniProt, ChEMBL, and source-local assertions.",
        ])
        ensure_parent(self.qc_md_file).write_text("\n".join(lines) + "\n", encoding="utf-8")

    @staticmethod
    def count_tsv_rows(path: Path) -> int:
        if not path.exists():
            return 0
        with open(path, encoding="utf-8", newline="") as fh:
            return max(0, sum(1 for _ in csv.DictReader(fh, delimiter="\t")))


def parse_statuses(value: str) -> list[int]:
    statuses = []
    for part in value.split(","):
        text = part.strip()
        if text:
            statuses.append(int(text))
    return statuses


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Export NCATS Inxight Activity -> Targets annotations")
    parser.add_argument("--base-url", default="https://drugs.ncats.io")
    parser.add_argument("--chembl-target-url", default="https://www.ebi.ac.uk/chembl/api/data/target/{chembl_id}.json")
    parser.add_argument("--chembl-lookup-url", default="https://www.ebi.ac.uk/chembl/api/data/chembl_id_lookup/{chembl_id}.json")
    parser.add_argument("--cache-dir", default="inxight_activity_targets_cache")
    parser.add_argument("--output-file", default="inxight_activity_targets.tsv")
    parser.add_argument("--edge-output-file", default="inxight_activity_target_edges.tsv")
    parser.add_argument("--drugcentral-candidate-file", default="inxight_activity_targets_drugcentral_candidate.tsv")
    parser.add_argument("--metadata-file", default="inxight_activity_targets_metadata.json")
    parser.add_argument("--qc-file", default="inxight_activity_targets_qc.json")
    parser.add_argument("--qc-md-file", default="inxight_activity_targets_qc.md")
    parser.add_argument("--candidate-index-file", default="inxight_primary_target_unii_index.tsv")
    parser.add_argument("--skipped-target-facet-file", default="inxight_skipped_target_facets.tsv")
    parser.add_argument("--additional-failure-file", default="inxight_additional_failures.tsv")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--request-timeout", type=int, default=60)
    parser.add_argument("--name-request-timeout", type=int, default=10)
    parser.add_argument("--max-attempts", type=int, default=4)
    parser.add_argument("--name-max-attempts", type=int, default=2)
    parser.add_argument("--sleep-seconds", type=float, default=0.05)
    parser.add_argument("--facet-page-size", type=int, default=5000)
    parser.add_argument("--unii-facet-page-size", type=int, default=1000)
    parser.add_argument("--limit-targets", type=int, default=0)
    parser.add_argument("--limit-substances", type=int, default=0)
    parser.add_argument("--base-facet", action="append", default=[], help="Optional Inxight facet filter, repeatable")
    parser.add_argument("--skip-chembl", action="store_true", help="Skip ChEMBL target/UniProt enrichment")
    parser.add_argument("--skip-drug-names", action="store_true", help="Do not call the Inxight /names endpoint")
    parser.add_argument("--fetch-substance-details", action="store_true", help="Fetch Inxight substance display metadata")
    parser.add_argument("--force-refresh", action="store_true", help="Ignore existing API response cache")
    parser.add_argument(
        "--additional-nonretryable-statuses",
        type=parse_statuses,
        default=[400, 404, 500],
        help="Comma-separated HTTP statuses to cache as failed for @additional, default: 400,404,500",
    )
    parser.add_argument("--log-file", default="", help="Optional log file")
    return parser


def setup_logging(log_file: str = "") -> None:
    handlers: list[logging.Handler] = [logging.StreamHandler()]
    if log_file:
        ensure_parent(log_file)
        handlers.insert(0, logging.FileHandler(log_file, mode="a", encoding="utf-8"))
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(levelname)s - %(message)s",
        handlers=handlers,
        force=True,
    )


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    setup_logging(args.log_file)
    InxightActivityTargetExporter(args).run()


if __name__ == "__main__":
    main()
