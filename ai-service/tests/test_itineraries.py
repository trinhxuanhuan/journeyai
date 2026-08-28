from datetime import date
from decimal import Decimal

import pytest
from bson import ObjectId
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.catalog import catalog_metadata, resolve_destination
from app.itineraries import (
    CreateItineraryRequest,
    _apply_ai_narratives,
    _build_fallback_itinerary,
    interpret_refinement,
)
from app.planner import build_grounded_itinerary
from main import app


class _Result:
    def __init__(self, *, inserted_id=None, matched_count=0) -> None:
        self.inserted_id = inserted_id
        self.matched_count = matched_count


class _Cursor:
    def __init__(self, items: list[dict]) -> None:
        self.items = items
        self.index = 0

    def sort(self, *_):
        return self

    def skip(self, count: int):
        self.items = self.items[count:]
        return self

    def limit(self, count: int):
        self.items = self.items[:count]
        return self

    def __aiter__(self):
        return self

    async def __anext__(self):
        if self.index >= len(self.items):
            raise StopAsyncIteration
        item = self.items[self.index]
        self.index += 1
        return dict(item)


class _FakeCollection:
    def __init__(self) -> None:
        self.documents: dict[ObjectId, dict] = {}

    async def insert_one(self, document: dict) -> _Result:
        object_id = ObjectId()
        stored = dict(document)
        stored["_id"] = object_id
        self.documents[object_id] = stored
        return _Result(inserted_id=object_id)

    async def find_one(self, query: dict):
        return next((dict(item) for item in self.documents.values() if self._matches(item, query)), None)

    async def update_one(self, query: dict, update: dict) -> _Result:
        for object_id, item in self.documents.items():
            if not self._matches(item, query):
                continue
            item.update(update.get("$set", {}))
            for key, value in update.get("$push", {}).items():
                item.setdefault(key, []).append(value)
            self.documents[object_id] = item
            return _Result(matched_count=1)
        return _Result(matched_count=0)

    def find(self, query: dict) -> _Cursor:
        return _Cursor([dict(item) for item in self.documents.values() if self._matches(item, query)])

    async def count_documents(self, query: dict) -> int:
        return sum(self._matches(item, query) for item in self.documents.values())

    @staticmethod
    def _matches(document: dict, query: dict) -> bool:
        return all(document.get(key) == value for key, value in query.items())


def _request(**overrides) -> CreateItineraryRequest:
    payload = {
        "destination": "Huế",
        "days": 3,
        "budget": Decimal("6000000"),
        "travelerCount": 2,
        "groupProfile": "FAMILY",
        "preferences": ["di sản", "ẩm thực"],
        **overrides,
    }
    return CreateItineraryRequest.model_validate(payload)


def test_grounded_itinerary_respects_day_count_and_exposes_real_estimate() -> None:
    days, estimate = _build_fallback_itinerary(_request())

    assert len(days) == 3
    assert [day["dayNumber"] for day in days] == [1, 2, 3]
    assert all(day["activities"] for day in days)
    assert all(
        activity["dataSource"] == "VIET_KHAM_PHA_CURATED_V1"
        for day in days
        for activity in day["activities"]
    )
    assert estimate["currency"] == "VND"
    assert estimate["budget"] == "6000000"
    assert estimate["status"] in {"WITHIN_BUDGET", "TIGHT", "OVER_BUDGET"}
    component_total = sum(
        Decimal(estimate[key])
        for key in ("accommodation", "transport", "meals", "activities", "contingency")
    )
    assert abs(component_total - Decimal(estimate["total"])) <= Decimal("4000")


def test_family_transport_and_activity_selection_are_safely_adjusted() -> None:
    request = _request(
        destination="Đà Lạt",
        childrenCount=1,
        transportPreference="MOTORBIKE",
        pace="ACTIVE",
    )

    plan = build_grounded_itinerary(request.to_planner_input())

    assert plan["effectiveTransport"] == "TAXI_RIDESHARE"
    assert any("trẻ em" in warning for warning in plan["warnings"])
    selected_ids = {
        activity["placeId"]
        for day in plan["itineraryDays"]
        for activity in day["activities"]
    }
    assert "dl-thac-datanla" not in selected_ids


def test_schedule_has_dates_and_does_not_overlap_inside_each_day() -> None:
    request = _request(startDate=date(2026, 10, 10), pace="ACTIVE")

    plan = build_grounded_itinerary(request.to_planner_input())

    assert [item["date"] for item in plan["itineraryDays"]] == [
        "2026-10-10",
        "2026-10-11",
        "2026-10-12",
    ]
    for day in plan["itineraryDays"]:
        activities = day["activities"]
        for previous, current in zip(activities, activities[1:]):
            assert previous["endTime"] <= current["startTime"]


def test_unknown_destination_is_explicitly_marked_as_generic() -> None:
    request = _request(destination="Một điểm đến chưa kiểm duyệt")

    plan = build_grounded_itinerary(request.to_planner_input())

    assert plan["qualitySummary"]["catalogCoverage"] == "GENERIC"
    assert plan["qualitySummary"]["score"] < 70
    assert any("chưa nằm trong bộ dữ liệu" in warning for warning in plan["warnings"])


