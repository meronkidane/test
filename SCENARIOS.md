# Synthetic review scenario

ReportRepository concatenates request-controlled SQL values. ParameterizedReportRepository is the SQL-injection counterexample. Verify that an injection string is treated as a literal value and cannot read another tenant’s records.

These files are isolated review fixtures, not a deployed application. Compare the vulnerable implementation with its named counterexample. The automated review must distinguish evidence-backed findings from incomplete coverage. No merge or deployment is requested.
