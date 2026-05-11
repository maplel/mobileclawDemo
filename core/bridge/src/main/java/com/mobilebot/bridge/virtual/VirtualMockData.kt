package com.mobilebot.bridge.virtual

/**
 * Centralized mock data for all virtual bridges and services.
 * Organized by service ID, then by action name.
 */
object VirtualMockData {

    // ── Geico Insurance ────────────────────────────────────────────
    val GEICO = mapOf(
        "getPolicy" to mapOf(
            "policyNumber" to "GK-2024-8891726",
            "holderName" to "张三",
            "coverageType" to "comprehensive",
            "deductible" to "$500",
            "monthlyPremium" to "$142.50",
            "rentalCarCoverage" to "$50/day up to 30 days",
            "roadsideAssistance" to true,
            "vehicleVin" to "5YJ3E1EA8PF123456",
            "vehicleYear" to 2024,
            "vehicleMake" to "Tesla",
            "vehicleModel" to "Model 3",
            "expirationDate" to "2027-01-15",
        ),
        "fileClaim" to mapOf(
            "claimId" to "CLM-2024-0042",
            "status" to "submitted",
            "submittedAt" to "2026-04-06T08:30:00Z",
            "estimatedReviewDays" to 3,
            "assignedAdjuster" to "Mary Johnson",
            "adjusterPhone" to "1-800-555-0142",
        ),
        "getClaimStatus" to mapOf(
            "claimId" to "CLM-2024-0042",
            "status" to "under_review",
            "assignedAdjuster" to "Mary Johnson",
            "lastUpdated" to "2026-04-06T10:15:00Z",
            "estimatedCompletionDate" to "2026-04-09",
            "documentsReceived" to 3,
            "documentsRequired" to 4,
        ),
        "uploadEvidence" to mapOf(
            "uploadId" to "EVD-2026-0088",
            "status" to "received",
            "fileCount" to 2,
            "totalSizeBytes" to 4_500_000,
            "attachedToClaimId" to "CLM-2024-0042",
        ),
    )

    // ── Tesla Fleet API ────────────────────────────────────────────
    val TESLA_FLEET = mapOf(
        "getVehicleData" to mapOf(
            "vehicleId" to "1492931520243",
            "vin" to "5YJ3E1EA8PF123456",
            "displayName" to "My Model 3",
            "state" to "online",
            "batteryLevel" to 78,
            "batteryRange" to "245 mi",
            "chargingState" to "Disconnected",
            "odometerMiles" to 12345,
            "insideTemp" to "22°C",
            "outsideTemp" to "18°C",
            "latitude" to 31.2304,
            "longitude" to 121.4737,
            "speed" to 0,
            "locked" to true,
            "softwareVersion" to "2026.8.3",
        ),
        "getCollisionReport" to mapOf(
            "vehicleId" to "1492931520243",
            "collisionDetected" to true,
            "timestamp" to "2026-04-06T07:45:12Z",
            "severity" to "moderate",
            "impactDirection" to "rear",
            "impactForceG" to 4.2,
            "airbagDeployed" to false,
            "absActivated" to true,
            "latitude" to 31.2298,
            "longitude" to 121.4745,
            "speedAtImpactMph" to 25,
        ),
        "getDashcamFootage" to mapOf(
            "vehicleId" to "1492931520243",
            "clips" to listOf(
                mapOf("clipId" to "DC-001", "camera" to "front", "startTime" to "2026-04-06T07:44:00Z", "durationSec" to 120, "sizeMb" to 85),
                mapOf("clipId" to "DC-002", "camera" to "rear", "startTime" to "2026-04-06T07:44:00Z", "durationSec" to 120, "sizeMb" to 78),
                mapOf("clipId" to "DC-003", "camera" to "left_repeater", "startTime" to "2026-04-06T07:44:00Z", "durationSec" to 120, "sizeMb" to 62),
            ),
        ),
        "getLocation" to mapOf(
            "vehicleId" to "1492931520243",
            "latitude" to 31.2304,
            "longitude" to 121.4737,
            "heading" to 270,
            "timestamp" to "2026-04-06T08:00:00Z",
        ),
        "flashLights" to mapOf(
            "vehicleId" to "1492931520243",
            "result" to true,
            "message" to "Lights flashed successfully",
        ),
    )

