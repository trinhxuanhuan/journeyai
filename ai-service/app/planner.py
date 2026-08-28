from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP
from typing import Any

from app.catalog import catalog_metadata, normalize_text, resolve_destination


PACE_SLOTS = {
    "RELAXED": ["MORNING", "AFTERNOON"],
    "BALANCED": ["MORNING", "AFTERNOON", "EVENING"],
    "ACTIVE": ["MORNING", "MORNING", "AFTERNOON", "EVENING"],
}
PERIOD_START_MINUTES = {"MORNING": 8 * 60, "AFTERNOON": 13 * 60 + 30, "EVENING": 18 * 60}
TRANSPORT_SPEED_KMH = {
    "PUBLIC_TRANSPORT": 18,
    "TAXI_RIDESHARE": 25,
    "MOTORBIKE": 28,
    "PRIVATE_CAR": 30,
    "FLEXIBLE": 25,
}
TRANSPORT_LABELS = {
    "PUBLIC_TRANSPORT": "Phương tiện công cộng",
    "TAXI_RIDESHARE": "Taxi/xe công nghệ",
    "MOTORBIKE": "Xe máy",
    "PRIVATE_CAR": "Ô tô riêng",
    "FLEXIBLE": "Linh hoạt theo chặng",
}
TRANSPORT_COST_FACTORS = {
    "PUBLIC_TRANSPORT": Decimal("0.65"),
    "TAXI_RIDESHARE": Decimal("1.00"),
    "MOTORBIKE": Decimal("0.45"),
    "PRIVATE_CAR": Decimal("1.30"),
    "FLEXIBLE": Decimal("1.00"),
}


@dataclass(frozen=True)
class PlannerInput:
    destination: str
    days: int
    budget: Decimal
    traveler_count: int
    group_profile: str
    preferences: list[str] = field(default_factory=list)
    children_count: int = 0
    senior_count: int = 0
    pace: str = "BALANCED"
    transport_preference: str = "FLEXIBLE"
    start_date: date | None = None


