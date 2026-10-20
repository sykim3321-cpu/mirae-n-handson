# CLAUDE.md — 인프라 · AWS 운영 팀 템플릿

이 파일은 `templates/CLAUDE.iac.md` 입니다. 저장소 루트의 `CLAUDE.md` 로 옮겨 쓰기 전에 팀 사정에 맞지 않는 줄을 고치세요.
대상: Terraform 으로 관리하는 AWS 자원. 이 저장소에서는 `pipeline-samples/terraform/`(`main.tf` · `variables.tf`)이 재료다. **실제 AWS 계정에는 접속하지 않는다.**

## 1. 명령 — plan 과 apply 를 분리한다

```bash
cd pipeline-samples/terraform && terraform fmt -check          # 형식 검사 (고치려면 -check 를 뺀다)
cd pipeline-samples/terraform && terraform init -backend=false  # 로컬 문법 검증용. 원격 상태에 붙지 않는다
cd pipeline-samples/terraform && terraform validate             # 문법 · 참조 검증
terraform plan -out=tfplan                                      # 변경 미리보기. 사람이 읽는다
terraform show -no-color tfplan > plan.txt                      # 리뷰용 텍스트. PR 에 첨부
terraform apply tfplan                                          # 승인된 plan 파일만 적용. 사람이 직접 실행한다
```

- **Claude 가 실행해도 되는 것**: `fmt`, `init -backend=false`, `validate`, `plan`. **Claude 가 실행하지 않는 것**: `apply`, `destroy`, `import`, `state` 하위 명령, `taint`, `force-unlock`. 이 명령들은 답변에 "사람이 실행할 명령"으로 적기만 한다.
- `apply` 는 항상 저장된 plan 파일(`tfplan`)로만 한다. plan 없이 `apply -auto-approve` 를 쓰지 않는다.
- `plan` 결과에 `destroy` 또는 `replace`(`-/+`, `must be replaced`)가 하나라도 있으면 답변 첫 줄에 그 자원 이름을 적고 멈춘다.

## 2. 코딩 컨벤션

### 파일 · 모듈 구조
- 환경별 디렉터리: `envs/dev/`, `envs/stg/`, `envs/prod/`. 공통 자원은 `modules/<이름>/` 로 빼고 환경 디렉터리에서 호출한다.
- 파일 역할 고정: `main.tf`(자원), `variables.tf`(입력), `outputs.tf`(출력), `versions.tf`(provider · terraform 버전 고정), `backend.tf`(상태 저장소).
- 자원 이름은 `<서비스>-<환경>-<역할>` (예: `itembank-dev-db`). `identifier` · `name` 같은 교체를 일으키는 속성은 한 번 정하면 바꾸지 않는다.
- 변수 · 출력에는 `description` 을 반드시 쓴다. 비밀값 변수는 `sensitive = true` 이고 `default` 가 없다.

### 네트워크 · 보안 그룹
- ingress 의 `cidr_blocks` 에 `0.0.0.0/0` · `::/0` 은 80 · 443 을 제외하고 금지한다. DB · 관리 포트(22, 1433, 3306, 5432, 6379)는 반드시 특정 CIDR 또는 보안 그룹 참조(`security_groups`)로 제한한다.
- 보안 그룹 규칙은 인라인 블록이 아니라 `aws_security_group_rule` 자원으로 하나씩 만든다(diff 가 읽히도록).
- 공개 서브넷에 DB 인스턴스를 두지 않는다(`publicly_accessible = false`).

### 태그 · 비용
- 모든 자원에 `Project`, `Env`, `Owner`, `ManagedBy = "terraform"` 태그를 붙인다(`default_tags` 사용).
- 인스턴스 · DB 클래스 변경, 스토리지 확장, NAT · ALB 추가처럼 월 비용이 바뀌는 변경은 답변에 "비용 영향" 한 줄을 적는다.

## 3. 상태 파일 · 시크릿 취급 규칙

