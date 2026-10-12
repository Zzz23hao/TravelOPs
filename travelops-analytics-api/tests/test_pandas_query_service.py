import pandas as pd

from app.services import pandas_query_service as queries


def ids(frame: pd.DataFrame) -> list[str]:
    return frame["flight_id"].tolist()


def test_domestic_means_same_source_and_destination_country(sample_flights):
    result = queries.domestic(sample_flights, "price")

    assert len(result) == 14
    assert (result["source_country"] == result["destination_country"]).all()


def test_international_means_different_countries(sample_flights):
    result = queries.international(sample_flights, "price")

    assert len(result) == 10
    assert (result["source_country"] != result["destination_country"]).all()
    assert "F018" in ids(result)  # London to Paris: international without touching North America


def test_domestic_and_international_partition_all_flights(sample_flights):
    assert len(queries.domestic(sample_flights, "price")) + len(
        queries.international(sample_flights, "price")
    ) == len(sample_flights)


def test_country_comparison_ignores_case(sample_flights):
    flights = sample_flights.copy()
    flights.loc[flights["flight_id"] == "F003", "destination_country"] = "usa"

    assert "F003" in ids(queries.domestic(flights, "price"))


def test_by_source_filters_and_sorts_by_price(sample_flights):
    result = queries.by_source(sample_flights, "YYZ", "price")

    assert set(result["source_airport"]) == {"YYZ"}
    assert result["price"].is_monotonic_increasing
    assert ids(result)[0] == "F006"  # 90 to New York is the cheapest departure from Toronto


def test_by_source_can_sort_by_duration(sample_flights):
    result = queries.by_source(sample_flights, "YYZ", "duration_minutes")

    assert result["duration_minutes"].is_monotonic_increasing


def test_without_source_returns_every_flight(sample_flights):
    assert len(queries.by_source(sample_flights, None, "price")) == 24


def test_direct_only_returns_flights_between_the_two_airports(sample_flights):
    assert ids(queries.direct(sample_flights, "YYZ", "ORD", "price")) == ["F002"]
    assert ids(queries.direct(sample_flights, "YYZ", "LAX", "price")) == []
