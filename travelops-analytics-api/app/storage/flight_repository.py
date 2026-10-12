import logging
from pathlib import Path

import pandas as pd
import psycopg

from app.config import database_url
from app.services.flight_parser import COLUMNS, ParseResult

logger = logging.getLogger(__name__)

_SCHEMA_SQL = (Path(__file__).parent / "schema.sql").read_text()

_UPSERT_AIRPORT = """
    INSERT INTO analytics.airports (airport_code, city, country)
    VALUES (%(code)s, %(city)s, %(country)s)
    ON CONFLICT (airport_code) DO UPDATE SET city = EXCLUDED.city, country = EXCLUDED.country
"""

# Re-uploading a file updates flights by flight_id instead of duplicating them.
_UPSERT_FLIGHT = f"""
    INSERT INTO analytics.flights ({", ".join(COLUMNS)}, import_job_id)
    VALUES ({", ".join(f"%({c})s" for c in COLUMNS)}, %(import_job_id)s)
    ON CONFLICT (flight_id) DO UPDATE SET
        {", ".join(f"{c} = EXCLUDED.{c}" for c in COLUMNS if c != "flight_id")},
        import_job_id = EXCLUDED.import_job_id
"""


class FlightRepository:
    """Postgres access for the analytics schema. One short-lived connection per call."""

    def __init__(self, dsn: str):
        self._dsn = dsn

    def init_schema(self) -> None:
        with psycopg.connect(self._dsn) as conn:
            conn.execute(_SCHEMA_SQL)

    def save_import(self, filename: str, result: ParseResult) -> int:
        """Records the job, then stores its valid flights. Returns the job id.

        The job row is committed first, on its own, so a failure while writing flights still
        leaves a 'failed' job behind instead of no trace of the upload.
        """
        with psycopg.connect(self._dsn) as conn:
            job_id = conn.execute(
                "INSERT INTO analytics.data_import_jobs (filename, status) VALUES (%s, 'processing') RETURNING id",
                (filename,),
            ).fetchone()[0]

        try:
            with psycopg.connect(self._dsn) as conn, conn.transaction():
                with conn.cursor() as cur:
                    cur.executemany(_UPSERT_AIRPORT, _airports(result.valid))
                    rows = result.valid.to_dict("records")
                    cur.executemany(_UPSERT_FLIGHT, [{**row, "import_job_id": job_id} for row in rows])
                conn.execute(
                    """UPDATE analytics.data_import_jobs
                       SET status = 'completed', total_records = %s, valid_records = %s,
                           invalid_records = %s, completed_at = now()
                       WHERE id = %s""",
                    (result.total_records, len(result.valid), len(result.invalid), job_id),
                )
        except Exception:
            logger.exception("Import job %s failed", job_id)
            with psycopg.connect(self._dsn) as conn:
                conn.execute(
                    "UPDATE analytics.data_import_jobs SET status = 'failed', completed_at = now() WHERE id = %s",
                    (job_id,),
                )
            raise
        return job_id

    def load_flights(self) -> pd.DataFrame:
        with psycopg.connect(self._dsn) as conn:
            rows = conn.execute(f"SELECT {', '.join(COLUMNS)} FROM analytics.flights ORDER BY flight_id").fetchall()
        frame = pd.DataFrame(rows, columns=COLUMNS)
        # NUMERIC columns arrive as Decimal; the analytics code works in floats.
        frame["distance_km"] = frame["distance_km"].astype(float)
        frame["price"] = frame["price"].astype(float)
        frame["duration_minutes"] = frame["duration_minutes"].astype(int)
        return frame


def _airports(flights: pd.DataFrame) -> list[dict]:
    """Every airport that appears as a source or a destination, once each."""
    ends = [
        flights[["source_airport", "source_city", "source_country"]].set_axis(["code", "city", "country"], axis=1),
        flights[["destination_airport", "destination_city", "destination_country"]].set_axis(
            ["code", "city", "country"], axis=1
        ),
    ]
    return pd.concat(ends).drop_duplicates(subset="code", keep="last").to_dict("records")


def get_repository() -> FlightRepository:
    """FastAPI dependency; tests replace it with an in-memory fake through dependency_overrides."""
    return FlightRepository(database_url())
