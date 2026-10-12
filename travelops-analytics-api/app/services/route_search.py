"""Route searches over a RouteGraph. Each returns the flights of the route, or None if none exists."""

import heapq
import math
from collections import deque
from collections.abc import Callable

from app.services.route_graph import Edge, RouteGraph


def shortest(graph: RouteGraph, source: str, target: str) -> list[Edge] | None:
    return _dijkstra(graph, source, target, weight=lambda edge: edge.distance_km)


def cheapest(graph: RouteGraph, source: str, target: str) -> list[Edge] | None:
    return _dijkstra(graph, source, target, weight=lambda edge: edge.price)


def min_transfers(graph: RouteGraph, source: str, target: str) -> list[Edge] | None:
    """BFS: every flight counts as one hop, so the first time BFS reaches the target it has used
    the fewest flights. Among routes with equally few hops it does not look for the cheapest."""
    via: dict[str, Edge] = {}
    seen = {source}
    queue = deque([source])
    while queue:
        airport = queue.popleft()
        if airport == target:
            return _path(via, source, target)
        for edge in graph.outgoing(airport):
            if edge.destination not in seen:
                seen.add(edge.destination)
                via[edge.destination] = edge
                queue.append(edge.destination)
    return None


def _dijkstra(graph: RouteGraph, source: str, target: str, weight: Callable[[Edge], float]) -> list[Edge] | None:
    """Correct only for non-negative weights; the parser guarantees distance and price are > 0."""
    best = {source: 0.0}
    via: dict[str, Edge] = {}
    settled: set[str] = set()
    heap = [(0.0, source)]
    while heap:
        cost, airport = heapq.heappop(heap)
        # The heap may still hold an older, more expensive entry for an airport already settled.
        if airport in settled:
            continue
        if airport == target:
            return _path(via, source, target)
        settled.add(airport)
        for edge in graph.outgoing(airport):
            candidate = cost + weight(edge)
            if candidate < best.get(edge.destination, math.inf):
                best[edge.destination] = candidate
                via[edge.destination] = edge
                heapq.heappush(heap, (candidate, edge.destination))
    return None


def _path(via: dict[str, Edge], source: str, target: str) -> list[Edge]:
    """Walks the predecessor edges back from the target, then reverses them into travel order."""
    edges = []
    airport = target
    while airport != source:
        edge = via[airport]
        edges.append(edge)
        airport = edge.source
    return edges[::-1]
