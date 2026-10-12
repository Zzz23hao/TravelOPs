from fastapi.testclient import TestClient

from app.main import create_app

client = TestClient(create_app())


def test_health_reports_service_name():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "travelops-analytics-api"}


def test_unknown_path_uses_the_shared_error_shape():
    response = client.get("/no-such-endpoint")

    assert response.status_code == 404
    assert response.json()["error"] == "NOT_FOUND"