    // ── Ctrip Travel ───────────────────────────────────────────────
    val CTRIP = mapOf(
        "searchFlights" to mapOf(
            "results" to listOf(
                mapOf("flightNo" to "MU5101", "airline" to "东方航空", "departure" to "上海浦东 08:00", "arrival" to "北京大兴 10:30", "price" to "¥980", "class" to "economy"),
                mapOf("flightNo" to "CA1502", "airline" to "国航", "departure" to "上海浦东 09:30", "arrival" to "北京大兴 12:00", "price" to "¥1,120", "class" to "economy"),
                mapOf("flightNo" to "CZ3572", "airline" to "南航", "departure" to "上海虹桥 11:00", "arrival" to "北京大兴 13:20", "price" to "¥860", "class" to "economy"),
            ),
            "currency" to "CNY",
            "searchDate" to "2026-04-10",
        ),
        "bookFlight" to mapOf(
            "bookingId" to "CT-FL-20260410-0078",
            "status" to "confirmed",
            "flightNo" to "MU5101",
            "passenger" to "张三",
            "seatAssignment" to "32A",
            "totalPrice" to "¥980",
            "eTicketNumber" to "781-2345678901",
        ),
        "searchHotels" to mapOf(
            "results" to listOf(
                mapOf("hotelName" to "上海外滩华尔道夫", "rating" to 4.8, "pricePerNight" to "¥2,800", "location" to "黄浦区中山东一路2号"),
                mapOf("hotelName" to "和平饭店", "rating" to 4.7, "pricePerNight" to "¥2,200", "location" to "黄浦区南京东路20号"),
            ),
        ),
        "bookHotel" to mapOf(
            "bookingId" to "CT-HT-20260410-0034",
            "status" to "confirmed",
            "hotelName" to "上海外滩华尔道夫",
            "checkIn" to "2026-04-10",
            "checkOut" to "2026-04-12",
            "roomType" to "豪华大床房",
            "totalPrice" to "¥5,600",
        ),
        "getFlightStatus" to mapOf(
            "flightNo" to "MU5101",
            "status" to "on_time",
            "scheduledDeparture" to "2026-04-10T08:00:00+08:00",
            "estimatedDeparture" to "2026-04-10T08:00:00+08:00",
            "gate" to "A12",
            "terminal" to "T1",
        ),
    )

    // ── Visa Checker ─────────────────────────────────────────────
    val VISA_CHECKER = mapOf(
        "checkRequirements" to mapOf(
            "nationality" to "中国",
            "destination" to "日本",
            "visaRequired" to true,
            "visaType" to "短期滞在签证（观光）",
            "processingDays" to "5-7 个工作日",
            "requiredDocuments" to listOf(
                "有效护照（剩余有效期 6 个月以上）",
                "签证申请表",
                "2 寸白底照片 2 张",
                "在职证明 / 在读证明",
                "银行流水（近 3 个月，余额 5 万以上）",
                "机票及酒店预订确认单",
                "户口本复印件",
            ),
            "visaFee" to "¥200（单次）",
            "embassyUrl" to "https://www.cn.emb-japan.go.jp/itpr_zh/visa.html",
            "notes" to "可通过指定旅行社代办，部分城市可办理电子签证。",
        ),
        "checkStatus" to mapOf(
            "applicationId" to "JP-VISA-2026-04-00312",
            "status" to "processing",
            "submittedDate" to "2026-04-01",
            "estimatedCompletionDate" to "2026-04-08",
        ),
    )

    // ── Hotel Search ──────────────────────────────────────────────
    val HOTEL_SEARCH = mapOf(
        "search" to mapOf(
            "results" to listOf(
                mapOf("hotelName" to "东京新宿华盛顿酒店", "rating" to 4.3, "pricePerNight" to "¥650", "location" to "东京新宿区西新宿 3-2-9", "stars" to 3, "breakfastIncluded" to true),
                mapOf("hotelName" to "东京半岛酒店", "rating" to 4.9, "pricePerNight" to "¥3,800", "location" to "东京千代田区有乐町 1-8-1", "stars" to 5, "breakfastIncluded" to true),
                mapOf("hotelName" to "京都四条三井花园酒店", "rating" to 4.5, "pricePerNight" to "¥880", "location" to "京都市下京区四条通", "stars" to 4, "breakfastIncluded" to false),
                mapOf("hotelName" to "大阪难波道顿堀酒店", "rating" to 4.2, "pricePerNight" to "¥520", "location" to "大阪市中央区道顿堀 1-3-19", "stars" to 3, "breakfastIncluded" to false),
            ),
            "currency" to "CNY",
        ),
        "getDetails" to mapOf(
            "hotelName" to "东京新宿华盛顿酒店",
            "address" to "东京都新宿区西新宿 3-2-9",
            "phone" to "+81-3-3343-3111",
            "amenities" to listOf("WiFi", "餐厅", "商务中心", "洗衣服务"),
            "checkInTime" to "14:00",
            "checkOutTime" to "11:00",
            "roomTypes" to listOf(
                mapOf("type" to "标准单人间", "price" to "¥650", "available" to true),
                mapOf("type" to "标准双人间", "price" to "¥850", "available" to true),
                mapOf("type" to "豪华双人间", "price" to "¥1,200", "available" to false),
            ),
        ),
        "book" to mapOf(
            "bookingId" to "HS-BK-20260415-0056",
            "status" to "confirmed",
            "hotelName" to "东京新宿华盛顿酒店",
            "checkIn" to "2026-04-15",
            "checkOut" to "2026-04-18",
            "roomType" to "标准双人间",
            "totalPrice" to "¥2,550",
            "cancellationPolicy" to "入住前 24 小时免费取消",
        ),
    )

