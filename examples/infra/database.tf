terraform {
  required_version = ">= 1.6.0, < 2.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

variable "vpc_id" {
  type        = string
  description = "Existing private application VPC."
  validation {
    condition     = can(regex("^vpc-[0-9a-f]+$", var.vpc_id))
    error_message = "Expected an existing VPC identifier."
  }
}
variable "application_security_group_id" {
  type        = string
  description = "Approved application security group in this VPC."
  validation {
    condition     = can(regex("^sg-[0-9a-f]+$", var.application_security_group_id))
    error_message = "Expected an application security group identifier."
  }
}
resource "aws_security_group" "database" {
  name_prefix = "review-db-"
  description = "Database ingress"
  vpc_id      = var.vpc_id
  ingress {
    description     = "Approved application clients only"
    protocol        = "tcp"
    from_port       = 5432
    to_port         = 5432
    security_groups = [var.application_security_group_id]
  }
}
