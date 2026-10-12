from pathlib import Path

import pytest

from app.services.flight_parser import COLUMNS, CsvFormatError, parse_flights_csv

HEADER = ",".join(COLUMNS)
VALID_ROW = "F001,Air Canada,YYZ,YVR,Toronto,Vancouver,Canada,Canada,3359,310,320"
SAMPLE_CSV = Path(__file__).resolve().parents[1] / "data" / "sample_flights.csv"


def parse(*rows: str):
    return parse_flights_csv("\n".join([HEADER, *rows]).encode())


def test_valid_record_is_kept_with_numeric_types():
    result = parse(VALID_ROW)

    assert result.total_records == 1
    assert result.invalid == []
    row = result.valid.iloc[0]
    assert row["flight_id"] == "F001"
    assert row["distance_km"] == 3359.0
    assert row["duration_minutes"] == 310
    assert row["price"] == 320.0


def test_codes_are_trimmed_and_upper_cased():
    result = parse("F001,Air Canada, yyz ,yvr,Toronto,Vancouver,Canada,Canada,3359,310,320")

    assert result.valid.iloc[0]["source_airport"] == "YYZ"
    assert result.valid.iloc[0]["destination_airport"] == "YVR"


@pytest.mark.parametrize(
    "row, reason",
    [
        (",Air Canada,YYZ,YVR,Toronto,Vancouver,Canada,Canada,3359,310,320", "flight_id is required"),
        ("F001,Air Canada,,YVR,Toronto,Vancouver,Canada,Canada,3359,310,320", "source_airport is required"),
        ("F001,Air Canada,YYZ,,Toronto,Vancouver,Canada,Canada,3359,310,320", "destination_airport is required"),
        ("F001,Air Canada,YYZ,YVR,Toronto,Vancouver,Canada,Canada,-1,310,320", "distance_km must be a positive number"),
        ("F001,Air Canada,YYZ,YVR,Toronto,Vancouver,Canada,Canada,3359,0,320",
         "duration_minutes must be a positive whole number"),
        ("F001,Air Canada,YYZ,YVR,Toronto,Vancouver,Canada,Canada,3359,310,0", "price must be a positive number"),
        ("F001,Air Canada,YYZ,YYZ,Toronto,Toronto,Canada,Canada,3359,310,320",
         "source_airport must differ from destination_airport"),
    ],
)
def test_each_rule_rejects_its_bad_value(row, reason):
    result = parse(row)

    assert len(result.valid) == 0
    assert reason in result.invalid[0].reasons


@pytest.mark.parametrize("bad_number", ["sixty", "", "inf", "12.5"])
def test_non_numeric_infinite_or_fractional_duration_is_rejected(bad_number):
    result = parse(f"F001,Air Canada,YYZ,YVR,Toronto,Vancouver,Canada,Canada,3359,{bad_number},320")

    assert len(result.valid) == 0
    assert result.invalid[0].reasons == ["duration_minutes must be a positive whole number"]


def test_row_with_extra_fields_is_counted_not_fatal():
    result = parse(VALID_ROW, "F002,United,ORD,JFK,Chicago,New York,USA,USA,1188,125,160,EXTRA")

    assert len(result.valid) == 1
    assert result.total_records == 2
    assert result.invalid[0].flight_id == "F002"
    assert result.invalid[0].reasons == ["expected 11 fields, got 12"]


def test_row_with_missing_fields_is_rejected():
    result = parse(VALID_ROW, "F002,United,ORD")

    assert len(result.valid) == 1
    assert "destination_airport is required" in result.invalid[0].reasons


def test_one_row_can_break_several_rules():
    result = parse("F025,Air Canada,YYZ,YYZ,Toronto,Toronto,Canada,Canada,0,0,0")

    assert len(result.invalid[0].reasons) == 4


def test_missing_column_rejects_the_whole_file():
    with pytest.raises(CsvFormatError, match="price"):
        parse_flights_csv(b"flight_id,source_airport,destination_airport\nF001,YYZ,YVR")


def test_empty_file_is_rejected():
    with pytest.raises(CsvFormatError, match="empty"):
        parse_flights_csv(b"")


def test_non_utf8_file_is_rejected():
    with pytest.raises(CsvFormatError, match="UTF-8"):
        parse_flights_csv(b"\xff\xfe\x00bad")


def test_header_only_file_is_an_empty_import_not_an_error():
    result = parse()

    assert result.total_records == 0


def test_sample_file_has_valid_and_malformed_records():
    result = parse_flights_csv(SAMPLE_CSV.read_bytes())

    assert len(result.valid) == 24
    assert len(result.invalid) == 7
    assert result.total_records == 31