    // ── AAA Roadside Assistance ────────────────────────────────────
    val AAA_ROADSIDE = mapOf(
        "requestTow" to mapOf(
            "requestId" to "TOW-2026-04060012",
            "status" to "dispatched",
            "estimatedArrivalMinutes" to 25,
            "driverName" to "Mike Chen",
            "driverPhone" to "1-555-0199",
            "truckId" to "AAA-SH-T042",
            "pickupLocation" to "31.2298, 121.4745",
            "destinationServiceCenter" to "AAA Approved - 上海浦东汽车服务中心",
        ),
        "checkMembership" to mapOf(
            "memberId" to "AAA-438821907",
            "memberName" to "张三",
            "tier" to "Plus",
            "status" to "active",
            "towingCoverageKm" to 160,
            "towsUsedThisYear" to 0,
            "towsRemaining" to 4,
            "expirationDate" to "2027-03-15",
        ),
        "findServiceCenter" to mapOf(
            "results" to listOf(
                mapOf("name" to "上海浦东汽车服务中心", "distance" to "3.2 km", "address" to "浦东新区张杨路1234号", "phone" to "021-58001234", "rating" to 4.5, "openNow" to true),
                mapOf("name" to "黄浦区AAA授权维修站", "distance" to "5.8 km", "address" to "黄浦区西藏南路567号", "phone" to "021-63005678", "rating" to 4.3, "openNow" to true),
            ),
        ),
        "getTowStatus" to mapOf(
            "requestId" to "TOW-2026-04060012",
            "status" to "en_route",
            "driverName" to "Mike Chen",
            "currentDistanceKm" to 4.5,
            "estimatedArrivalMinutes" to 12,
            "lastUpdated" to "2026-04-06T08:18:00Z",
        ),
    )

    // ── OpenTable ──────────────────────────────────────────────────
    val OPENTABLE = mapOf(
        "searchRestaurants" to mapOf(
            "results" to listOf(
                mapOf("restaurantId" to "OT-SH-001", "name" to "M on the Bund", "cuisine" to "西餐/法餐", "priceRange" to "¥¥¥¥", "rating" to 4.6, "location" to "外滩5号7楼", "availableTonight" to true),
                mapOf("restaurantId" to "OT-SH-002", "name" to "南翔馒头店", "cuisine" to "本帮菜/小笼包", "priceRange" to "¥", "rating" to 4.2, "location" to "豫园路85号", "availableTonight" to true),
                mapOf("restaurantId" to "OT-SH-003", "name" to "鼎泰丰（正大店）", "cuisine" to "台湾菜/小笼包", "priceRange" to "¥¥", "rating" to 4.5, "location" to "陆家嘴正大广场6楼", "availableTonight" to false),
            ),
        ),
        "getRestaurant" to mapOf(
            "restaurantId" to "OT-SH-001",
            "name" to "M on the Bund",
            "cuisine" to "西餐/法餐",
            "address" to "上海市黄浦区中山东一路5号7楼",
            "phone" to "021-63509988",
            "hours" to "11:30-14:30, 17:30-22:30",
            "priceRange" to "人均 ¥600-800",
            "rating" to 4.6,
            "reviewCount" to 1284,
        ),
        "checkAvailability" to mapOf(
            "restaurantId" to "OT-SH-001",
            "date" to "2026-04-06",
            "availableSlots" to listOf("18:00", "18:30", "20:00", "20:30", "21:00"),
            "maxPartySize" to 8,
        ),
        "makeReservation" to mapOf(
            "reservationId" to "OT-RES-20260406-0891",
            "status" to "confirmed",
            "restaurantName" to "M on the Bund",
            "date" to "2026-04-06",
            "time" to "18:30",
            "partySize" to 2,
            "confirmationCode" to "MB0891",
        ),
        "cancelReservation" to mapOf(
            "reservationId" to "OT-RES-20260406-0891",
            "status" to "cancelled",
            "cancellationTime" to "2026-04-06T16:00:00Z",
        ),
    )

