from collections.abc import Callable
from typing import Annotated

from fastapi import APIRouter, Depends, Query

from app.errors import ApiError
from app.models.flight import AirportCode
from app.models.route import RouteResponse
from app.services import route_search
from app.services.route_graph import Edge, RouteGraph
from app.storage.flight_repository import FlightRepository, get_repository

router = APIRouter(prefix="/routes")

Repository = Annotated[FlightRepository, Depends(get_repository)]
FromCode = Annotated[str, Query(alias="from", pattern=r"^[A-Za-z]{3}$")]


@router.get("/shortest", response_model=RouteResponse)
def shortest_route(repository: Repository, from_: FromCode, to: AirportCode) -> RouteResponse:
    return _find("shortest", route_search.shortest, repository, from_, to)


@router.get("/cheapest", response_model=RouteResponse)
def cheapest_route(repository: Repository, from_: FromCode, to: AirportCode) -> RouteResponse:
    return _find("cheapest", route_search.cheapest, repository, from_, to)


@router.get("/min-transfers", response_model=RouteResponse)
def min_transfers_route(repository: Repository, from_: FromCode, to: AirportCode) -> RouteResponse:
    return _find("min-transfers", route_search.min_transfers, repository, from_, to)


def _find(
    search_type: str,
    search: Callable[[RouteGraph, str, str], list[Edge] | None],
    repository: FlightRepository,
    source: str,
    target: str,
) -> RouteResponse:
    source, target = source.upper(), target.upper()
    if source == target:
        raise ApiError(400, "INVALID_PARAMETER", "from and to must be different airports.")

    # Built per request from the current table, so a new upload is visible immediately.
    graph = RouteGraph.from_flights(repository.load_flights())
    unknown = [code for code in (source, target) if not graph.has_airport(code)]
    if unknown:
        raise ApiError(404, "AIRPORT_NOT_FOUND", f"No flights to or from {', '.join(unknown)}.")

    edges = search(graph, source, target)
    if edges is None:
        raise ApiError(404, "NO_ROUTE_FOUND", f"No route from {source} to {target}.")
    return RouteResponse.from_edges(search_type, edges)
