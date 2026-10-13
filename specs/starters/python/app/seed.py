"""로컬 확인용 시드 데이터를 넣는 곳.

이 시작 골격이 정한 시드 방식은 이 파일의 seed() 하나다.
uvicorn 으로 서버를 띄우면 시작할 때 seed() 가 한 번 실행된다.
테스트(pytest)에서는 실행되지 않는다(tests/conftest.py 가 create_app(seed=False) 를 쓴다).

더미 데이터가 필요해지면 seed() 안에서 conn.execute("INSERT ...") 로 넣는다.
실제 학교 · 학생 이름은 쓰지 않는다.
"""

import logging
import sqlite3

log = logging.getLogger("uvicorn.error")


def seed(conn: sqlite3.Connection) -> None:
    # 여기에 시드 데이터를 넣는다. 지금은 비어 있다.
    conn.commit()
    log.info("시드 데이터 적재 완료")
