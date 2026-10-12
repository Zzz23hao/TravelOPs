from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.services.flight_parser import parse_flights_csv
from app.storage.flight_repository import get_repository
from tests.fakes import InMemoryFlightRepository

SAMPLE_CSV = Path(__file__).resolve().parents[1] / "data" / "sample_flights.csv"


@pytest.fixture
def sample_flights():
    """The 24 valid flights from the sample file, as the parser produces them."""
    return parse_flights_csv(SAMPLE_CSV.read_bytes()).valid


@pytest.fixture
def repository():
    return InMemoryFlightRepository()


@pytest.fixture
def client(repository):
    app = create_app(init_schema=False)
    app.dependency_overrides[get_repository] = lambda: repository
    return TestClient(app)
