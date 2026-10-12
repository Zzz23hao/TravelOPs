from fastapi import APIRouter, Depends, File, UploadFile

from app.errors import ApiError
from app.models.import_job import ImportSummary
from app.services.flight_parser import CsvFormatError, parse_flights_csv
from app.storage.flight_repository import FlightRepository, get_repository

router = APIRouter(prefix="/api/data/flights")

MAX_UPLOAD_BYTES = 10 * 1024 * 1024


# A plain def, not async def: the database driver blocks, and FastAPI runs sync endpoints in a
# thread pool so one slow import does not stall every other request on the event loop.
@router.post("/upload", status_code=201, response_model=ImportSummary)
def upload_flights(
    file: UploadFile = File(...),
    repository: FlightRepository = Depends(get_repository),
) -> ImportSummary:
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
