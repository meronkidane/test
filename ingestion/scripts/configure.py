"""Create local credentials once; never print their values."""
import os
import secrets
from pathlib import Path
path = Path(".env")
fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
with os.fdopen(fd, "w") as file:
    for name in ("DB_PASSWORD", "DEMO_API_TOKEN", "OTHER_API_TOKEN"):
        file.write(f"{name}={secrets.token_hex(24)}\n")
print("Created .env with local-only random credentials")
