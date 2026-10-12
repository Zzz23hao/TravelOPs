"""Parses an uploaded flight CSV into valid rows and rejected rows.

A bad row is counted and logged, never fatal. Only a file that cannot be read as a flight CSV
at all (not UTF-8, empty, missing columns) is rejected as a whole.
"""

import io
import logging
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

COLUMNS = [
    "flight_id",
    "airline",
    "source_airport",
    "destination_airport",
    "source_city",
    "destination_city",
    "source_country",
    "destination_country",
    "distance_km",
    "duration_minutes",
    "price",
]

# Per-upload cap on logged rejections so one broken file cannot flood the logs.
_MAX_LOGGED_REJECTIONS = 20


class CsvFormatError(ValueError):
    """The file as a whole is not a readable flight CSV."""


@dataclass
class InvalidRecord:
    flight_id: str
    reasons: list[str]


@dataclass
class ParseResult:
    valid: pd.DataFrame
    invalid: list[InvalidRecord] = field(default_factory=list)

    @property
    def total_records(self) -> int:
        return len(self.valid) + len(self.invalid)


def parse_flights_csv(content: bytes) -> ParseResult:
    try:
        # utf-8-sig also accepts the byte-order mark that Excel puts at the start of exported CSVs.
        text = content.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise CsvFormatError("File is not UTF-8 text.") from exc

    extra_field_lines: list[list[str]] = []

    def keep_bad_line(fields: list[str]) -> None:
        extra_field_lines.append(fields)
        return None  # returning None tells pandas to skip the line

    try:
        frame = pd.read_csv(
            io.StringIO(text),
            dtype=str,
            keep_default_na=False,
            # The python engine is the one that accepts a callable here; it is how a row with
            # too many fields gets counted instead of aborting the whole read.
            engine="python",
            on_bad_lines=keep_bad_line,
        )
    except pd.errors.EmptyDataError as exc:
        raise CsvFormatError("File is empty.") from exc

    missing = [c for c in COLUMNS if c not in frame.columns]
    if missing:
        raise CsvFormatError(f"Missing required columns: {', '.join(missing)}.")

    # Rows with too few fields come back with NaN in the missing columns.
    frame = frame[COLUMNS].fillna("").apply(lambda column: column.str.strip())
    frame["source_airport"] = frame["source_airport"].str.upper()
    frame["destination_airport"] = frame["destination_airport"].str.upper()

    reasons = _validate(frame)
    invalid = [
        InvalidRecord(flight_id=frame.at[i, "flight_id"], reasons=reasons[i]) for i in frame.index if reasons[i]
    ]
    invalid += [
        InvalidRecord(
            flight_id=fields[0].strip() if fields else "",
            reasons=[f"expected {len(COLUMNS)} fields, got {len(fields)}"],
        )
        for fields in extra_field_lines
    ]

    valid = frame.loc[[i for i in frame.index if not reasons[i]]].copy()
    valid["distance_km"] = valid["distance_km"].astype(float)
    valid["duration_minutes"] = valid["duration_minutes"].astype(float).astype(int)
    valid["price"] = valid["price"].astype(float)
    valid = valid.reset_index(drop=True)

    _log_rejections(invalid)
    return ParseResult(valid=valid, invalid=invalid)


def _validate(frame: pd.DataFrame) -> dict[int, list[str]]:
    """Applies each rule to the whole column at once and collects the reasons per row."""
    reasons: dict[int, list[str]] = {i: [] for i in frame.index}

    def reject(mask: pd.Series, reason: str) -> None:
        for i in frame.index[mask]:
            reasons[i].append(reason)

    source = frame["source_airport"]
    destination = frame["destination_airport"]
    distance = pd.to_numeric(frame["distance_km"], errors="coerce")
    duration = pd.to_numeric(frame["duration_minutes"], errors="coerce")
    price = pd.to_numeric(frame["price"], errors="coerce")

    reject(frame["flight_id"] == "", "flight_id is required")
    reject(source == "", "source_airport is required")
    reject(destination == "", "destination_airport is required")
    reject(~_is_positive(distance), "distance_km must be a positive number")
    # Stored as an integer column, so a fractional minute count is rejected rather than silently rounded.
    reject(~_is_positive(duration) | (duration % 1 != 0), "duration_minutes must be a positive whole number")
    reject(~_is_positive(price), "price must be a positive number")
    reject((source != "") & (source == destination), "source_airport must differ from destination_airport")
    return reasons


def _is_positive(values: pd.Series) -> pd.Series:
    # Text that is not a number became NaN in to_numeric; NaN compares False, so it is not positive.
    return np.isfinite(values) & (values > 0)


def _log_rejections(invalid: list[InvalidRecord]) -> None:
    for record in invalid[:_MAX_LOGGED_REJECTIONS]:
        logger.warning("Rejected flight record flight_id=%r: %s", record.flight_id, "; ".join(record.reasons))
    if len(invalid) > _MAX_LOGGED_REJECTIONS:
        logger.warning("%d more rejected records not logged", len(invalid) - _MAX_LOGGED_REJECTIONS)
