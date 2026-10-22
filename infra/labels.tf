# Provider-free review fixture: creates no cloud resources.
terraform {
  required_version = ">= 1.6.0, < 2.0.0"
}
locals {
  labels = {
    application = "review-test"
    environment = "dev"
  }
}
output "labels" {
  description = "Static labels used by this isolated review example."
  value       = local.labels
}