def build_grounded_itinerary(
    request: PlannerInput,
    *,
    day_paces: dict[int, str] | None = None,
    excluded_place_names: set[str] | None = None,
    locked_days: dict[int, dict[str, Any]] | None = None,
) -> dict[str, Any]:
    profile = resolve_destination(request.destination)
    if profile is None:
        return _build_generic_itinerary(request)

    warnings: list[str] = []
    effective_transport = request.transport_preference
    if effective_transport == "MOTORBIKE" and (request.children_count > 0 or request.senior_count > 0):
        effective_transport = "TAXI_RIDESHARE"
        warnings.append(
            "Đã đổi phương tiện dự kiến từ xe máy sang taxi/xe công nghệ vì nhóm có trẻ em hoặc người cao tuổi."
        )
    if request.senior_count > 0 and request.pace == "ACTIVE":
        warnings.append("Nhịp độ ACTIVE có thể không phù hợp người cao tuổi; nên kiểm tra thể lực trước chuyến đi.")

    excluded = {normalize_text(item) for item in excluded_place_names or set()}
    places = [
        place
        for place in profile["places"]
        if normalize_text(place["name"]) not in excluded and place["id"] not in excluded
    ]
    ranked_places = sorted(places, key=lambda item: _place_score(item, request), reverse=True)
    areas = _rank_areas(ranked_places)
    used_place_ids: set[str] = {
        activity["placeId"]
        for locked_day in (locked_days or {}).values()
        for activity in locked_day.get("activities", [])
        if activity.get("placeId")
    }
    itinerary_days: list[dict[str, Any]] = []

    for day_number in range(1, request.days + 1):
        if locked_days and day_number in locked_days:
            itinerary_days.append(locked_days[day_number])
            continue

        day_pace = (day_paces or {}).get(day_number, request.pace)
        preferred_area = areas[(day_number - 1) % len(areas)] if areas else profile["name"]
        available_count = sum(place["id"] not in used_place_ids for place in ranked_places)
        future_unlocked_days = sum(
            1
            for future_day in range(day_number + 1, request.days + 1)
            if not locked_days or future_day not in locked_days
        )
        max_activities = max(
            0,
            min(
                len(PACE_SLOTS[day_pace]),
                available_count - future_unlocked_days,
            ),
        )
        day_plan = _build_day(
            day_number=day_number,
            start_date=request.start_date,
            pace=day_pace,
            preferred_area=preferred_area,
            ranked_places=ranked_places,
            used_place_ids=used_place_ids,
            request=request,
            effective_transport=effective_transport,
            max_activities=max_activities,
        )
        itinerary_days.append(day_plan)

    activity_total = sum(
        (
            Decimal(str(activity.get("estimatedCost", {}).get("amount", "0")))
            for day_plan in itinerary_days
            for activity in day_plan.get("activities", [])
        ),
        Decimal("0"),
    )
    cost_estimate, budget_adjustments = _optimize_activities_for_budget(
        request=request,
        profile=profile,
        itinerary_days=itinerary_days,
        activity_total=activity_total,
        effective_transport=effective_transport,
    )
    if cost_estimate["status"] == "OVER_BUDGET":
        warnings.append(
            "Dự toán đang vượt ngân sách. Hãy giảm hạng lưu trú, chọn thêm hoạt động miễn phí hoặc rút ngắn hành trình."
        )
    elif cost_estimate["status"] == "TIGHT":
        warnings.append("Ngân sách còn ít khoảng dự phòng; nên kiểm tra lại giá trước khi chốt chuyến đi.")
    if cost_estimate["optimizedToBudget"]:
        warnings.append(
            "Hệ thống đã tự điều chỉnh hạng chi tiêu xuống mức phù hợp hơn để bám sát ngân sách."
        )
    if budget_adjustments:
        warnings.append(
            f"Đã chuyển {len(budget_adjustments)} hoạt động trả phí thành thời gian linh hoạt để không vượt ngân sách."
        )

    if len(used_place_ids) < request.days:
        warnings.append("Số ngày dài hơn độ phủ dữ liệu nổi bật; một số ngày được thiết kế theo nhịp khám phá chậm.")

    metadata = catalog_metadata()
    warnings.append(metadata["referenceNotice"])
    placeholder_days = sum(
        not any(activity.get("placeId") for activity in day_plan.get("activities", []))
        for day_plan in itinerary_days
    )
    placeholder_activities = sum(
        activity.get("requiresUserConfirmation", False)
        for day_plan in itinerary_days
        for activity in day_plan.get("activities", [])
    )
    score = 92
    if cost_estimate["status"] == "OVER_BUDGET":
        score -= 20
    if len(used_place_ids) < min(request.days * 2, len(ranked_places)):
        score -= 8
    score -= min(placeholder_days * 4, 20)
    score -= min(placeholder_activities * 2, 10)

    return {
        "destinationDisplayName": profile["name"],
        "overview": (
            f"Hành trình {request.days} ngày tại {profile['name']} được sắp xếp theo nhịp "
            f"{request.pace.lower()} và ngân sách của nhóm."
        ),
        "itineraryDays": itinerary_days,
        "costEstimate": cost_estimate,
        "budgetAdjustments": budget_adjustments,
        "warnings": warnings,
        "assumptions": [
            "Ngân sách là tổng chi phí dự kiến cho cả nhóm.",
            "Chi phí chưa bao gồm phương tiện từ nơi ở của khách tới điểm đến.",
            "Lưu trú được ước tính theo hai khách một phòng; phòng lẻ được làm tròn lên.",
            (
                "Thời gian di chuyển được ước tính theo khoảng cách và phương tiện ưu tiên, "
                "chưa phản ánh giao thông thời gian thực."
            ),
        ],
        "qualitySummary": {
            "score": max(score, 0),
            "catalogCoverage": "CURATED",
            "budgetFit": cost_estimate["status"] != "OVER_BUDGET",
            "scheduleFeasible": True,
            "placeholderDays": placeholder_days,
            "placeholderActivities": placeholder_activities,
        },
        "plannerVersion": "1.0.0",
        "schemaVersion": "2.0",
        "catalogVersion": metadata["catalogVersion"],
        "effectiveTransport": effective_transport,
    }


