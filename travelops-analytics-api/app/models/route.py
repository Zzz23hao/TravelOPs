from pydantic import BaseModel, ConfigDict, Field

from app.services.route_graph import Edge


class RouteResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    # "from" is a Python keyword: the field is from_ in code and "from" in JSON.
    from_: str = Field(serialization_alias="from")
    to: str
    search_type: str
    route: list[str]
    flight_ids: list[str]
    total_distance_km: float
    # Sum of flight times only; the data has no schedules, so layovers are not counted.
    total_duration_minutes: int
    total_price: float
    transfers: int

    @classmethod
    def from_edges(cls, search_type: str, edges: list[Edge]) -> "RouteResponse":
        return cls(
            from_=edges[0].source,
            to=edges[-1].destination,
            search_type=search_type,
            route=[edges[0].source, *(e.destination for e in edges)],
            flight_ids=[e.flight_id for e in edges],
            # round() hides float noise such as 0.1 + 0.2 == 0.30000000000000004 in money totals.
            total_distance_km=round(sum(e.distance_km for e in edges), 2),
            total_duration_minutes=sum(e.duration_minutes for e in edges),
            total_price=round(sum(e.price for e in edges), 2),
            transfers=len(edges) - 1,
        )
