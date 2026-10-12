from fastapi import APIRouter

router = APIRouter()


# Liveness only: it does not query Postgres, so it reports whether this process can serve requests.
@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "travelops-analytics-api"}
