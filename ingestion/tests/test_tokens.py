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

def test_missing_token_rejects_startup(monkeypatch):
    monkeypatch.delenv("DEMO_API_TOKEN", raising=False)
    with pytest.raises(KeyError):
        create_app(store=object(), catalog=Catalog.load("config/catalog.json"))