def test_refinement_parser_understands_vietnamese_constraints() -> None:
    decision = interpret_refinement(
        "Làm ngày 2 nhẹ hơn, bỏ Đại Nội Huế và giảm ngân sách xuống 5 triệu cho trẻ em",
        destination="Huế",
        current_budget=Decimal("6000000"),
    )

    assert decision["targetDay"] == 2
    assert decision["pace"] == "RELAXED"
    assert decision["budget"] == Decimal("5000000")
    assert "Đại Nội Huế" in decision["excludedPlaceNames"]
    assert "phù hợp trẻ em" in decision["addedPreferences"]


def test_ai_narrative_cannot_change_grounded_fields() -> None:
    plan = build_grounded_itinerary(_request(days=1).to_planner_input())
    activity = plan["itineraryDays"][0]["activities"][0]
    original_place_id = activity["placeId"]
    original_cost = dict(activity["estimatedCost"])

    _apply_ai_narratives(
        plan,
        {
            "overview": "Một ngày khám phá Huế vừa đủ sâu và không quá vội.",
            "days": [
                {
                    "dayNumber": 1,
                    "title": "Chạm vào chiều sâu di sản Huế",
                    "theme": "Di sản và ký ức cố đô",
                    "activities": [
                        {
                            "placeId": original_place_id,
                            "description": "Tìm hiểu di sản trong bối cảnh lịch sử thay vì chỉ ghé qua để chụp ảnh.",
                            "whyRecommended": "Phù hợp mong muốn khám phá văn hóa của nhóm.",
                            "estimatedCost": {"amount": "999999999", "currency": "VND"},
                        }
                    ],
                }
            ],
        },
    )

    enhanced = plan["itineraryDays"][0]["activities"][0]
    assert enhanced["placeId"] == original_place_id
    assert enhanced["estimatedCost"] == original_cost
    assert enhanced["description"].startswith("Tìm hiểu di sản")


def test_group_composition_cannot_exceed_traveler_count() -> None:
    with pytest.raises(ValidationError):
        _request(travelerCount=2, childrenCount=1, seniorCount=2)


def test_catalog_resolves_vietnamese_aliases() -> None:
    assert resolve_destination("Du lịch Đà Nẵng và Hội An")["id"] == "danang-hoian"
    assert resolve_destination("HA NOI")["id"] == "hanoi-ninhbinh"
    assert len(catalog_metadata()["supportedDestinations"]) == 4


def test_ping_contract() -> None:
    response = TestClient(app).get("/v1/ai/ping")

    assert response.status_code == 200
    assert response.json()["service"] == "ai-service"
    assert response.json()["status"] == "UP"


def test_create_refine_and_share_contract(monkeypatch) -> None:
    fake_collection = _FakeCollection()
    monkeypatch.setattr("app.itineraries.collection", fake_collection)
    client = TestClient(app)
    headers = {"X-User-Id": "customer-1"}

    created = client.post(
        "/v1/ai/itineraries",
        headers=headers,
        json={
            "destination": "Huế",
            "days": 3,
            "budget": 6000000,
            "travelerCount": 2,
            "childrenCount": 1,
            "groupProfile": "FAMILY",
            "preferences": ["di sản", "ẩm thực"],
        },
    )
    assert created.status_code == 201
    created_body = created.json()
    assert created_body["revision"] == 1
    assert created_body["qualitySummary"]["catalogCoverage"] == "CURATED"
    assert all("shareToken" not in document for document in fake_collection.documents.values())

    second_created = client.post(
        "/v1/ai/itineraries",
        headers=headers,
        json={
            "destination": "Huế",
            "days": 2,
            "budget": 4000000,
            "travelerCount": 1,
        },
    )
    assert second_created.status_code == 201
    assert all("shareToken" not in document for document in fake_collection.documents.values())

    refined = client.post(
        f"/v1/ai/itineraries/{created_body['id']}/refine",
        headers=headers,
        json={
            "instruction": "Làm ngày 2 nhẹ hơn và giảm ngân sách xuống 5 triệu",
            "lockedDayNumbers": [1],
        },
    )
    assert refined.status_code == 200
    refined_body = refined.json()
    assert refined_body["revision"] == 2
    assert refined_body["itineraryDays"][0] == created_body["itineraryDays"][0]
    assert refined_body["itineraryDays"][1]["pace"] == "RELAXED"

    refined_again = client.post(
        f"/v1/ai/itineraries/{created_body['id']}/refine",
        headers=headers,
        json={"instruction": "Ưu tiên thêm trải nghiệm ẩm thực địa phương"},
    )
    assert refined_again.status_code == 200
    assert refined_again.json()["revision"] == 3
    assert refined_again.json()["itineraryDays"][1]["pace"] == "RELAXED"

    shared = client.post(f"/v1/ai/itineraries/{created_body['id']}/share", headers=headers)
    assert shared.status_code == 200
    public = client.get(shared.json()["sharePath"])
    assert public.status_code == 200
    assert "userId" not in public.json()
    assert "refinementHistory" not in public.json()
