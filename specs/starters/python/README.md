# spec-project (Python · FastAPI 시작 골격)

Day 4-2 spec-driven 실습용 빈 골격입니다. 스펙의 기능은 하나도 들어 있지 않고, 서버가 뜨는지 확인하는 `GET /health` 와 그 테스트 하나만 있습니다.

- FastAPI · Python 3.12 기준
- 저장: 표준 라이브러리 `sqlite3` 의 메모리 DB. 외부 DB나 Docker 없이 돕니다. 서버를 끄면(또는 `--reload` 로 다시 뜨면) 데이터가 사라집니다.

## 명령

| 할 일 | 명령 |
|---|---|
| 처음 한 번 준비 | `python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt` |
| 테스트 (검증 명령) | `pytest` |
| 서버 실행 | `uvicorn app.main:app --reload --port 8000` → `http://localhost:8000` |

새 터미널 탭을 열 때마다 `source .venv/bin/activate` 를 다시 합니다. 프롬프트 앞에 `(.venv)` 가 보이면 켜진 것입니다.

서버를 띄운 뒤 다른 터미널에서 확인합니다. `200` 과 `{"status":"ok"}` 가 나오면 됩니다.

```
curl -i http://localhost:8000/health
```

## 구조

```
requirements.txt      fastapi · uvicorn · pytest · httpx
pytest.ini            루트에서 pytest 를 치면 tests/ 를 찾는다
app/
  main.py             create_app() 과 app 객체, GET /health
  db.py               sqlite3 메모리 DB 연결, Depends(get_db)
  schema.sql          테이블 정의(지금은 비어 있음)
  seed.py             로컬 확인용 시드 데이터(아래 참고)
tests/
  conftest.py         client 픽스처 — 테스트마다 새 DB, 시드 없음
  test_health.py      GET /health 가 200 인지 확인
```

엔드포인트는 `create_app()` 안에 추가하거나 `APIRouter` 로 나눠 `app.include_router()` 로 붙입니다. DB는 `Depends(get_db)` 로 받습니다. 테이블은 `app/schema.sql` 에 적습니다.

## 시드 데이터

로컬에서 직접 호출해 볼 더미 데이터는 **`app/seed.py` 의 `seed()` 안에** 넣습니다. 이 골격이 정한 시드 방식은 이것 하나입니다.

- `uvicorn` 으로 서버를 띄우면 시작할 때 `seed()` 가 한 번 실행되고, 로그에 `시드 데이터 적재 완료` 가 나옵니다.
- `pytest` 에서는 실행되지 않습니다(`tests/conftest.py` 가 `create_app(seed=False)` 를 씁니다). 테스트에 필요한 데이터는 테스트 안에서 만듭니다.
- 실제 학교 · 학생 이름은 쓰지 않습니다.

## 안 될 때

- `ensurepip is not available` (WSL) → `sudo apt install -y python3-venv` 후 다시 실행합니다.
- `pytest: command not found` → 가상환경이 꺼져 있습니다. `source .venv/bin/activate` 후 다시 실행합니다.