- 상태 파일(`*.tfstate`, `*.tfstate.backup`)과 plan 파일(`tfplan`)은 **읽지 않고, 커밋하지 않고, 답변에 내용을 붙이지 않는다.** 상태에는 비밀값이 평문으로 들어 있다.
- 상태 저장소는 원격 backend(S3 + 잠금)만 쓴다. 로컬 상태로 `apply` 하지 않는다. `terraform state rm/mv/push` 는 사람이 직접 한다.
- 비밀값(DB 비밀번호, API 키, 인증서)은 변수 기본값 · `terraform.tfvars` · 코드 리터럴에 넣지 않는다. Secrets Manager · SSM Parameter Store 참조(`data` 소스)나 CI 의 시크릿 주입으로 받는다.
- `.env`, `.env.*`, `*.pem`, `~/.aws/`, `~/.ssh/` 는 읽지 않는다. 자격 증명은 이미 셸에 설정돼 있다고 가정하고 값을 확인하지 않는다(확인이 필요하면 `aws sts get-caller-identity` 결과의 계정 ID 뒷 4자리만).
- `.gitignore` 에 `.terraform/`, `*.tfstate*`, `tfplan`, `*.tfvars`(예시 파일 `*.tfvars.example` 제외)가 있어야 한다.

## 4. 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- **운영 환경(`envs/prod/`) 변경 금지.** 운영 디렉터리의 파일을 고치라는 요청은 거절하고, 대신 `dev` 에서 같은 변경의 plan 을 만들어 보인다. 운영 반영은 사람이 PR 승인 후 직접 한다.
- `terraform apply` · `destroy` · `import` · `state` · `force-unlock` 을 실행하지 않는다.
- 운영 DB 호스트(`prod-db` 등), 운영 계정 프로필(`AWS_PROFILE=prod`)을 명령에 쓰지 않는다.
- 자원을 교체(`replace`)하거나 삭제하는 변경(`identifier` · `name` · `engine` 변경, `lifecycle` 의 `prevent_destroy` 제거)은 먼저 사람에게 묻는다.
- provider · module 버전 고정(`versions.tf`, `required_version`)을 풀거나 올리지 않는다. 필요하면 별도 변경으로 제안만 한다.
- IAM 정책에 `"Action": "*"` 또는 `"Resource": "*"` 를 함께 쓰지 않는다. 권한 상승이 필요한 변경(IAM 역할 · 정책 · 신뢰 관계)은 보안 담당 확인이 필요하다고 답변에 적는다.
- 요청받지 않은 자원을 "정리" 명목으로 고치지 않는다.

## 5. 아키텍처 안내

```
pipeline-samples/terraform/     이 저장소의 실습용 예시
├── main.tf        aws_security_group (3306 을 제한된 CIDR 로만 허용), aws_db_instance (identifier 고정)
└── variables.tf   db_password: sensitive = true, default 없음
```

- 리뷰 기준: (1) 보안 그룹 개방 — ingress 에 `0.0.0.0/0` · `::/0`, 특히 DB · 관리 포트 (2) 자원 교체 · 삭제 — `identifier` 등 교체 유발 속성 변경 (3) 시크릿 노출 — 평문 기본값 · 리터럴, `sensitive` 누락. 지적마다 `파일:줄번호`.
- 판정은 첫 줄에 "판정: 승인" 또는 "판정: 반려". 치명이 1건이라도 있으면 반려.
- 변경 흐름: 요청 → 코드 수정(dev) → `fmt` · `validate` · `plan` → 리뷰(읽기 전용) → 반려면 수정 → 승인 → 사람이 `apply`.

## 6. 완료 기준

- [ ] `terraform fmt -check` · `terraform validate` 가 통과했고 결과를 답변에 적었다
- [ ] `plan` 결과의 추가 · 변경 · 삭제 자원 수를 답변에 적었고, `destroy` · `replace` 가 있으면 첫 줄에 표시했다
- [ ] 변경이 `envs/prod/` 를 건드리지 않았다
- [ ] 새 변수에 `description` 이 있고, 비밀값 변수는 `sensitive = true` 이며 `default` 가 없다
- [ ] 상태 파일 · plan 파일 · 자격 증명을 읽거나 답변에 붙이지 않았다
- [ ] 변경 파일이 요청 범위 안에 있다
