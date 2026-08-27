from decimal import Decimal

from fastapi.testclient import TestClient

from app.itineraries import CreateItineraryRequest, _build_fallback_itinerary
from main import app


def test_fallback_itinerary_respects_day_count_and_budget() -> None:
    request = CreateItineraryRequest(
        destination="Huế",
        days=3,
        budget=Decimal("6000000"),
        travelerCount=2,
        groupProfile="FAMILY",
        preferences=["di sản", "ẩm thực"],
    )

    days, estimate = _build_fallback_itinerary(request)

    assert len(days) == 3
    assert [day["dayNumber"] for day in days] == [1, 2, 3]
    assert "Khám phá Huế" in days[0]["title"]
    assert estimate["total"] == "6000000"
    assert estimate["currency"] == "VND"
    assert sum(
        Decimal(estimate[key])
        for key in ("accommodation", "transport", "meals", "activities", "contingency")
    ) == Decimal(estimate["total"])


def test_ping_contract() -> None:
    response = TestClient(app).get("/v1/ai/ping")

    assert response.status_code == 200
    assert response.json()["service"] == "ai-service"
    assert response.json()["status"] == "UP"
