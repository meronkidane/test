variable "vpc_id" { type = string }
variable "application_security_group_id" { type = string }
resource "aws_security_group" "database" {
  name_prefix = "review-db-"
  description = "Database ingress"
  vpc_id = var.vpc_id
  ingress {
    description = "Database clients"
    protocol = "tcp"
    from_port = 5432
    to_port = 5432
    cidr_blocks = ["0.0.0.0/0"]
  }
}
