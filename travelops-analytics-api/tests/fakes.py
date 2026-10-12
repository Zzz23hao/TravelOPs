import pandas as pd

from app.services.flight_parser import COLUMNS, ParseResult


class InMemoryFlightRepository:
    """Stands in for FlightRepository in API tests: same methods, no database."""

    def __init__(self, flights: pd.DataFrame | None = None):
        self.flights = flights if flights is not None else pd.DataFrame(columns=COLUMNS)
        self.jobs: list[str] = []

    def save_import(self, filename: str, result: ParseResult) -> int:
        self.jobs.append(filename)
        kept = self.flights[~self.flights["flight_id"].isin(result.valid["flight_id"])]
        self.flights = pd.concat([kept, result.valid], ignore_index=True)
        return len(self.jobs)

    def load_flights(self) -> pd.DataFrame:
        return self.flights.copy()
