# Provider-free example for code review routing; provisions no infrastructure.
terraform {
  required_version = ">= 1.6.0, < 2.0.0"
}

variable "environment" {
  description = "Environment label for the review routing example."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "qa", "staging", "production"], var.environment)
    error_message = "Use dev, qa, staging, or production."
  }
}

output "environment" {
  description = "Validated environment label."
  value       = var.environment
}
