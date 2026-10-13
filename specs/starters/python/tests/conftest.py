import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client():
    """테스트마다 새 메모리 DB를 가진 앱. 시드 데이터는 넣지 않는다."""
    with TestClient(create_app(seed=False)) as c:
        yield c
