from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from decimal import Decimal
from secrets import token_urlsafe
from typing import Any, Literal

from bson import ObjectId
from fastapi import APIRouter, Header, HTTPException, Query, status
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

from app.config import settings


router = APIRouter(prefix="/v1/ai", tags=["AI Itinerary"])
mongo_client = AsyncIOMotorClient(settings.mongo_uri)
collection = mongo_client[settings.mongo_database]["ai_itineraries"]
logger = logging.getLogger(__name__)


async def ensure_indexes() -> None:
    await collection.create_index([("userId", 1), ("createdAt", -1)])
    await collection.create_index("shareToken", unique=True, sparse=True)


class CreateItineraryRequest(BaseModel):
    destination: str = Field(min_length=2, max_length=120)
    days: int = Field(ge=1, le=30)
    budget: Decimal = Field(gt=0)
    traveler_count: int = Field(default=1, ge=1, le=30, alias="travelerCount")
    group_profile: Literal["SOLO", "COUPLE", "FAMILY", "FRIENDS", "SENIORS"] = Field(
        default="FRIENDS", alias="groupProfile"
    )
    preferences: list[str] = Field(default_factory=list, max_length=12)

    model_config = {"populate_by_name": True}


class ShareResponse(BaseModel):
    share_token: str = Field(alias="shareToken")
    share_path: str = Field(alias="sharePath")

    model_config = {"populate_by_name": True}


def _serialize(document: dict[str, Any]) -> dict[str, Any]:
    result = dict(document)
    result["id"] = str(result.pop("_id"))
    result.pop("shareToken", None)
    return result


def _build_fallback_itinerary(request: CreateItineraryRequest) -> tuple[list[dict[str, Any]], dict[str, str]]:
    preference_text = ", ".join(request.preferences) if request.preferences else "văn hóa địa phương"
    days: list[dict[str, Any]] = []
    for day_number in range(1, request.days + 1):
        days.append(
            {
                "dayNumber": day_number,
                "title": f"Ngày {day_number}: Khám phá {request.destination}",
                "activities": [
                    {"period": "MORNING", "suggestion": f"Khám phá điểm nổi bật tại {request.destination}"},
                    {"period": "AFTERNOON", "suggestion": f"Trải nghiệm {preference_text}"},
                    {"period": "EVENING", "suggestion": "Thưởng thức ẩm thực và tự do tham quan"},
                ],
            }
        )

    budget = request.budget
    estimate = {
        "accommodation": str((budget * Decimal("0.35")).quantize(Decimal("1"))),
        "transport": str((budget * Decimal("0.20")).quantize(Decimal("1"))),
        "meals": str((budget * Decimal("0.25")).quantize(Decimal("1"))),
        "activities": str((budget * Decimal("0.15")).quantize(Decimal("1"))),
        "contingency": str((budget * Decimal("0.05")).quantize(Decimal("1"))),
        "total": str(budget.quantize(Decimal("1"))),
        "currency": "VND",
    }
    return days, estimate


async def _generate_itinerary(
    request: CreateItineraryRequest,
) -> tuple[list[dict[str, Any]], dict[str, str], str]:
    if not settings.gemini_api_key:
        days, estimate = _build_fallback_itinerary(request)
        return days, estimate, "RULE_BASED_FALLBACK"

    prompt = f"""
    Bạn là chuyên gia du lịch Việt Nam của Việt Khám Phá. Hãy tạo lịch trình thực tế,
    tôn trọng văn hóa địa phương và không bịa thông tin đặt chỗ.
    Điểm đến: {request.destination}; số ngày: {request.days}; ngân sách tổng: {request.budget} VND;
    số khách: {request.traveler_count}; nhóm: {request.group_profile};
    sở thích: {', '.join(request.preferences) if request.preferences else 'văn hóa địa phương'}.
    Chỉ trả JSON gồm itineraryDays và costEstimate. itineraryDays phải có đúng {request.days} phần tử;
    mỗi ngày có dayNumber, title và activities (period, suggestion). costEstimate gồm accommodation,
    transport, meals, activities, contingency, total và currency=VND. Các khoản tiền là chuỗi số nguyên,
    tổng không vượt quá ngân sách.
    """.strip()

    try:
        async with genai.Client(api_key=settings.gemini_api_key).aio as client:
            response = await client.models.generate_content(
                model=settings.gemini_model,
                contents=prompt,
                config=types.GenerateContentConfig(response_mime_type="application/json"),
            )
        payload = json.loads(response.text)
        days = payload.get("itineraryDays")
        estimate = payload.get("costEstimate")
        if not isinstance(days, list) or len(days) != request.days or not isinstance(estimate, dict):
            raise ValueError("Gemini trả về lịch trình không đúng cấu trúc")
        estimate = {key: str(value) for key, value in estimate.items()}
        estimate["currency"] = "VND"
        return days, estimate, "GEMINI"
    except Exception as exception:
        logger.warning("Không thể tạo lịch trình bằng Gemini, dùng phương án dự phòng: %s", exception)
        days, estimate = _build_fallback_itinerary(request)
        return days, estimate, "RULE_BASED_FALLBACK"


