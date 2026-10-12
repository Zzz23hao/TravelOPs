from collections import defaultdict
from collections.abc import Iterable
from dataclasses import dataclass

import pandas as pd


@dataclass(frozen=True)
class Edge:
    """One flight: a directed edge from source airport to destination airport."""

    flight_id: str
    source: str
    destination: str
    distance_km: float
    duration_minutes: int
    price: float


class RouteGraph:
    """Directed multigraph: nodes are airports, edges are flights.

    Directed because a flight YYZ to LAX says nothing about LAX to YYZ. A multigraph because two
    airlines flying the same pair are two different edges with different prices and distances.
    """

    def __init__(self, edges: Iterable[Edge]):
        self._outgoing: dict[str, list[Edge]] = defaultdict(list)
        self._airports: set[str] = set()
        for edge in edges:
            self._outgoing[edge.source].append(edge)
            self._airports.update((edge.source, edge.destination))
        # A fixed neighbour order makes every search deterministic, and puts the cheapest of
        # several parallel flights first (BFS relies on that).
        for outgoing in self._outgoing.values():
            outgoing.sort(key=lambda e: (e.destination, e.price, e.flight_id))

    @classmethod
    def from_flights(cls, flights: pd.DataFrame) -> "RouteGraph":
        return cls(
            Edge(
                flight_id=row.flight_id,
                source=row.source_airport,
                destination=row.destination_airport,
                distance_km=float(row.distance_km),
                duration_minutes=int(row.duration_minutes),
                price=float(row.price),
            )
            for row in flights.itertuples(index=False)
        )

    def has_airport(self, code: str) -> bool:
        return code in self._airports

    def outgoing(self, code: str) -> list[Edge]:
        return self._outgoing.get(code, [])