def _build_day(
    *,
    day_number: int,
    start_date: date | None,
    pace: str,
    preferred_area: str,
    ranked_places: list[dict[str, Any]],
    used_place_ids: set[str],
    request: PlannerInput,
    effective_transport: str,
    max_activities: int,
) -> dict[str, Any]:
    activities: list[dict[str, Any]] = []
    previous_place: dict[str, Any] | None = None
    current_end = 7 * 60 + 30

    for desired_period in PACE_SLOTS[pace][:max_activities]:
        place = _choose_place(
            ranked_places=ranked_places,
            used_place_ids=used_place_ids,
            preferred_area=preferred_area,
            desired_period=desired_period,
            previous_place=previous_place,
            request=request,
        )
        actual_period = desired_period
        if place is None:
            place = _choose_place(
                ranked_places=ranked_places,
                used_place_ids=used_place_ids,
                preferred_area=preferred_area,
                desired_period=None,
                previous_place=previous_place,
                request=request,
            )
            if place is not None:
                actual_period = place["periods"][0]
        if place is None:
            continue

        travel_minutes = _travel_minutes(previous_place, place, effective_transport)
        start_minutes = max(PERIOD_START_MINUTES[actual_period], current_end + travel_minutes)
        end_minutes = start_minutes + int(place["durationMinutes"])
        if end_minutes > 21 * 60 + 30:
            continue

        amount = _activity_cost(place, request)
        activities.append(
            {
                "placeId": place["id"],
                "period": actual_period,
                "startTime": _clock(start_minutes),
                "endTime": _clock(end_minutes),
                "placeName": place["name"],
                "area": place["area"],
                "category": place["category"],
                "suggestion": place["description"],
                "description": place["description"],
                "whyRecommended": _why_recommended(place, request),
                "culturalNote": place["culturalNote"],
                "durationMinutes": int(place["durationMinutes"]),
                "estimatedCost": {"amount": _money(amount), "currency": "VND"},
                "travelFromPrevious": {
                    "minutes": travel_minutes,
                    "mode": effective_transport,
                    "label": TRANSPORT_LABELS[effective_transport],
                },
                "location": {"latitude": place["latitude"], "longitude": place["longitude"]},
                "setting": place["setting"],
                "dataSource": "VIET_KHAM_PHA_CURATED_V1",
                "referenceOnly": True,
            }
        )
        used_place_ids.add(place["id"])
        previous_place = place
        current_end = end_minutes

    if not activities:
        activities.append(
            {
                "placeId": None,
                "period": "MORNING",
                "startTime": "09:00",
                "endTime": "11:00",
                "placeName": None,
                "area": preferred_area,
                "category": "FLEXIBLE_TIME",
                "suggestion": (
                    "Dành thời gian nghỉ ngơi hoặc chọn thêm một trải nghiệm địa phương "
                    "sau khi kiểm tra thông tin thực tế."
                ),
                "description": "Khoảng thời gian linh hoạt giúp hành trình không bị quá tải.",
                "whyRecommended": "Giữ khoảng trống để thích ứng với thời tiết, sức khỏe và trải nghiệm phát sinh.",
                "culturalNote": "Ưu tiên dịch vụ địa phương có thông tin và giá công khai.",
                "durationMinutes": 120,
                "estimatedCost": {"amount": "0", "currency": "VND"},
                "travelFromPrevious": {
                    "minutes": 0,
                    "mode": effective_transport,
                    "label": TRANSPORT_LABELS[effective_transport],
                },
                "location": None,
                "setting": "FLEXIBLE",
                "dataSource": "PLANNER_PLACEHOLDER",
                "referenceOnly": True,
                "requiresUserConfirmation": True,
            }
        )

    day_date = start_date + timedelta(days=day_number - 1) if start_date else None
    areas = list(dict.fromkeys(activity["area"] for activity in activities))
    title_area = " – ".join(areas) if areas else preferred_area
    return {
        "dayNumber": day_number,
        "date": day_date.isoformat() if day_date else None,
        "title": f"Ngày {day_number}: {title_area}",
        "theme": _day_theme(activities, pace),
        "pace": pace,
        "activities": activities,
        "dailyActivityCost": {
            "amount": _money(
                sum(
                    (Decimal(activity["estimatedCost"]["amount"]) for activity in activities),
                    Decimal("0"),
                )
            ),
            "currency": "VND",
        },
    }


