from typing import Annotated

from fastapi import Query
from pydantic import BaseModel

AirportCode = Annotated[str, Query(pattern=r"^[A-Za-z]{3}$", description="Three-letter IATA code")]


class FlightOut(BaseModel):
    flight_id: str
    airline: str
    source_airport: str
    destination_airport: str
    source_city: str
    destination_city: str
    source_country: str
    destination_country: str
    distance_km: float
    duration_minutes: int
    price: float
