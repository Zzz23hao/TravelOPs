import logging

from fastapi import FastAPI

from app.errors import register_error_handlers
from app.routers import health

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def create_app() -> FastAPI:
    app = FastAPI(title="TravelOps Analytics API")
    register_error_handlers(app)
    app.include_router(health.router)
    return app


app = create_app()
