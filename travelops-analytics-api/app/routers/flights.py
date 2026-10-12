from typing import Annotated

import pandas as pd
from fastapi import APIRouter, Depends, File, Query, UploadFile

from app.errors import ApiError
from app.models.flight import AirportCode, FlightOut
from app.models.import_job import ImportSummary
from app.services import pandas_query_service as queries
from app.services.flight_parser import CsvFormatError, parse_flights_csv
from app.storage.flight_repository import FlightRepository, get_repository

router = APIRouter(prefix="/api/data/flights")

MAX_UPLOAD_BYTES = 10 * 1024 * 1024

SortBy = Annotated[queries.SortField, Query()]
Repository = Annotated[FlightRepository, Depends(get_repository)]


# A plain def, not async def: the database driver blocks, and FastAPI runs sync endpoints in a
# thread pool so one slow import does not stall every other request on the event loop.
@router.post("/upload", status_code=201, response_model=ImportSummary)
def upload_flights(repository: Repository, file: UploadFile = File(...)) -> ImportSummary:
    # Read one byte past the limit: enough to know the file is too big without loading all of it.
    content = file.file.read(MAX_UPLOAD_BYTES + 1)
    if len(content) > MAX_UPLOAD_BYTES:
        raise ApiError(413, "FILE_TOO_LARGE", f"File exceeds {MAX_UPLOAD_BYTES // (1024 * 1024)} MB.")

    try:
        result = parse_flights_csv(content)
    except CsvFormatError as exc:
        raise ApiError(400, "INVALID_CSV", str(exc)) from exc

    filename = file.filename or "upload.csv"
    job_id = repository.save_import(filename, result)
    return ImportSummary(
        job_id=f"job_{job_id:03d}",
        filename=filename,
        total_records=result.total_records,
        valid_records=len(result.valid),
        invalid_records=len(result.invalid),
        status="completed",
    )


@router.get("", response_model=list[FlightOut])
def list_flights(repository: Repository, source: AirportCode | None = None, sort_by: SortBy = "price"):
    return _records(queries.by_source(repository.load_flights(), _upper(source), sort_by))


# from is a Python keyword, so the parameter is named from_ and exposed under the alias "from".
@router.get("/search", response_model=list[FlightOut])
def search_direct_flights(
    repository: Repository,
    from_: Annotated[str, Query(alias="from", pattern=r"^[A-Za-z]{3}$")],
    to: AirportCode,
    sort_by: SortBy = "price",
):
    return _records(queries.direct(repository.load_flights(), from_.upper(), to.upper(), sort_by))


@router.get("/domestic", response_model=list[FlightOut])
def domestic_flights(repository: Repository, sort_by: SortBy = "price"):
    return _records(queries.domestic(repository.load_flights(), sort_by))


@router.get("/international", response_model=list[FlightOut])
def international_flights(repository: Repository, sort_by: SortBy = "price"):
    return _records(queries.international(repository.load_flights(), sort_by))


def _records(flights: pd.DataFrame) -> list[dict]:
    return flights.to_dict("records")


def _upper(code: str | None) -> str | None:
    return code.upper() if code is not None else None