def _choose_place(
    *,
    ranked_places: list[dict[str, Any]],
    used_place_ids: set[str],
    preferred_area: str,
    desired_period: str | None,
    previous_place: dict[str, Any] | None,
    request: PlannerInput,
) -> dict[str, Any] | None:
    candidates = [
        place
        for place in ranked_places
        if place["id"] not in used_place_ids
        and (desired_period is None or desired_period in place["periods"])
        and not _unsuitable_for_group(place, request)
    ]
    if not candidates:
        return None

    def route_score(place: dict[str, Any]) -> float:
        area_bonus = 18 if place["area"] == preferred_area else 0
        distance_penalty = _distance_km(previous_place, place) if previous_place else 0
        return _place_score(place, request) + area_bonus - distance_penalty

    return max(candidates, key=route_score)


def _place_score(place: dict[str, Any], request: PlannerInput) -> float:
    searchable = normalize_text(
        " ".join([place["name"], place["category"], *place.get("tags", [])])
    )
    score = 10.0
    for preference in request.preferences:
        normalized_preference = normalize_text(preference)
        if normalized_preference and normalized_preference in searchable:
            score += 8
    if request.group_profile in place.get("suitableFor", []):
        score += 5
    if request.children_count > 0 and "FAMILY" in place.get("suitableFor", []):
        score += 4
    if request.senior_count > 0 and "SENIORS" in place.get("suitableFor", []):
        score += 4
    if _budget_tier(request) == "ECONOMY":
        score -= float(place["adultCost"]) / 10000
    return score


def _unsuitable_for_group(place: dict[str, Any], request: PlannerInput) -> bool:
    if request.children_count > 0 and request.group_profile == "FAMILY":
        return "FAMILY" not in place.get("suitableFor", [])
    if request.senior_count > 0:
        return "SENIORS" not in place.get("suitableFor", [])
    return False


def _rank_areas(places: list[dict[str, Any]]) -> list[str]:
    totals: dict[str, float] = {}
    for index, place in enumerate(places):
        totals.setdefault(place["area"], 0)
        totals[place["area"]] += max(100 - index, 1)
    return sorted(totals, key=totals.get, reverse=True)


def _activity_cost(place: dict[str, Any], request: PlannerInput) -> Decimal:
    adult_count = request.traveler_count - request.children_count
    return Decimal(place["adultCost"]) * adult_count + Decimal(place["childCost"]) * request.children_count


def _build_cost_estimate(
    *,
    request: PlannerInput,
    profile: dict[str, Any],
    activity_total: Decimal,
    effective_transport: str,
) -> dict[str, Any]:
    requested_tier = _budget_tier(request)
    tier_order = ["ECONOMY", "COMFORT", "PREMIUM"]
    requested_index = tier_order.index(requested_tier)
    candidates = list(reversed(tier_order[: requested_index + 1]))
    selected: dict[str, Any] | None = None

    for tier in candidates:
        candidate = _calculate_costs_for_tier(
            request=request,
            daily=profile["dailyCosts"][tier],
            activity_total=activity_total,
            effective_transport=effective_transport,
        )
        candidate["travelStyle"] = tier
        selected = candidate
        if Decimal(candidate["total"]) <= request.budget:
            break

    assert selected is not None
    total = Decimal(selected["total"])
    remaining = _rounded(request.budget - total)
    if total > request.budget:
        budget_status = "OVER_BUDGET"
    elif remaining < request.budget * Decimal("0.10"):
        budget_status = "TIGHT"
    else:
        budget_status = "WITHIN_BUDGET"

    return {
        **selected,
        "budget": _money(request.budget),
        "remaining": _money(remaining),
        "currency": "VND",
        "status": budget_status,
        "requestedTravelStyle": requested_tier,
        "optimizedToBudget": selected["travelStyle"] != requested_tier,
    }


