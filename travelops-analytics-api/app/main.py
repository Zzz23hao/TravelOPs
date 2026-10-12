import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import database_url
from app.errors import register_error_handlers
from app.routers import flights, health, routes
from app.storage.flight_repository import FlightRepository

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def create_app(init_schema: bool = True) -> FastAPI:
    """Tests pass init_schema=False: they swap in a fake repository and need no database."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if init_schema:
            FlightRepository(database_url()).init_schema()
        yield

    app = FastAPI(title="TravelOps Analytics API", lifespan=lifespan)
    register_error_handlers(app)
    app.include_router(health.router)
    app.include_router(flights.router)
    app.include_router(routes.router)
    return app


app = create_app()
