from decimal import Decimal

import pytest

from app.planner import PlannerInput, build_grounded_itinerary


@pytest.mark.parametrize(
    ("scenario", "expected_budget_status", "minimum_score"),
    [
        (
            PlannerInput(
                destination="Huế",
                days=3,
                budget=Decimal("6000000"),
                traveler_count=2,
                children_count=1,
                group_profile="FAMILY",
                preferences=["di sản", "ẩm thực"],
            ),
            {"WITHIN_BUDGET", "TIGHT"},
            80,
        ),
        (
            PlannerInput(
                destination="Đà Lạt",
                days=4,
                budget=Decimal("5000000"),
                traveler_count=2,
                group_profile="COUPLE",
                preferences=["thiên nhiên", "thư giãn"],
                pace="RELAXED",
            ),
            {"WITHIN_BUDGET", "TIGHT"},
            80,
        ),
        (
            PlannerInput(
                destination="Đà Nẵng – Hội An",
                days=5,
                budget=Decimal("8000000"),
                traveler_count=3,
                children_count=1,
                group_profile="FAMILY",
                preferences=["văn hóa"],
            ),
            {"OVER_BUDGET"},
            70,
        ),
    ],
)
def test_demo_scenarios_meet_quality_gate(
    scenario: PlannerInput,
    expected_budget_status: set[str],
    minimum_score: int,
) -> None:
    plan = build_grounded_itinerary(scenario)

    assert len(plan["itineraryDays"]) == scenario.days
    assert all(day["activities"] for day in plan["itineraryDays"])
    assert plan["costEstimate"]["status"] in expected_budget_status
    assert plan["qualitySummary"]["score"] >= minimum_score
    assert plan["qualitySummary"]["scheduleFeasible"] is True


def test_long_trip_is_transparently_scored_down_when_catalog_runs_out() -> None:
    plan = build_grounded_itinerary(
        PlannerInput(
            destination="Huế",
            days=12,
            budget=Decimal("25000000"),
            traveler_count=2,
            group_profile="FRIENDS",
            preferences=["văn hóa"],
        )
    )

    assert plan["qualitySummary"]["placeholderDays"] == 4
    assert plan["qualitySummary"]["score"] < 80
    assert any(
        activity.get("requiresUserConfirmation")
        for day in plan["itineraryDays"]
        for activity in day["activities"]
    )
