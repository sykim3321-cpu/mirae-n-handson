from contextlib import asynccontextmanager

from fastapi import FastAPI

from app import db
from app.seed import seed as load_seed


def create_app(seed: bool = True) -> FastAPI:
    """앱을 만든다. 서버 실행은 seed=True, 테스트는 seed=False 로 만든다."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.db = db.connect()
        if seed:
            load_seed(app.state.db)
        yield
        app.state.db.close()

    app = FastAPI(title="spec-project", lifespan=lifespan)

    @app.get("/health")
    def health() -> dict[str, str]:
        """서버가 떠 있는지만 확인하는 엔드포인트. 스펙의 기능과는 관계가 없다."""
        return {"status": "ok"}

    return app


# uvicorn app.main:app 이 이 객체를 띄운다.
app = create_app()
