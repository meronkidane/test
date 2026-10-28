import pytest
from app.api import create_app
from app.catalog import Catalog

@pytest.mark.parametrize("demo,other", [("short", "other-token-with-at-least-24-characters"),
    ("same-token-with-at-least-24-characters", "same-token-with-at-least-24-characters")])
def test_weak_or_reused_tokens_reject_startup(monkeypatch, demo, other):
    monkeypatch.setenv("DEMO_API_TOKEN", demo)
    monkeypatch.setenv("OTHER_API_TOKEN", other)
    with pytest.raises(ValueError, match="distinct API tokens"):
        create_app(store=object(), catalog=Catalog.load("config/catalog.json"))

@pytest.mark.parametrize("missing", ["DEMO_API_TOKEN", "OTHER_API_TOKEN"])
def test_missing_token_rejects_startup(monkeypatch, missing):
    monkeypatch.setenv("DEMO_API_TOKEN", "demo-token-with-at-least-24-characters")
    monkeypatch.setenv("OTHER_API_TOKEN", "other-token-with-at-least-24-characters")
    monkeypatch.delenv(missing, raising=False)
    with pytest.raises(ValueError, match="requires a non-empty API token"):
        create_app(store=object(), catalog=Catalog.load("config/catalog.json"))

def test_empty_token_rejects_startup(monkeypatch):
    monkeypatch.setenv("DEMO_API_TOKEN", "")
    with pytest.raises(ValueError, match="requires a non-empty API token"):
        create_app(store=object(), catalog=Catalog.load("config/catalog.json"))
