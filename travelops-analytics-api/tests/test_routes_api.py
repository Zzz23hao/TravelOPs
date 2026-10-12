import pytest


@pytest.fixture(autouse=True)
def loaded(repository, sample_flights):
    repository.flights = sample_flights


def test_shortest_route_response_shape(client):
    response = client.get("/routes/shortest", params={"from": "YYZ", "to": "LAX"})

    assert response.status_code == 200
    assert response.json() == {
        "from": "YYZ",
        "to": "LAX",
        "search_type": "shortest",
        "route": ["YYZ", "ORD", "LAX"],
        "flight_ids": ["F002", "F003"],
        "total_distance_km": 3507.0,
        "total_duration_minutes": 355,
        "total_price": 420.0,
        "transfers": 1,
    }


def test_cheapest_and_min_transfers_endpoints(client):
    cheapest = client.get("/routes/cheapest", params={"from": "yyz", "to": "lax"}).json()
    fewest = client.get("/routes/min-transfers", params={"from": "YYZ", "to": "LAX"}).json()

    assert cheapest["total_price"] == 280.0
    assert cheapest["transfers"] == 2
    assert fewest["transfers"] == 1


def test_no_route_is_404(client):
    response = client.get("/routes/shortest", params={"from": "YYZ", "to": "MEX"})

    assert response.status_code == 404
    assert response.json()["error"] == "NO_ROUTE_FOUND"


def test_unknown_airport_is_404_with_its_own_code(client):
    response = client.get("/routes/cheapest", params={"from": "YYZ", "to": "XXX"})

    assert response.status_code == 404
    assert response.json() == {"error": "AIRPORT_NOT_FOUND", "message": "No flights to or from XXX."}


def test_same_origin_and_destination_is_400(client):
    response = client.get("/routes/shortest", params={"from": "YYZ", "to": "yyz"})

    assert response.status_code == 400
    assert response.json()["error"] == "INVALID_PARAMETER"


def test_missing_parameter_is_400(client):
    response = client.get("/routes/shortest", params={"from": "YYZ"})

    assert response.status_code == 400
    assert response.json() == {"error": "VALIDATION_FAILED", "message": "to: Field required"}


def test_unknown_search_type_is_404(client):
    response = client.get("/routes/fastest", params={"from": "YYZ", "to": "LAX"})

    assert response.status_code == 404
    assert response.json()["error"] == "NOT_FOUND"