    // ── Marriott Bonvoy ────────────────────────────────────────────
    val MARRIOTT = mapOf(
        "searchHotels" to mapOf(
            "results" to listOf(
                mapOf("hotelId" to "MAR-SH-001", "name" to "上海外滩W酒店", "brand" to "W Hotels", "pricePerNight" to "$280", "rating" to 4.7, "distanceKm" to 0.5),
                mapOf("hotelId" to "MAR-SH-002", "name" to "上海明天广场JW万豪", "brand" to "JW Marriott", "pricePerNight" to "$220", "rating" to 4.5, "distanceKm" to 2.1),
                mapOf("hotelId" to "MAR-SH-003", "name" to "上海浦东丽思卡尔顿", "brand" to "Ritz-Carlton", "pricePerNight" to "$350", "rating" to 4.8, "distanceKm" to 3.4),
            ),
        ),
        "getHotelDetails" to mapOf(
            "hotelId" to "MAR-SH-001",
            "name" to "上海外滩W酒店",
            "brand" to "W Hotels",
            "address" to "上海市黄浦区中山东二路585号",
            "phone" to "+86-21-22869999",
            "amenities" to listOf("泳池", "健身房", "Spa", "酒吧", "餐厅", "WiFi"),
            "checkInTime" to "15:00",
            "checkOutTime" to "12:00",
            "rating" to 4.7,
        ),
        "checkAvailability" to mapOf(
            "hotelId" to "MAR-SH-001",
            "checkIn" to "2026-04-10",
            "checkOut" to "2026-04-12",
            "rooms" to listOf(
                mapOf("roomType" to "Wonderful Room", "pricePerNight" to "$280", "available" to true, "pointsPerNight" to 50000),
                mapOf("roomType" to "Spectacular Suite", "pricePerNight" to "$520", "available" to true, "pointsPerNight" to 85000),
            ),
        ),
        "bookRoom" to mapOf(
            "confirmationNumber" to "MAR-92847561",
            "status" to "confirmed",
            "hotelName" to "上海外滩W酒店",
            "roomType" to "Wonderful Room",
            "checkIn" to "2026-04-10",
            "checkOut" to "2026-04-12",
            "totalCost" to "$560",
            "bonvoyPointsEarned" to 1680,
        ),
        "getBonvoyBalance" to mapOf(
            "memberId" to "BV-123456789",
            "memberName" to "张三",
            "tier" to "Gold Elite",
            "pointsBalance" to 85420,
            "nightsThisYear" to 12,
            "nightsToNextTier" to 38,
            "nextTier" to "Platinum Elite",
        ),
    )

