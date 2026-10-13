"""저장소: 표준 라이브러리 sqlite3 의 메모리 DB.

외부 DB 없이 돈다. 서버를 끄면 데이터가 사라지고, 띄울 때마다 새로 만든다.
테이블은 app/schema.sql 에 적는다.
"""

import sqlite3
from pathlib import Path

from fastapi import Request

SCHEMA_PATH = Path(__file__).with_name("schema.sql")


def connect(path: str = ":memory:") -> sqlite3.Connection:
    """새 연결을 열고 schema.sql 을 적용한다."""
    conn = sqlite3.connect(path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(SCHEMA_PATH.read_text(encoding="utf-8"))
    conn.commit()
    return conn


def get_db(request: Request) -> sqlite3.Connection:
    """엔드포인트에서 Depends(get_db) 로 받아 쓴다."""
    return request.app.state.db
