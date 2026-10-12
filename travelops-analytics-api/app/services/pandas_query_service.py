"""Flight queries as Pandas filters over the flights table loaded into a DataFrame."""

from typing import Literal

import pandas as pd

SortField = Literal["price", "duration_minutes"]


def by_source(flights: pd.DataFrame, source: str | None, sort_by: SortField) -> pd.DataFrame:
    if source is not None:
        flights = flights[flights["source_airport"] == source]
    return _sorted(flights, sort_by)


def direct(flights: pd.DataFrame, source: str, destination: str, sort_by: SortField) -> pd.DataFrame:
    mask = (flights["source_airport"] == source) & (flights["destination_airport"] == destination)
    return _sorted(flights[mask], sort_by)


def domestic(flights: pd.DataFrame, sort_by: SortField) -> pd.DataFrame:
    return _sorted(flights[_same_country(flights)], sort_by)


def international(flights: pd.DataFrame, sort_by: SortField) -> pd.DataFrame:
    return _sorted(flights[~_same_country(flights)], sort_by)


def _same_country(flights: pd.DataFrame) -> pd.Series:
    # casefold so "USA" and "usa" from different CSV sources still count as one country.
    return flights["source_country"].str.casefold() == flights["destination_country"].str.casefold()


def _sorted(flights: pd.DataFrame, sort_by: SortField) -> pd.DataFrame:
    # flight_id breaks ties so equal prices come back in the same order on every call.
    return flights.sort_values([sort_by, "flight_id"])
