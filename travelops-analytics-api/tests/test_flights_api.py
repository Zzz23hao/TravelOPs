from app.routers import flights
from tests.conftest import SAMPLE_CSV


def upload(client, content: bytes, filename: str = "flights.csv"):
    return client.post("/api/data/flights/upload", files={"file": (filename, content, "text/csv")})


def test_upload_sample_returns_import_summary_with_invalid_records(client, repository):
    response = upload(client, SAMPLE_CSV.read_bytes(), "sample_flights.csv")

    assert response.status_code == 201
    assert response.json() == {
        "job_id": "job_001",
        "filename": "sample_flights.csv",
        "total_records": 31,
        "valid_records": 24,
        "invalid_records": 7,
        "status": "completed",
    }
    assert len(repository.flights) == 24


def test_reupload_updates_flights_instead_of_duplicating(client, repository):
    upload(client, SAMPLE_CSV.read_bytes())
    upload(client, SAMPLE_CSV.read_bytes())

    assert len(repository.flights) == 24


def test_file_missing_columns_is_400_and_stores_nothing(client, repository):
    response = upload(client, b"flight_id,price\nF001,100")

    assert response.status_code == 400
    assert response.json()["error"] == "INVALID_CSV"
    assert repository.jobs == []


def test_upload_without_file_is_400(client):
    response = client.post("/api/data/flights/upload")

    assert response.status_code == 400
    assert response.json()["error"] == "VALIDATION_FAILED"


def test_file_over_size_limit_is_413(client, monkeypatch):
    monkeypatch.setattr(flights, "MAX_UPLOAD_BYTES", 10)

    response = upload(client, SAMPLE_CSV.read_bytes())

    assert response.status_code == 413
    assert response.json()["error"] == "FILE_TOO_LARGE"


def test_list_by_source_is_sorted_by_price(client, repository, sample_flights):
    repository.flights = sample_flights

    response = client.get("/api/data/flights", params={"source": "yyz"})

    assert response.status_code == 200
    prices = [f["price"] for f in response.json()]
    assert prices == sorted(prices)
    assert {f["source_airport"] for f in response.json()} == {"YYZ"}


def test_search_direct_flights(client, repository, sample_flights):
    repository.flights = sample_flights

    response = client.get("/api/data/flights/search", params={"from": "YYZ", "to": "ORD"})

    assert [f["flight_id"] for f in response.json()] == ["F002"]


def test_domestic_and_international_endpoints(client, repository, sample_flights):
    repository.flights = sample_flights

    assert len(client.get("/api/data/flights/domestic").json()) == 14
    assert len(client.get("/api/data/flights/international").json()) == 10


def test_unknown_sort_field_is_400(client):
    response = client.get("/api/data/flights/domestic", params={"sort_by": "airline"})

    assert response.status_code == 400
    assert response.json()["error"] == "VALIDATION_FAILED"


def test_malformed_airport_code_is_400(client):
    response = client.get("/api/data/flights", params={"source": "TORONTO"})

    assert response.status_code == 400
    assert response.json()["error"] == "VALIDATION_FAILED"
