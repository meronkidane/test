# Review example only; validation does not provision AWS resources.
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
  description = "Existing VPC for the isolated review example. No default VPC is assumed."
  type        = string
  validation {
    condition     = can(regex("^vpc-[0-9a-f]+$", var.vpc_id))
    error_message = "Supply an existing VPC identifier."
  }
}

resource "aws_security_group" "isolated" {
  name_prefix = "review-fixture-isolated-"
  description = "No inbound or outbound rules; connectivity must be explicitly reviewed"
  vpc_id      = var.vpc_id
  ingress     = []
  egress      = []
}

resource "aws_iam_policy" "artifact_reader" {
  name_prefix = "review-fixture-artifact-reader-"
  description = "Read-only access to the approved artifact prefix; not attached to any principal"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = ["arn:aws:s3:::review-fixture-artifacts/approved/*"]
    }]
  })
}
