from pydantic import BaseModel


class ImportSummary(BaseModel):
    job_id: str
    filename: str
    total_records: int
    valid_records: int
    invalid_records: int
    status: str
