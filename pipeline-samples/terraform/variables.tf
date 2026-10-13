variable "aws_region" {
  description = "배포 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_id" {
  description = "itembank dev VPC ID"
  type        = string
  default     = "vpc-0a1b2c3d4e5f60718"
}

variable "api_subnet_cidr" {
  description = "문항 은행 API 서버가 있는 서브넷 CIDR (DB 접속 허용 출발지)"
  type        = string
  default     = "10.20.7.0/24"
}

variable "db_subnet_ids" {
  description = "DB 서브넷 그룹에 넣을 private 서브넷 ID 목록"
  type        = list(string)
  default     = ["subnet-0aa11bb22cc33dd44", "subnet-0ee55ff66aa77bb88"]
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t3.micro"
}

variable "db_username" {
  description = "DB 관리자 계정 이름"
  type        = string
  default     = "itembank_admin"
}

variable "db_password" {
  description = "DB 관리자 비밀번호. 시크릿 저장소에서 주입한다(TF_VAR_db_password)"
  type        = string
  sensitive   = true
}