    val PET_STORE_MOCK = mapOf(
        "locations" to listOf(
            mapOf(
                "id" to "pet_store_nanshan_001",
                "name" to "Paopao Pet Life Nanshan",
                "address" to "Shenzhen Nanshan Keyuan South Road 2666",
                "distanceKm" to 2.4,
                "serviceRadiusKm" to 8,
                "businessHours" to mapOf("weekday" to "09:30-20:00", "weekend" to "10:00-21:00"),
                "closedDates" to listOf("2026-05-06"),
                "openNow" to true,
            ),
            mapOf(
                "id" to "pet_store_futian_001",
                "name" to "Wangmiao Pet Life Futian",
                "address" to "Shenzhen Futian Fuhua 3rd Road 118",
                "distanceKm" to 6.8,
                "serviceRadiusKm" to 10,
                "businessHours" to mapOf("weekday" to "10:00-19:30", "weekend" to "10:00-20:30"),
                "closedDates" to emptyList<String>(),
                "openNow" to true,
            ),
        ),
        "services" to listOf(
            mapOf("id" to "dog_bath_basic", "name" to "Dog basic bath", "petType" to "dog", "durationMinutes" to 60, "price" to 99, "supportedLocations" to listOf("pet_store_nanshan_001", "pet_store_futian_001")),
            mapOf("id" to "dog_bath_teeth", "name" to "Dog bath plus teeth brushing", "petType" to "dog", "durationMinutes" to 80, "price" to 139, "supportedLocations" to listOf("pet_store_nanshan_001")),
            mapOf("id" to "cat_bath_care", "name" to "Cat gentle grooming", "petType" to "cat", "durationMinutes" to 90, "price" to 169, "supportedLocations" to listOf("pet_store_futian_001")),
        ),
        "products" to listOf(
            mapOf("id" to "dog_food_adult_10kg", "name" to "Adult dog food 10kg", "petType" to "dog", "category" to "food", "price" to 289, "stockByLocation" to mapOf("pet_store_nanshan_001" to 8, "pet_store_futian_001" to 4)),
            mapOf("id" to "cat_food_adult_5kg", "name" to "Adult cat food 5kg", "petType" to "cat", "category" to "food", "price" to 199, "stockByLocation" to mapOf("pet_store_nanshan_001" to 8, "pet_store_futian_001" to 0)),
            mapOf("id" to "cat_litter_tofu_6l", "name" to "Tofu cat litter 6L", "petType" to "cat", "category" to "litter", "price" to 49, "stockByLocation" to mapOf("pet_store_nanshan_001" to 10, "pet_store_futian_001" to 10)),
        ),
        "promotions" to listOf(
            mapOf("id" to "promo_weekday_bath", "name" to "Weekday grooming 10 percent off", "appliesTo" to listOf("dog_bath_basic", "dog_bath_teeth", "cat_bath_care"), "discount" to 0.9),
            mapOf("id" to "promo_food_bundle", "name" to "Cat food plus litter bundle", "appliesTo" to listOf("cat_food_adult_5kg", "cat_litter_tofu_6l"), "discountAmount" to 20),
        ),
        "storeCalendars" to mapOf(
            "pet_store_nanshan_001" to mapOf(
                "2026-05-04" to mapOf(
                    "open" to "09:30",
                    "close" to "20:00",
                    "breaks" to listOf("12:30-13:30"),
                    "bookedSlots" to listOf(
                        mapOf("bookingId" to "bk_other_001", "serviceId" to "dog_bath_basic", "start" to "10:00", "end" to "11:00"),
                        mapOf("bookingId" to "bk_other_002", "serviceId" to "dog_bath_teeth", "start" to "16:00", "end" to "17:20"),
                    ),
                    "staffUnavailable" to listOf("18:00-19:00"),
                ),
                "2026-05-05" to mapOf(
                    "open" to "09:30",
                    "close" to "20:00",
                    "breaks" to listOf("12:30-13:30"),
                    "bookedSlots" to emptyList<Map<String, Any>>(),
                    "staffUnavailable" to emptyList<String>(),
                ),
            ),
            "pet_store_futian_001" to mapOf(
                "2026-05-04" to mapOf(
                    "open" to "10:00",
                    "close" to "19:30",
                    "breaks" to listOf("13:00-14:00"),
                    "bookedSlots" to listOf(
                        mapOf("bookingId" to "bk_other_101", "serviceId" to "cat_bath_care", "start" to "15:00", "end" to "16:30"),
                    ),
                    "staffUnavailable" to emptyList<String>(),
                ),
            ),
        ),
    )

    val PET_TRANSPORT_MOCK = mapOf(
        "estimate_transport" to mapOf(
            "serviceType" to "pet_pickup_dropoff",
            "estimatedFee" to 36,
            "estimatedPickupMinutes" to 18,
            "estimatedDurationMinutes" to 25,
            "provider" to "Mock Pet Transport",
        ),
        "request_transport" to mapOf(
            "transportId" to "pt_mock_20260504_001",
            "status" to "requested",
            "serviceType" to "pet_pickup_dropoff",
            "driverInfo" to mapOf("name" to "Mock Driver Chen", "phone" to "13800000001", "vehicle" to "Pet-safe van"),
            "estimatedPickupTime" to "2026-05-04T15:30:00+08:00",
            "estimatedArrivalTime" to "2026-05-04T17:30:00+08:00",
            "idempotencyKey" to "mock_transport_idempotency",
        ),
        "modify_transport" to mapOf(
            "transportId" to "pt_mock_20260504_001",
            "status" to "modified",
            "summary" to "Transport schedule updated",
        ),
        "cancel_transport" to mapOf(
            "transportId" to "pt_mock_20260504_001",
            "status" to "cancelled",
            "refundInfo" to "No cancellation fee in mock mode",
        ),
        "get_transport_status" to mapOf(
            "transportId" to "pt_mock_20260504_001",
            "status" to "driver_assigned",
            "currentLocation" to "2.1 km from pickup",
            "estimatedArrivalMinutes" to 12,
        ),
    )