def _object_id(value: str) -> ObjectId:
    if not ObjectId.is_valid(value):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Không tìm thấy lịch trình")
    return ObjectId(value)


@router.post("/itineraries", status_code=status.HTTP_201_CREATED)
async def create_itinerary(
    request: CreateItineraryRequest,
    x_user_id: str = Header(alias="X-User-Id"),
) -> dict[str, Any]:
    itinerary_days, cost_estimate, generation_provider = await _generate_itinerary(request)
    now = datetime.now(timezone.utc)
    document = {
        "userId": x_user_id,
        "destination": request.destination.strip(),
        "days": request.days,
        "budget": str(request.budget),
        "travelerCount": request.traveler_count,
        "groupProfile": request.group_profile,
        "preferences": request.preferences,
        "itineraryDays": itinerary_days,
        "costEstimate": cost_estimate,
        "generationProvider": generation_provider,
        "shareEnabled": False,
        "shareToken": None,
        "createdAt": now,
        "updatedAt": now,
    }
    inserted = await collection.insert_one(document)
    document["_id"] = inserted.inserted_id
    return _serialize(document)


@router.get("/itineraries/me")
async def list_my_itineraries(
    x_user_id: str = Header(alias="X-User-Id"),
    page: int = Query(default=0, ge=0),
    size: int = Query(default=20, ge=1, le=100),
) -> dict[str, Any]:
    cursor = collection.find({"userId": x_user_id}).sort("createdAt", -1).skip(page * size).limit(size)
    items = [_serialize(document) async for document in cursor]
    total = await collection.count_documents({"userId": x_user_id})
    return {"items": items, "total": total, "page": page, "size": size}


@router.get("/itineraries/{itinerary_id}")
async def get_itinerary(
    itinerary_id: str,
    x_user_id: str = Header(alias="X-User-Id"),
) -> dict[str, Any]:
    document = await collection.find_one({"_id": _object_id(itinerary_id), "userId": x_user_id})
    if document is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Không tìm thấy lịch trình")
    return _serialize(document)


@router.post("/itineraries/{itinerary_id}/share", response_model=ShareResponse)
async def share_itinerary(
    itinerary_id: str,
    x_user_id: str = Header(alias="X-User-Id"),
) -> ShareResponse:
    existing = await collection.find_one({"_id": _object_id(itinerary_id), "userId": x_user_id})
    if existing is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Không tìm thấy lịch trình")
    if existing.get("shareEnabled") and existing.get("shareToken"):
        token = existing["shareToken"]
        return ShareResponse(shareToken=token, sharePath=f"/v1/ai/shared/{token}")

    share_token = token_urlsafe(24)
    result = await collection.update_one(
        {"_id": _object_id(itinerary_id), "userId": x_user_id},
        {"$set": {"shareEnabled": True, "shareToken": share_token, "updatedAt": datetime.now(timezone.utc)}},
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Không tìm thấy lịch trình")
    return ShareResponse(shareToken=share_token, sharePath=f"/v1/ai/shared/{share_token}")


@router.get("/shared/{share_token}")
async def get_shared_itinerary(share_token: str) -> dict[str, Any]:
    document = await collection.find_one({"shareToken": share_token, "shareEnabled": True})
    if document is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Liên kết chia sẻ không tồn tại")
    result = _serialize(document)
    result.pop("userId", None)
    return result
