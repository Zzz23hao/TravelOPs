import pytest

from app.services import route_search
from app.services.route_graph import Edge, RouteGraph


@pytest.fixture
def graph(sample_flights):
    return RouteGraph.from_flights(sample_flights)


def airports(edges):
    return [edges[0].source, *(e.destination for e in edges)]


def edge(flight_id, source, destination, distance=100.0, price=100.0):
    return Edge(flight_id, source, destination, distance, 60, price)


def test_shortest_minimizes_total_distance(graph):
    edges = route_search.shortest(graph, "YYZ", "LAX")

    assert airports(edges) == ["YYZ", "ORD", "LAX"]
    assert sum(e.distance_km for e in edges) == 3507


def test_cheapest_minimizes_total_price_even_with_more_transfers(graph):
    edges = route_search.cheapest(graph, "YYZ", "LAX")

    assert airports(edges) == ["YYZ", "JFK", "DEN", "LAX"]
    assert sum(e.price for e in edges) == 280


def test_min_transfers_minimizes_number_of_flights(graph):
    edges = route_search.min_transfers(graph, "YYZ", "LAX")

    assert len(edges) == 2  # no direct YYZ-LAX flight in the sample, so one transfer is the minimum


def test_three_searches_can_disagree(graph):
    shortest = airports(route_search.shortest(graph, "YYZ", "LAX"))
    cheapest = airports(route_search.cheapest(graph, "YYZ", "LAX"))

    assert shortest != cheapest


def test_dijkstra_prefers_a_cheaper_two_hop_route_over_an_expensive_direct_flight():
    graph = RouteGraph([
        edge("D", "A", "C", price=500),
        edge("H1", "A", "B", price=100),
        edge("H2", "B", "C", price=100),
    ])

    assert [e.flight_id for e in route_search.cheapest(graph, "A", "C")] == ["H1", "H2"]
    assert [e.flight_id for e in route_search.min_transfers(graph, "A", "C")] == ["D"]


def test_parallel_flights_each_search_picks_the_best_one_for_its_weight():
    graph = RouteGraph([
        edge("NEAR", "A", "B", distance=10, price=300),
        edge("CHEAP", "A", "B", distance=50, price=80),
    ])

    assert route_search.shortest(graph, "A", "B")[0].flight_id == "NEAR"
    assert route_search.cheapest(graph, "A", "B")[0].flight_id == "CHEAP"
    assert route_search.min_transfers(graph, "A", "B")[0].flight_id == "CHEAP"


def test_flights_are_directed():
    graph = RouteGraph([edge("F1", "A", "B")])

    assert route_search.shortest(graph, "B", "A") is None


@pytest.mark.parametrize("search", [route_search.shortest, route_search.cheapest, route_search.min_transfers])
def test_no_route_returns_none(graph, search):
    # MEX only has an outbound flight in the sample, so nothing can reach it.
    assert graph.has_airport("MEX")
    assert search(graph, "YYZ", "MEX") is None


def test_unknown_airport_is_not_in_graph(graph):
    assert not graph.has_airport("XXX")