    /**
     * Lookup mock data by serviceId and action.
     * Returns null if the combination is not found.
     * [params] are used for dynamic mock data (e.g. searchFlights reflects origin/destination).
     */
    fun lookup(serviceId: String, action: String, params: Map<String, Any?> = emptyMap()): Map<String, Any>? {
        if (serviceId == "ctrip" && action == "searchFlights" && params.isNotEmpty()) {
            return buildDynamicFlightResults(params)
        }
        if (serviceId == "pet_store_mock") {
            return buildPetStoreMockResponse(action, params)
        }
        if (serviceId == "pet_transport_mock") {
            return PET_TRANSPORT_MOCK[action]
        }
        val serviceData = when (serviceId) {
            "geico" -> GEICO
            "tesla_fleet" -> TESLA_FLEET
            "ctrip" -> CTRIP
            "aaa_roadside" -> AAA_ROADSIDE
            "opentable" -> OPENTABLE
            "marriott" -> MARRIOTT
            "visa_checker" -> VISA_CHECKER
            "hotel_search" -> HOTEL_SEARCH
            else -> null
        }
        return serviceData?.get(action)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildPetStoreMockResponse(action: String, params: Map<String, Any?>): Map<String, Any>? {
        val locations = PET_STORE_MOCK["locations"] as List<Map<String, Any>>
        val services = PET_STORE_MOCK["services"] as List<Map<String, Any>>
        val products = PET_STORE_MOCK["products"] as List<Map<String, Any>>
        val promotions = PET_STORE_MOCK["promotions"] as List<Map<String, Any>>
        val calendars = PET_STORE_MOCK["storeCalendars"] as Map<String, Map<String, Map<String, Any>>>

        return when (action) {
            "list_locations" -> mapOf("locations" to locations)
            "list_services" -> {
                val petType = params["pet_type"]?.toString() ?: params["petType"]?.toString()
                val storeId = params["store_id"]?.toString() ?: params["storeId"]?.toString()
                mapOf(
                    "services" to services.filter { service ->
                        (petType == null || service["petType"] == petType) &&
                            (storeId == null || (service["supportedLocations"] as List<*>).contains(storeId))
                    },
                )
            }
            "list_products" -> {
                val petType = params["pet_type"]?.toString() ?: params["petType"]?.toString()
                val category = params["category"]?.toString()
                mapOf(
                    "products" to products.filter { product ->
                        (petType == null || product["petType"] == petType) &&
                            (category == null || product["category"] == category)
                    },
                )
            }
            "query_inventory" -> {
                val productId = params["product_id"]?.toString() ?: params["productId"]?.toString()
                val storeId = params["store_id"]?.toString() ?: params["storeId"]?.toString() ?: "pet_store_nanshan_001"
                val product = products.firstOrNull { it["id"] == productId } ?: products.first()
                val stockByLocation = product["stockByLocation"] as Map<String, Int>
                val stock = stockByLocation[storeId] ?: 0
                mapOf("product" to product, "storeId" to storeId, "stock" to stock, "available" to (stock > 0))
            }
            "list_promotions" -> mapOf("promotions" to promotions)
            "get_store_calendar" -> {
                val storeId = params["store_id"]?.toString() ?: params["storeId"]?.toString() ?: "pet_store_nanshan_001"
                mapOf("storeId" to storeId, "calendar" to (calendars[storeId] ?: emptyMap<String, Any>()))
            }
            "query_availability" -> buildAvailabilityResponse(params, services, calendars)
            "list_user_addresses", "save_user_address", "list_pet_profiles", "save_pet_profile" -> null
            "create_booking" -> mapOf(
                "booking_id" to "bk_mock_20260504_001",
                "status" to "confirmed",
                "summary" to "Mock grooming booking created",
                "idempotency_key" to (params["idempotency_key"] ?: params["idempotencyKey"] ?: "mock_booking_idempotency"),
            )
            "modify_booking" -> mapOf("booking_id" to (params["booking_id"] ?: params["bookingId"] ?: "bk_mock_20260504_001"), "status" to "modified", "summary" to "Mock booking modified")
            "cancel_booking" -> mapOf("booking_id" to (params["booking_id"] ?: params["bookingId"] ?: "bk_mock_20260504_001"), "status" to "cancelled", "summary" to "Mock booking cancelled")
            "create_order" -> mapOf(
                "order_id" to "ord_mock_20260504_001",
                "status" to "confirmed",
                "summary" to "Mock pet product order created",
                "estimated_delivery_time" to "2026-05-04T20:00:00+08:00",
                "idempotency_key" to (params["idempotency_key"] ?: params["idempotencyKey"] ?: "mock_order_idempotency"),
            )
            "modify_order" -> mapOf("order_id" to (params["order_id"] ?: params["orderId"] ?: "ord_mock_20260504_001"), "status" to "modified", "summary" to "Mock order modified")
            "cancel_order" -> mapOf("order_id" to (params["order_id"] ?: params["orderId"] ?: "ord_mock_20260504_001"), "status" to "cancelled", "summary" to "Mock order cancelled")
            "get_status" -> mapOf("entity_id" to (params["entity_id"] ?: params["entityId"] ?: "bk_mock_20260504_001"), "status" to "confirmed", "last_updated" to "2026-05-04T12:00:00+08:00")
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildAvailabilityResponse(
        params: Map<String, Any?>,
        services: List<Map<String, Any>>,
        calendars: Map<String, Map<String, Map<String, Any>>>,
    ): Map<String, Any> {
        val storeId = params["store_id"]?.toString() ?: params["storeId"]?.toString() ?: "pet_store_nanshan_001"
        val serviceId = params["service_id"]?.toString() ?: params["serviceId"]?.toString() ?: "dog_bath_basic"
        val date = params["date"]?.toString() ?: "2026-05-04"
        val service = services.firstOrNull { it["id"] == serviceId }
        val duration = service?.get("durationMinutes") as? Int ?: 60
        val calendar = calendars[storeId]?.get(date)
        if (calendar == null) {
            return mapOf("storeId" to storeId, "serviceId" to serviceId, "date" to date, "availableSlots" to emptyList<Map<String, Any>>(), "reason" to "No mock calendar for date")
        }

        val closed = storeId == "pet_store_nanshan_001" && date == "2026-05-06"
        if (closed) {
            return mapOf("storeId" to storeId, "serviceId" to serviceId, "date" to date, "availableSlots" to emptyList<Map<String, Any>>(), "reason" to "Store closed")
        }

        val candidateStarts = listOf("09:30", "10:00", "11:00", "13:30", "14:00", "15:00", "16:00", "17:30", "18:00")
        val open = calendar["open"]?.toString() ?: "09:30"
        val close = calendar["close"]?.toString() ?: "20:00"
        val blockedRanges = (calendar["breaks"] as? List<String>).orEmpty() +
            (calendar["staffUnavailable"] as? List<String>).orEmpty() +
            (calendar["bookedSlots"] as? List<Map<String, Any>>).orEmpty().map { "${it["start"]}-${it["end"]}" }

        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
        val currentMinutes = currentHour * 60 + currentMinute
        val minBookingMinutes = currentMinutes + 30
        val isToday = date == java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())

        val slots = candidateStarts.mapNotNull { start ->
            val end = addMinutes(start, duration)
            val range = "$start-$end"
            val startMinutes = minutes(start)
            if (minutes(start) < minutes(open) || minutes(end) > minutes(close) || blockedRanges.any { overlaps(range, it) }) {
                null
            } else if (isToday && startMinutes < minBookingMinutes) {
                null
            } else {
                mapOf("start" to "${date}T$start:00+08:00", "end" to "${date}T$end:00+08:00", "durationMinutes" to duration)
            }
        }

        val filteredCount = candidateStarts.size - slots.size - blockedRanges.count { r ->
            candidateStarts.any { s -> overlaps("$s-${addMinutes(s, duration)}", r) }
        }
        val filteredByTime = if (isToday) candidateStarts.count { minutes(it) < minBookingMinutes } else 0

        val filterNote = if (isToday && filteredByTime > 0) "Filtered out $filteredByTime slots before minimum booking time (current + 30min). Only slots at or after ${"%02d:%02d".format(minBookingMinutes / 60, minBookingMinutes % 60)} are shown." else ""

        return mapOf(
            "storeId" to storeId,
            "serviceId" to serviceId,
            "date" to date,
            "availableSlots" to slots,
            "blockedRanges" to blockedRanges,
            "filterNote" to filterNote,
        )
    }

    private fun addMinutes(time: String, minutes: Int): String {
        val parts = time.split(":")
        val total = parts[0].toInt() * 60 + parts[1].toInt() + minutes
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun overlaps(left: String, right: String): Boolean {
        val (leftStart, leftEnd) = left.split("-").map(::minutes)
        val (rightStart, rightEnd) = right.split("-").map(::minutes)
        return leftStart < rightEnd && rightStart < leftEnd
    }

    private fun minutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun buildDynamicFlightResults(params: Map<String, Any?>): Map<String, Any> {
        val from = params["from"]?.toString() ?: params["origin"]?.toString() ?: params["departure"]?.toString() ?: "出发地"
        val to = params["to"]?.toString() ?: params["destination"]?.toString() ?: params["arrival"]?.toString() ?: "目的地"
        val date = params["date"]?.toString() ?: params["departure_date"]?.toString() ?: "2026-04-15"
        return mapOf(
            "results" to listOf(
                mapOf("flightNo" to "MU523", "airline" to "东方航空", "departure" to "$from 08:30", "arrival" to "$to 12:45", "price" to "¥2,680", "class" to "economy"),
                mapOf("flightNo" to "CA925", "airline" to "国航", "departure" to "$from 10:00", "arrival" to "$to 14:15", "price" to "¥3,120", "class" to "economy"),
                mapOf("flightNo" to "NH972", "airline" to "全日空", "departure" to "$from 13:30", "arrival" to "$to 17:50", "price" to "¥3,580", "class" to "economy"),
            ),
            "currency" to "CNY",
            "searchDate" to date,
            "from" to from,
            "to" to to,
        )
    }

    // ── UserProfileStore test data ─────────────────────────────────
    val USER_PROFILE = mapOf(
        "insurance" to mapOf(
            "geico_policy_number" to "GK-2024-8891726",
            "geico_coverage" to "comprehensive",
            "geico_deductible" to "$500",
            "geico_rental_car" to "$50/day up to 30 days",
            "geico_monthly_premium" to "$142.50",
            "geico_expiration" to "2027-01-15",
        ),
        "membership" to mapOf(
            "aaa_member_id" to "AAA-438821907",
            "aaa_tier" to "Plus",
            "aaa_expiration" to "2027-03-15",
            "marriott_bonvoy_id" to "BV-123456789",
            "marriott_tier" to "Gold Elite",
            "marriott_points" to "85420",
        ),
        "vehicles" to mapOf(
            "primary_vehicle" to "2024 Tesla Model 3",
            "vin" to "5YJ3E1EA8PF123456",
            "license_plate" to "沪A·12345",
            "color" to "白色",
            "tesla_account" to "linked",
        ),
        "emergency_contacts" to mapOf(
            "contact_1" to "李四 - 13900003333 (配偶)",
            "contact_2" to "王五 - 13700004444 (父亲)",
        ),
        "preferences" to mapOf(
            "language" to "zh-CN",
            "currency" to "CNY",
            "nationality" to "中国",
            "passport_number" to "E12345678",
            "passport_expiry" to "2030-08-20",
            "preferred_airline" to "东方航空",
            "frequent_flyer" to "东航万里行 MU8800123456",
            "seat_preference" to "靠窗",
            "travel_class" to "economy",
            "accommodation_brand" to "Marriott",
            "accommodation_room_type" to "大床房",
            "accommodation_amenities" to "健身房, WiFi, 早餐",
            "dining_cuisine" to "中餐, 日料",
            "dining_price_range" to "中等",
            "dietary_restrictions" to "none",
        ),
        "trip_plans" to mapOf(
            "current_trip" to "黄石国家公园自驾 14 天",
            "start_date" to "2026-04-01",
            "end_date" to "2026-04-14",
            "origin" to "上海",
            "destination" to "美国黄石国家公园",
            "day_01" to "04/01 上海浦东 -> 旧金山 (MU589, 10:00-06:30)",
            "day_02" to "04/02 旧金山市区观光（金门大桥、渔人码头）",
            "day_03" to "04/03 旧金山 -> 盐湖城 (自驾, 约 11h)",
            "day_04" to "04/04 盐湖城 -> 杰克逊镇 (自驾, 约 5h)",
            "day_05" to "04/05 大提顿国家公园一日游",
            "day_06" to "04/06 杰克逊镇 -> 黄石南入口 (自驾, 约 1.5h)",
            "day_07" to "04/07 黄石：老忠实间歇泉、大棱镜温泉",
            "day_08" to "04/08 黄石：黄石大峡谷、塔瀑",
            "day_09" to "04/09 黄石：拉马尔山谷观野生动物",
            "day_10" to "04/10 黄石 -> 比林斯 (自驾, 约 3h)，还车检修",
            "day_11" to "04/11 比林斯 -> 旧金山 (UA 航班)",
            "day_12" to "04/12 旧金山硅谷参观",
            "day_13" to "04/13 旧金山购物、收拾行李",
            "day_14" to "04/14 旧金山 -> 上海浦东 (MU590, 12:00)",
            "hotel_day01_03" to "旧金山 Marriott Union Square",
            "hotel_day04_05" to "杰克逊镇 Snow King Resort",
            "hotel_day06_09" to "黄石 Old Faithful Inn",
            "hotel_day10" to "比林斯 Marriott Billings",
            "hotel_day11_13" to "旧金山 W San Francisco",
            "rental_car" to "Hertz 全尺寸 SUV (旧金山取/比林斯还)",
            "travel_insurance" to "Geico 保单 GK-2024-8891726 覆盖",
        ),
        "health" to mapOf(
            "blood_type" to "A+",
            "allergies" to "无已知过敏",
            "medications" to "无长期用药",
            "emergency_medical_contact" to "上海仁济医院 021-58752345",
            "health_insurance" to "中国平安健康险",
        ),
    )
}
