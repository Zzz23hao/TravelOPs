import os


def database_url() -> str:
    # Read on use, not at import, so tests that never touch the database need no environment.
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise RuntimeError("DATABASE_URL is not set.")
    return url