def _optimize_activities_for_budget(
    *,
    request: PlannerInput,
    profile: dict[str, Any],
    itinerary_days: list[dict[str, Any]],
    activity_total: Decimal,
    effective_transport: str,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    estimate = _build_cost_estimate(
        request=request,
        profile=profile,
        activity_total=activity_total,
        effective_transport=effective_transport,
    )
    if estimate["status"] != "OVER_BUDGET":
        return estimate, []

    fixed_cost_estimate = _build_cost_estimate(
        request=request,
        profile=profile,
        activity_total=Decimal("0"),
        effective_transport=effective_transport,
    )
    if fixed_cost_estimate["status"] == "OVER_BUDGET":
        return estimate, []

    adjustments: list[dict[str, Any]] = []
    while estimate["status"] == "OVER_BUDGET":
        overage = Decimal(estimate["total"]) - request.budget
        required_reduction = overage / Decimal("1.08")
        paid_activities = [
            (Decimal(activity["estimatedCost"]["amount"]), day_plan, index, activity)
            for day_plan in itinerary_days
            for index, activity in enumerate(day_plan["activities"])
            if Decimal(activity.get("estimatedCost", {}).get("amount", "0")) > 0
            and activity.get("placeId")
        ]
        if not paid_activities:
            break
        sufficient = [item for item in paid_activities if item[0] >= required_reduction]
        selected = (
            min(sufficient, key=lambda item: item[0])
            if sufficient
            else max(paid_activities, key=lambda item: item[0])
        )
        selected_cost, day_plan, activity_index, activity = selected
        adjustments.append(
            {
                "dayNumber": day_plan["dayNumber"],
                "placeId": activity["placeId"],
                "placeName": activity["placeName"],
                "savedAmount": _money(selected_cost),
                "currency": "VND",
                "reason": "BUDGET_FIT",
            }
        )
        day_plan["activities"][activity_index] = _budget_flexible_activity(activity)
        day_plan["dailyActivityCost"] = {
            "amount": _money(
                sum(
                    (
                        Decimal(item.get("estimatedCost", {}).get("amount", "0"))
                        for item in day_plan["activities"]
                    ),
                    Decimal("0"),
                )
            ),
            "currency": "VND",
        }
        activity_total -= selected_cost
        estimate = _build_cost_estimate(
            request=request,
            profile=profile,
            activity_total=activity_total,
            effective_transport=effective_transport,
        )

    return estimate, adjustments


def _budget_flexible_activity(activity: dict[str, Any]) -> dict[str, Any]:
    return {
        "placeId": None,
        "period": activity["period"],
        "startTime": activity["startTime"],
        "endTime": activity["endTime"],
        "placeName": None,
        "area": activity["area"],
        "category": "FLEXIBLE_TIME",
        "suggestion": (
            f"Dành thời gian linh hoạt; {activity['placeName']} là lựa chọn bổ sung nếu ngân sách thực tế cho phép."
        ),
        "description": "Khoảng thời gian dự phòng để hành trình bám sát ngân sách đã đặt.",
        "whyRecommended": "Giảm chi phí dự kiến mà vẫn giữ nhịp nghỉ hợp lý trong ngày.",
        "culturalNote": activity.get("culturalNote"),
        "durationMinutes": activity["durationMinutes"],
        "estimatedCost": {"amount": "0", "currency": "VND"},
        "travelFromPrevious": activity.get("travelFromPrevious"),
        "location": None,
        "setting": "FLEXIBLE",
        "dataSource": "BUDGET_OPTIMIZER",
        "referenceOnly": True,
        "requiresUserConfirmation": True,
        "budgetAlternative": {
            "placeId": activity["placeId"],
            "placeName": activity["placeName"],
            "estimatedCost": activity["estimatedCost"],
        },
    }


def _calculate_costs_for_tier(
    *,
    request: PlannerInput,
    daily: dict[str, int],
    activity_total: Decimal,
    effective_transport: str,
) -> dict[str, str]:
    rooms = max(1, math.ceil(request.traveler_count / 2))
    nights = max(request.days - 1, 0)
    adult_equivalents = Decimal(request.traveler_count - request.children_count) + (
        Decimal(request.children_count) * Decimal("0.65")
    )
    accommodation = Decimal(daily["roomPerNight"]) * rooms * nights
    meals = Decimal(daily["mealsPerAdult"]) * adult_equivalents * request.days
    vehicle_units = max(1, math.ceil(request.traveler_count / 4))
    transport = (
        Decimal(daily["localTransportPerGroup"])
        * request.days
        * vehicle_units
        * TRANSPORT_COST_FACTORS[effective_transport]
    )
    subtotal = accommodation + meals + transport + activity_total
    contingency = subtotal * Decimal("0.08")
    total = _rounded(subtotal + contingency)
    return {
        "accommodation": _money(accommodation),
        "transport": _money(transport),
        "meals": _money(meals),
        "activities": _money(activity_total),
        "contingency": _money(contingency),
        "total": _money(total),
    }


def _build_generic_itinerary(request: PlannerInput) -> dict[str, Any]:
    preference_text = ", ".join(request.preferences) if request.preferences else "văn hóa địa phương"
    itinerary_days: list[dict[str, Any]] = []
    for day_number in range(1, request.days + 1):
        day_date = request.start_date + timedelta(days=day_number - 1) if request.start_date else None
        itinerary_days.append(
            {
                "dayNumber": day_number,
                "date": day_date.isoformat() if day_date else None,
                "title": f"Ngày {day_number}: Khám phá {request.destination}",
                "theme": preference_text,
                "pace": request.pace,
                "activities": [
                    {
                        "period": "MORNING",
                        "startTime": "09:00",
                        "endTime": "11:00",
                        "suggestion": f"Khám phá một điểm nổi bật đã được kiểm tra tại {request.destination}",
                        "description": "Chọn điểm phù hợp sau khi kiểm tra giờ mở cửa và điều kiện thực tế.",
                        "estimatedCost": {"amount": "0", "currency": "VND"},
                        "referenceOnly": True,
                    },
                    {
                        "period": "AFTERNOON",
                        "startTime": "14:30",
                        "endTime": "16:30",
                        "suggestion": f"Trải nghiệm {preference_text}",
                        "description": "Ưu tiên đơn vị địa phương có thông tin và giá công khai.",
                        "estimatedCost": {"amount": "0", "currency": "VND"},
                        "referenceOnly": True,
                    },
                ],
                "dailyActivityCost": {"amount": "0", "currency": "VND"},
            }
        )

    accommodation = request.budget * Decimal("0.35")
    transport = request.budget * Decimal("0.20")
    meals = request.budget * Decimal("0.25")
    activities = request.budget * Decimal("0.12")
    contingency = request.budget - accommodation - transport - meals - activities
    estimate = {
        "accommodation": _money(accommodation),
        "transport": _money(transport),
        "meals": _money(meals),
        "activities": _money(activities),
        "contingency": _money(contingency),
        "total": _money(request.budget),
        "budget": _money(request.budget),
        "remaining": "0",
        "currency": "VND",
        "status": "TIGHT",
        "travelStyle": _budget_tier(request),
        "requestedTravelStyle": _budget_tier(request),
        "optimizedToBudget": False,
    }
    metadata = catalog_metadata()
    return {
        "destinationDisplayName": request.destination,
        "overview": (
            f"Khung hành trình {request.days} ngày tại {request.destination}; "
            "cần xác nhận lại địa điểm và dịch vụ trước chuyến đi."
        ),
        "itineraryDays": itinerary_days,
        "costEstimate": estimate,
        "budgetAdjustments": [],
        "warnings": [
            "Điểm đến chưa nằm trong bộ dữ liệu được Việt Khám Phá kiểm duyệt; lịch trình này chỉ là khung tham khảo.",
            metadata["referenceNotice"],
        ],
        "assumptions": ["Các hạng mục chi phí được phân bổ theo tỷ lệ ngân sách, chưa phải báo giá thực tế."],
        "qualitySummary": {
            "score": 55,
            "catalogCoverage": "GENERIC",
            "budgetFit": True,
            "scheduleFeasible": True,
            "placeholderDays": request.days,
            "placeholderActivities": request.days * 2,
        },
        "plannerVersion": "1.0.0",
        "schemaVersion": "2.0",
        "catalogVersion": metadata["catalogVersion"],
        "effectiveTransport": request.transport_preference,
    }


def _budget_tier(request: PlannerInput) -> str:
    daily_per_person = request.budget / Decimal(request.traveler_count * request.days)
    if daily_per_person < Decimal("700000"):
        return "ECONOMY"
    if daily_per_person < Decimal("1600000"):
        return "COMFORT"
    return "PREMIUM"


def _why_recommended(place: dict[str, Any], request: PlannerInput) -> str:
    searchable_tags = {normalize_text(tag): tag for tag in place.get("tags", [])}
    matches = [
        original
        for normalized, original in searchable_tags.items()
        if any(normalize_text(preference) in normalized for preference in request.preferences)
    ]
    if matches:
        return f"Phù hợp sở thích {', '.join(matches[:2])} của nhóm."
    if request.children_count > 0 and "FAMILY" in place.get("suitableFor", []):
        return "Hoạt động phù hợp nhóm gia đình và có thể điều chỉnh nhịp độ cho trẻ em."
    if request.senior_count > 0 and "SENIORS" in place.get("suitableFor", []):
        return "Điểm đến phù hợp nhóm có người cao tuổi khi duy trì nhịp tham quan vừa phải."
    return "Điểm tiêu biểu giúp hành trình cân bằng giữa trải nghiệm và tìm hiểu địa phương."


def _day_theme(activities: list[dict[str, Any]], pace: str) -> str:
    categories = list(dict.fromkeys(activity["category"] for activity in activities))
    if not categories:
        return "Khám phá địa phương theo nhịp riêng"
    labels = {
        "CULTURE": "văn hóa",
        "HERITAGE": "di sản",
        "MUSEUM": "bảo tàng",
        "FOOD": "ẩm thực",
        "NATURE": "thiên nhiên",
        "ADVENTURE": "trải nghiệm",
        "RELAXATION": "thư giãn",
        "LOCAL_LIFE": "đời sống địa phương",
    }
    theme = " và ".join(labels.get(item, item.lower()) for item in categories[:2])
    pace_label = {"RELAXED": "thư thả", "BALANCED": "cân bằng", "ACTIVE": "năng động"}[pace]
    return f"{theme.capitalize()} theo nhịp {pace_label}"


def _travel_minutes(
    origin: dict[str, Any] | None,
    destination: dict[str, Any],
    transport: str,
) -> int:
    if origin is None:
        return 0
    distance = _distance_km(origin, destination)
    minutes = math.ceil((distance / TRANSPORT_SPEED_KMH[transport]) * 60 + 10)
    return min(max(minutes, 10), 180)


def _distance_km(origin: dict[str, Any] | None, destination: dict[str, Any]) -> float:
    if origin is None:
        return 0
    lat1 = math.radians(float(origin["latitude"]))
    lon1 = math.radians(float(origin["longitude"]))
    lat2 = math.radians(float(destination["latitude"]))
    lon2 = math.radians(float(destination["longitude"]))
    delta_lat = lat2 - lat1
    delta_lon = lon2 - lon1
    haversine = math.sin(delta_lat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2
    return 6371 * 2 * math.atan2(math.sqrt(haversine), math.sqrt(1 - haversine))


def _clock(minutes: int) -> str:
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


def _rounded(value: Decimal | int) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("1000"), rounding=ROUND_HALF_UP)


def _money(value: Decimal | int) -> str:
    return str(_rounded(value).quantize(Decimal("1")))
