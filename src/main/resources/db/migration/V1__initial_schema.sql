CREATE TYPE "Ship_status" AS ENUM (
  'ACTIVE',
  'MAINTENANCE',
  'INACTIVE'
);

CREATE TYPE "Cabin_cabin_type" AS ENUM (
  'INTERIOR',
  'OCEAN_VIEW',
  'BALCONY',
  'SUITE'
);

CREATE TYPE "Cabin_status" AS ENUM (
  'AVAILABLE',
  'OCCUPIED',
  'MAINTENANCE',
  'OUT_OF_SERVICE'
);

CREATE TYPE "Cruise_cruise_type" AS ENUM (
  'ONE_DAY',
  'MULTI_DAYS'
);

CREATE TYPE "Cruise_status" AS ENUM (
  'DRAFT',
  'PUBLISHED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED'
);

CREATE TYPE "OperationalRegulation_regulation_type" AS ENUM (
  'SAFETY',
  'CAPACITY',
  'SPEED',
  'WEATHER',
  'ENVIRONMENT',
  'HEALTH'
);

CREATE TYPE "OperationalRegulation_scope" AS ENUM (
  'GLOBAL',
  'SHIP',
  'CRUISE'
);

CREATE TYPE "OperationalRegulation_unit" AS ENUM (
  'KNOT',
  'KM',
  'METER',
  'PERCENT',
  'PERSON',
  'CELSIUS',
  'NONE'
);

CREATE TYPE "OperationalRegulation_status" AS ENUM (
  'DRAFT',
  'ACTIVE',
  'INACTIVE',
  'EXPIRED'
);

CREATE TYPE "OperationalIssue_issue_type" AS ENUM (
  'WEATHER',
  'TECHNICAL',
  'CABIN',
  'SAFETY',
  'OPERATIONAL'
);

CREATE TYPE "OperationalIssue_severity" AS ENUM (
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL'
);

CREATE TYPE "OperationalIssue_status" AS ENUM (
  'OPEN',
  'IN_PROGRESS',
  'RESOLVED',
  'CLOSED'
);

CREATE TYPE "Itinerary_itinerary_type" AS ENUM (
  'ROUND_TRIP',
  'ONE_WAY'
);

CREATE TYPE "Itinerary_status" AS ENUM (
  'DRAFT',
  'PENDING_APPROVAL',
  'APPROVED',
  'OPEN_FOR_SALE',
  'ACTIVE',
  'COMPLETED',
  'REJECTED',
  'SUSPENDED',
  'CANCELLED'
);

CREATE TYPE "ItineraryDay_day_type" AS ENUM (
  'SEA_DAY',
  'PORT_DAY'
);

CREATE TYPE "CruiseStop_call_type" AS ENUM (
  'NORMAL',
  'LATE_NIGHT',
  'OVERNIGHT'
);

CREATE TYPE "CruiseStop_arrival_method" AS ENUM (
  'BERTH',
  'TENDER'
);

CREATE TYPE "CruiseStop_status" AS ENUM (
  'PLANNED',
  'DELAYED',
  'COMPLETED',
  'OMITTED',
  'SUBSTITUTED'
);

CREATE TYPE "Port_status" AS ENUM (
  'ACTIVE',
  'INACTIVE'
);

CREATE TYPE "Destination_status" AS ENUM (
  'ACTIVE',
  'INACTIVE'
);

CREATE TYPE "Supplier_status" AS ENUM (
  'ACTIVE',
  'INACTIVE'
);

CREATE TYPE "ShoreExcursion_status" AS ENUM (
  'PLANNED',
  'OPEN',
  'FULL',
  'CLOSED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED'
);

CREATE TYPE "TourGroup_status" AS ENUM (
  'PLANNED',
  'ASSEMBLING',
  'DEPARTED',
  'RETURNED',
  'CANCELLED'
);

CREATE TYPE "TourGroupPassenger_assignment_status" AS ENUM (
  'ASSIGNED',
  'CHECKED_IN',
  'COMPLETED',
  'CANCELLED'
);

CREATE TYPE "ItineraryChangeHistory_change_type" AS ENUM (
  'TIME_CHANGE',
  'PORT_OMITTED',
  'PORT_SUBSTITUTED',
  'STOP_REORDERED',
  'EXCURSION_IMPACT',
  'OTHER'
);

CREATE TYPE "Service_status" AS ENUM (
  'ACTIVE',
  'INACTIVE',
  'SUSPENDED',
  'CANCELLED'
);

CREATE TYPE "ServiceRequest_status" AS ENUM (
  'REQUESTED',
  'ACCEPTED',
  'IN_PROGRESS',
  'COMPLETED',
  'REJECTED',
  'CANCELLED'
);

CREATE TYPE "Product_status" AS ENUM (
  'ACTIVE',
  'INACTIVE',
  'DISCONTINUED'
);

CREATE TYPE "TourPackage_package_type" AS ENUM (
  'BASIC',
  'PREMIUM',
  'ADVANCED'
);

CREATE TYPE "TourPackage_status" AS ENUM (
  'DRAFT',
  'ACTIVE',
  'INACTIVE',
  'EXPIRED',
  'CANCELLED'
);

CREATE TYPE "OnboardActivity_status" AS ENUM (
  'PLANNED',
  'OPEN',
  'FULL',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED'
);

CREATE TYPE "Registration_status" AS ENUM (
  'PENDING',
  'CONFIRMED',
  'CANCELLED',
  'COMPLETED'
);

CREATE TYPE "Participation_status" AS ENUM (
  'REGISTERED',
  'CHECKED_IN',
  'IN_PROGRESS',
  'COMPLETED',
  'NO_SHOW',
  'CANCELLED'
);

CREATE TYPE "OnboardAccount_status" AS ENUM (
  'PENDING_ACTIVATION',
  'ACTIVE',
  'POSTING_BLOCKED',
  'CLOSED'
);

CREATE TYPE "OnboardAccountMember_member_role" AS ENUM (
  'OWNER',
  'AUTHORIZED'
);

CREATE TYPE "OnboardAccountMember_charge_permission" AS ENUM (
  'ALLOWED',
  'NOT_ALLOWED'
);

CREATE TYPE "OnboardAccountMember_status" AS ENUM (
  'ACTIVE',
  'REVOKED'
);

CREATE TYPE "SpendingLimit_status" AS ENUM (
  'ACTIVE',
  'EXPIRED',
  'CANCELLED'
);

CREATE TYPE "OnboardTransaction_transaction_type" AS ENUM (
  'CHARGE',
  'PAYMENT',
  'REFUND',
  'VOID',
  'ADJUSTMENT'
);

CREATE TYPE "OnboardTransaction_status" AS ENUM (
  'PENDING',
  'POSTED',
  'FAILED'
);

CREATE TYPE "OnboardTransaction_sync_status" AS ENUM (
  'PENDING',
  'SYNCED',
  'FAILED'
);

CREATE TYPE "ChargeDispute_reason_code" AS ENUM (
  'UNRECOGNIZED_CHARGE',
  'WRONG_AMOUNT',
  'DUPLICATE_CHARGE',
  'SERVICE_NOT_RECEIVED',
  'OTHER'
);

CREATE TYPE "ChargeDispute_status" AS ENUM (
  'OPEN',
  'UNDER_REVIEW',
  'RESOLVED',
  'REJECTED',
  'CANCELLED'
);

CREATE TYPE "ChargeDispute_resolution_type" AS ENUM (
  'NO_CHANGE',
  'REVERSE_CHARGE',
  'ADJUST_CHARGE',
  'REFUND_REQUIRED'
);

CREATE TYPE "User_status" AS ENUM (
  'ACTIVE',
  'LOCKED',
  'DISABLED'
);

CREATE TYPE "Passenger_document_type" AS ENUM (
  'NationalID',
  'Passport'
);

CREATE TYPE "Booking_booking_status" AS ENUM (
  'Draft',
  'Confirmed',
  'Cancelled'
);

CREATE TYPE "BookingPassenger_participation_status" AS ENUM (
  'Active',
  'Cancelled'
);

CREATE TYPE "BookingContract_status" AS ENUM (
  'Draft',
  'Accepted',
  'Cancelled'
);

CREATE TYPE "CruiseCheckin_checkin_status" AS ENUM (
  'NotStarted',
  'InProgress',
  'NeedsReview',
  'Completed',
  'BoardingReady'
);

CREATE TYPE "CheckinStep_step_code" AS ENUM (
  'Identity',
  'Document',
  'EmergencyContact',
  'PaymentEligibility'
);

CREATE TYPE "CheckinStep_status" AS ENUM (
  'Pending',
  'Accepted',
  'NeedsReview'
);

CREATE TYPE "PassengerIdentifier_identifier_type" AS ENUM (
  'QR',
  'CruiseCard',
  'WearableDemo'
);

CREATE TYPE "PassengerIdentifier_status" AS ENUM (
  'Active',
  'Revoked',
  'Expired'
);

CREATE TYPE "GangwayEvent_event_type" AS ENUM (
  'Embark',
  'ShoreExit',
  'ShoreReturn',
  'FinalDisembark'
);

CREATE TYPE "GangwayEvent_result" AS ENUM (
  'Accepted',
  'Rejected'
);

CREATE TYPE "GuidanceSession_status" AS ENUM (
  'Scheduled',
  'Completed',
  'Cancelled'
);

CREATE TYPE "GuidanceSessionParticipant_attendance_status" AS ENUM (
  'Registered',
  'Attended',
  'Absent'
);

CREATE TYPE "CruiseCheckout_checkout_status" AS ENUM (
  'NotStarted',
  'InProgress',
  'Blocked',
  'Completed'
);

CREATE TYPE "Invoice_document_type" AS ENUM (
  'BookingInvoice',
  'PreliminaryFolio',
  'FinalStatement',
  'AdjustmentStatement'
);

CREATE TYPE "Invoice_status" AS ENUM (
  'Draft',
  'Issued',
  'Superseded',
  'Voided'
);

CREATE TYPE "Payment_operation_type" AS ENUM (
  'Collect',
  'Void'
);

CREATE TYPE "Payment_payment_method" AS ENUM (
  'Cash',
  'GatewaySandbox'
);

CREATE TYPE "Payment_status" AS ENUM (
  'Pending',
  'Confirmed',
  'Failed',
  'Cancelled'
);

CREATE TYPE "Refund_status" AS ENUM (
  'Requested',
  'Approved',
  'Completed',
  'Failed',
  'Rejected'
);

CREATE TYPE "Installment_installment_type" AS ENUM (
  'Deposit',
  'FinalFare'
);

CREATE TYPE "Installment_status" AS ENUM (
  'Scheduled',
  'Cancelled'
);

CREATE TYPE "FinalSettlement_status" AS ENUM (
  'Pending',
  'PaymentRequired',
  'Settled'
);

CREATE TYPE "TourPackageService_benefit_type" AS ENUM (
  'INCLUDED',
  'OPTIONAL'
);

CREATE TYPE "TourPackageActivity_benefit_type" AS ENUM (
  'INCLUDED',
  'OPTIONAL'
);

CREATE TYPE "TourPackageExcursion_benefit_type" AS ENUM (
  'INCLUDED',
  'OPTIONAL'
);

CREATE TABLE "Ship" (
  "ship_id" uuid PRIMARY KEY NOT NULL,
  "ship_code" varchar(20) UNIQUE NOT NULL,
  "ship_name" varchar(100) NOT NULL,
  "status" "Ship_status" NOT NULL,
  "total_capacity" int NOT NULL,
  "total_decks" int NOT NULL,
  "commission_date" date,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (total_capacity > 0),
  CHECK (total_decks > 0)
);

CREATE TABLE "Deck" (
  "deck_id" uuid PRIMARY KEY NOT NULL,
  "ship_id" uuid NOT NULL,
  "deck_number" int NOT NULL,
  "deck_name" varchar(50),
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (deck_number > 0)
);

CREATE TABLE "Cabin" (
  "cabin_id" uuid PRIMARY KEY NOT NULL,
  "ship_id" uuid NOT NULL,
  "deck_id" uuid NOT NULL,
  "cabin_number" varchar(10) NOT NULL,
  "cabin_type" "Cabin_cabin_type" NOT NULL,
  "capacity" int NOT NULL,
  "status" "Cabin_status" NOT NULL,
  "is_accessible" boolean NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (capacity > 0)
);

CREATE TABLE "Cruise" (
  "cruise_id" uuid PRIMARY KEY NOT NULL,
  "cruise_code" varchar(20) UNIQUE NOT NULL,
  "cruise_name" varchar(150) NOT NULL,
  "ship_id" uuid,
  "cruise_type" "Cruise_cruise_type" NOT NULL,
  "departure_datetime" timestamptz NOT NULL,
  "return_datetime" timestamptz NOT NULL,
  "departure_port_id" uuid NOT NULL,
  "return_port_id" uuid NOT NULL,
  "status" "Cruise_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (return_datetime > departure_datetime)
);

CREATE TABLE "Port" (
  "port_id" uuid PRIMARY KEY NOT NULL,
  "port_code" varchar(50) UNIQUE,
  "port_name" varchar(200) NOT NULL,
  "country_code" char(2) NOT NULL,
  "time_zone_id" varchar(100) NOT NULL,
  "latitude" decimal(9,6),
  "longitude" decimal(9,6),
  "status" "Port_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL
);

CREATE TABLE "Destination" (
  "destination_id" uuid PRIMARY KEY NOT NULL,
  "destination_name" varchar(200) NOT NULL,
  "country_code" char(2) NOT NULL,
  "description" varchar(1000),
  "status" "Destination_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL
);

CREATE TABLE "Itinerary" (
  "itinerary_id" uuid PRIMARY KEY NOT NULL,
  "cruise_id" uuid UNIQUE NOT NULL,
  "itinerary_name" varchar(200),
  "start_date" date NOT NULL,
  "end_date" date NOT NULL,
  "itinerary_type" "Itinerary_itinerary_type" NOT NULL,
  "status" "Itinerary_status" NOT NULL,
  "approved_by_user_id" uuid,
  "approved_at" timestamptz,
  "created_by_user_id" uuid,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (end_date >= start_date)
);

CREATE TABLE "ItineraryDay" (
  "itinerary_day_id" uuid PRIMARY KEY NOT NULL,
  "itinerary_id" uuid NOT NULL,
  "day_number" int NOT NULL,
  "date" date NOT NULL,
  "day_type" "ItineraryDay_day_type" NOT NULL,
  "ship_time_zone_id" varchar(100),
  "description" varchar(500),
  CHECK (day_number >= 1)
);

CREATE TABLE "CruiseStop" (
  "cruise_stop_id" uuid PRIMARY KEY NOT NULL,
  "itinerary_id" uuid NOT NULL,
  "arrival_itinerary_day_id" uuid NOT NULL,
  "departure_itinerary_day_id" uuid NOT NULL,
  "port_id" uuid NOT NULL,
  "destination_id" uuid,
  "stop_order" int NOT NULL,
  "planned_arrival" timestamptz NOT NULL,
  "planned_departure" timestamptz NOT NULL,
  "actual_arrival" timestamptz,
  "actual_departure" timestamptz,
  "call_type" "CruiseStop_call_type" NOT NULL,
  "arrival_method" "CruiseStop_arrival_method",
  "all_aboard_time" timestamptz,
  "tender_transfer_minutes" int,
  "status" "CruiseStop_status" NOT NULL,
  "cancellation_reason" varchar(500),
  "substituted_by_stop_id" uuid,
  "port_contact_name" varchar(200),
  "port_contact_role" varchar(100),
  "port_contact_phone" varchar(50),
  "port_contact_email" varchar(255),
  "port_contact_responsibility" varchar(500),
  "notes" varchar(1000),
  CHECK (tender_transfer_minutes IS NULL OR tender_transfer_minutes >= 0),
  CHECK (planned_departure > planned_arrival),
  CHECK (actual_departure IS NULL OR (actual_arrival IS NOT NULL AND actual_departure >= actual_arrival))
);

CREATE TABLE "OperationalRegulation" (
  "regulation_id" uuid PRIMARY KEY NOT NULL,
  "regulation_code" varchar(30) UNIQUE NOT NULL,
  "name" varchar(150) NOT NULL,
  "description" text,
  "regulation_type" "OperationalRegulation_regulation_type" NOT NULL,
  "scope" "OperationalRegulation_scope" NOT NULL,
  "value" varchar(100) NOT NULL,
  "unit" "OperationalRegulation_unit",
  "effective_from" timestamptz NOT NULL,
  "effective_to" timestamptz,
  "status" "OperationalRegulation_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE "ShipRegulation" (
  "ship_id" uuid NOT NULL,
  "regulation_id" uuid NOT NULL,
  PRIMARY KEY ("ship_id", "regulation_id")
);

CREATE TABLE "CruiseRegulation" (
  "cruise_id" uuid NOT NULL,
  "regulation_id" uuid NOT NULL,
  PRIMARY KEY ("cruise_id", "regulation_id")
);

CREATE TABLE "OperationalIssue" (
  "issue_id" uuid PRIMARY KEY NOT NULL,
  "issue_code" varchar(30) UNIQUE NOT NULL,
  "cruise_id" uuid,
  "ship_id" uuid NOT NULL,
  "cabin_id" uuid,
  "issue_type" "OperationalIssue_issue_type" NOT NULL,
  "severity" "OperationalIssue_severity" NOT NULL,
  "description" text NOT NULL,
  "reported_at" timestamptz NOT NULL,
  "resolved_at" timestamptz,
  "status" "OperationalIssue_status" NOT NULL,
  "reported_by" uuid NOT NULL,
  "resolved_by" uuid,
  "resolution" text,
  "assigned_to" uuid,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (issue_type <> 'CABIN' OR cabin_id IS NOT NULL),
  CHECK (resolved_at IS NULL OR resolved_at >= reported_at)
);

CREATE TABLE "ItineraryChangeHistory" (
  "change_history_id" uuid PRIMARY KEY NOT NULL,
  "itinerary_id" uuid NOT NULL,
  "cruise_stop_id" uuid,
  "changed_entity_type" varchar(50) NOT NULL,
  "changed_entity_id" uuid NOT NULL,
  "change_type" "ItineraryChangeHistory_change_type" NOT NULL,
  "before_data" jsonb,
  "after_data" jsonb,
  "change_summary" varchar(1000) NOT NULL,
  "reason" varchar(1000) NOT NULL,
  "changed_by_user_id" uuid NOT NULL,
  "changed_at" timestamptz NOT NULL
);

CREATE TABLE "User" (
  "user_id" uuid PRIMARY KEY NOT NULL,
  "username" varchar(100) UNIQUE NOT NULL,
  "password_hash" varchar(255) NOT NULL,
  "email" varchar(255),
  "status" "User_status" NOT NULL,
  "user_type" varchar(50),
  "last_login_at" timestamptz,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS "RolePermission" (
  "role" varchar(50) PRIMARY KEY NOT NULL
);

CREATE TABLE IF NOT EXISTS "RolePermissionMapping" (
  "role" varchar(50) NOT NULL,
  "permission" varchar(100) NOT NULL,
  PRIMARY KEY ("role", "permission"),
  CONSTRAINT "fk_role_permission_mapping_role" 
    FOREIGN KEY ("role") REFERENCES "RolePermission" ("role") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "UserRole" (
  "user_id" uuid NOT NULL,
  "role" varchar(50) NOT NULL,
  PRIMARY KEY ("user_id", "role"),
  CONSTRAINT "fk_user_role_user_id" 
    FOREIGN KEY ("user_id") REFERENCES "User" ("user_id") ON DELETE CASCADE
);

CREATE TABLE "Passenger" (
  "passenger_id" uuid PRIMARY KEY NOT NULL,
  "user_id" uuid UNIQUE,
  "full_name" varchar(255) NOT NULL,
  "date_of_birth" date,
  "nationality" varchar(255),
  "document_type" "Passenger_document_type",
  "document_number_protected" varchar(255),
  "email" varchar(255),
  "phone_number" varchar(255),
  "created_at" timestamptz NOT NULL
);

CREATE TABLE "Booking" (
  "booking_id" uuid PRIMARY KEY NOT NULL,
  "cruise_id" uuid NOT NULL,
  "representative_booking_passenger_id" uuid,
  "booking_reference" varchar(255) UNIQUE NOT NULL,
  "booked_by_user_id" uuid NOT NULL,
  "booking_status" "Booking_booking_status" NOT NULL,
  "agreed_fare_amount" decimal(19,4) NOT NULL,
  "currency" char(3) NOT NULL,
  "required_deposit_amount" decimal(19,4) NOT NULL,
  "final_payment_due_at" timestamptz,
  "created_at" timestamptz NOT NULL,
  "confirmed_at" timestamptz,
  "cancelled_at" timestamptz,
  "cancellation_reason" text,
  "settlement_onboard_account_id" uuid
);

CREATE TABLE "BookingPassenger" (
  "booking_passenger_id" uuid PRIMARY KEY NOT NULL,
  "booking_id" uuid NOT NULL,
  "passenger_id" uuid NOT NULL,
  "participation_status" "BookingPassenger_participation_status" NOT NULL,
  "requested_cabin_category" varchar(255)
);

CREATE TABLE "BookingContract" (
  "booking_contract_id" uuid PRIMARY KEY NOT NULL,
  "booking_id" uuid NOT NULL,
  "contract_reference" varchar(255) UNIQUE NOT NULL,
  "document_reference" varchar(255) NOT NULL,
  "accepted_by_booking_passenger_id" uuid,
  "accepted_at" timestamptz,
  "status" "BookingContract_status" NOT NULL
);

CREATE TABLE "CabinAssignment" (
  "cabin_assignment_id" uuid PRIMARY KEY NOT NULL,
  "booking_passenger_id" uuid NOT NULL,
  "cabin_id" uuid NOT NULL,
  "valid_from" timestamptz NOT NULL,
  "valid_to" timestamptz,
  "assigned_by_user_id" uuid NOT NULL,
  "change_reason" text,
  CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE "Notification" (
  "notification_id" uuid PRIMARY KEY NOT NULL,
  "recipient_user_id" uuid NOT NULL,
  "booking_passenger_id" uuid,
  "notification_type" varchar(255) NOT NULL,
  "title" varchar(255) NOT NULL,
  "content" text NOT NULL,
  "created_at" timestamptz NOT NULL,
  "read_at" timestamptz
);

CREATE TABLE "CruiseCheckin" (
  "cruise_checkin_id" uuid PRIMARY KEY NOT NULL,
  "booking_passenger_id" uuid UNIQUE NOT NULL,
  "checkin_status" "CruiseCheckin_checkin_status" NOT NULL,
  "identity_document_snapshot" jsonb,
  "emergency_contact_name" varchar(255),
  "emergency_contact_phone" varchar(255),
  "location_tracking_allowed" boolean NOT NULL,
  "location_sharing_allowed" boolean NOT NULL,
  "started_at" timestamptz,
  "completed_at" timestamptz,
  "boarding_ready_at" timestamptz,
  "boarding_block_reason" text
);

CREATE TABLE "CheckinStep" (
  "checkin_step_id" uuid PRIMARY KEY NOT NULL,
  "cruise_checkin_id" uuid NOT NULL,
  "step_code" "CheckinStep_step_code" NOT NULL,
  "is_required" boolean NOT NULL,
  "status" "CheckinStep_status" NOT NULL,
  "checked_by_user_id" uuid,
  "checked_at" timestamptz,
  "review_note" text
);

CREATE TABLE "PassengerIdentifier" (
  "passenger_identifier_id" uuid PRIMARY KEY NOT NULL,
  "cruise_checkin_id" uuid NOT NULL,
  "identifier_type" "PassengerIdentifier_identifier_type" NOT NULL,
  "secure_token_reference" varchar(255) UNIQUE NOT NULL,
  "issued_at" timestamptz NOT NULL,
  "valid_to" timestamptz NOT NULL,
  "status" "PassengerIdentifier_status" NOT NULL,
  "previous_identifier_id" uuid,
  "revoked_at" timestamptz,
  "last_known_area" varchar(255),
  "location_updated_at" timestamptz
);

CREATE TABLE "GangwayEvent" (
  "gangway_event_id" uuid PRIMARY KEY NOT NULL,
  "passenger_identifier_id" uuid NOT NULL,
  "cruise_stop_id" uuid,
  "event_type" "GangwayEvent_event_type" NOT NULL,
  "occurred_at" timestamptz NOT NULL,
  "result" "GangwayEvent_result" NOT NULL,
  "rejection_reason" text,
  "idempotency_key" varchar(255) UNIQUE NOT NULL
);

CREATE TABLE "GuidanceSession" (
  "guidance_session_id" uuid PRIMARY KEY NOT NULL,
  "cruise_id" uuid NOT NULL,
  "session_name" varchar(255) NOT NULL,
  "location" varchar(255) NOT NULL,
  "start_at" timestamptz NOT NULL,
  "end_at" timestamptz NOT NULL,
  "instructor_user_id" uuid NOT NULL,
  "status" "GuidanceSession_status" NOT NULL,
  CHECK (end_at > start_at)
);

CREATE TABLE "GuidanceSessionParticipant" (
  "guidance_session_participant_id" uuid PRIMARY KEY NOT NULL,
  "guidance_session_id" uuid NOT NULL,
  "booking_passenger_id" uuid NOT NULL,
  "attendance_status" "GuidanceSessionParticipant_attendance_status" NOT NULL,
  "attended_at" timestamptz
);

CREATE TABLE "CruiseCheckout" (
  "cruise_checkout_id" uuid PRIMARY KEY NOT NULL,
  "booking_passenger_id" uuid UNIQUE NOT NULL,
  "final_settlement_id" uuid,
  "checkout_status" "CruiseCheckout_checkout_status" NOT NULL,
  "requested_at" timestamptz,
  "cabin_released_at" timestamptz,
  "exit_verified_by_user_id" uuid,
  "completed_at" timestamptz,
  "block_reason" text
);

CREATE TABLE "TourPackage" (
  "tour_package_id" uuid PRIMARY KEY NOT NULL,
  "package_code" varchar(50) UNIQUE NOT NULL,
  "package_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "package_type" "TourPackage_package_type" NOT NULL,
  "price" decimal(19,4) NOT NULL,
  "currency_code" char(3) NOT NULL,
  "status" "TourPackage_status" NOT NULL,
  "start_date" date,
  "end_date" date,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (price >= 0),
  CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE TABLE "BookingPassengerPackage" (
  "booking_passenger_id" uuid PRIMARY KEY NOT NULL,
  "tour_package_id" uuid NOT NULL,
  "assigned_at" timestamptz NOT NULL,
  "agreed_price" decimal(19,4) NOT NULL,
  "currency" char(3) NOT NULL,
  CHECK (agreed_price >= 0)
);

CREATE TABLE "TourPackageService" (
  "tour_package_id" uuid NOT NULL,
  "service_id" uuid NOT NULL,
  "benefit_type" "TourPackageService_benefit_type" NOT NULL,
  PRIMARY KEY ("tour_package_id", "service_id")
);

CREATE TABLE "TourPackageActivity" (
  "tour_package_id" uuid NOT NULL,
  "onboard_activity_id" uuid NOT NULL,
  "benefit_type" "TourPackageActivity_benefit_type" NOT NULL,
  PRIMARY KEY ("tour_package_id", "onboard_activity_id")
);

CREATE TABLE "TourPackageExcursion" (
  "tour_package_id" uuid NOT NULL,
  "shore_excursion_id" uuid NOT NULL,
  "benefit_type" "TourPackageExcursion_benefit_type" NOT NULL,
  PRIMARY KEY ("tour_package_id", "shore_excursion_id")
);

CREATE TABLE "Service" (
  "service_id" uuid PRIMARY KEY NOT NULL,
  "service_code" varchar(50) UNIQUE NOT NULL,
  "service_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "service_type" varchar(50) NOT NULL,
  "price" decimal(19,4),
  "currency_code" char(3),
  "capacity" int,
  "status" "Service_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (price IS NULL OR price >= 0),
  CHECK (capacity IS NULL OR capacity > 0),
  CHECK (price IS NULL OR currency_code IS NOT NULL)
);

CREATE TABLE "Product" (
  "product_id" uuid PRIMARY KEY NOT NULL,
  "product_code" varchar(50) UNIQUE NOT NULL,
  "product_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "product_type" varchar(50) NOT NULL,
  "unit_price" decimal(19,4) NOT NULL,
  "currency_code" char(3) NOT NULL,
  "status" "Product_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (unit_price >= 0)
);

CREATE TABLE "ServiceRequest" (
  "service_request_id" uuid PRIMARY KEY NOT NULL,
  "passenger_id" uuid NOT NULL,
  "service_id" uuid NOT NULL,
  "booking_id" uuid,
  "request_time" timestamptz NOT NULL,
  "quantity" int NOT NULL,
  "status" "ServiceRequest_status" NOT NULL,
  "assigned_user_id" uuid,
  "note" varchar(500),
  "completed_at" timestamptz,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  "booking_passenger_id" uuid,
  CHECK (quantity > 0)
);

CREATE TABLE "Feedback" (
  "feedback_id" uuid PRIMARY KEY NOT NULL,
  "passenger_id" uuid NOT NULL,
  "service_id" uuid,
  "rating" int NOT NULL,
  "comment" varchar(2000),
  "submitted_at" timestamptz NOT NULL,
  "status" varchar(50) NOT NULL,
  "registration_id" uuid,
  "booking_passenger_id" uuid
);

CREATE TABLE "OnboardActivity" (
  "onboard_activity_id" uuid PRIMARY KEY NOT NULL,
  "activity_code" varchar(50) UNIQUE NOT NULL,
  "activity_name" varchar(200) NOT NULL,
  "description" varchar(1000),
  "start_time" timestamptz NOT NULL,
  "end_time" timestamptz NOT NULL,
  "location" varchar(250),
  "capacity" int NOT NULL,
  "price" decimal(19,4),
  "currency_code" char(3),
  "status" "OnboardActivity_status" NOT NULL,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  "cruise_id" uuid NOT NULL,
  CHECK (capacity > 0),
  CHECK (end_time > start_time),
  CHECK (price IS NULL OR currency_code IS NOT NULL)
);

CREATE TABLE "ShoreExcursion" (
  "shore_excursion_id" uuid PRIMARY KEY NOT NULL,
  "cruise_stop_id" uuid NOT NULL,
  "excursion_code" varchar(50),
  "excursion_name" varchar(250) NOT NULL,
  "supplier_id" uuid NOT NULL,
  "start_time" timestamptz NOT NULL,
  "planned_return_time" timestamptz NOT NULL,
  "meeting_point" varchar(250) NOT NULL,
  "minimum_participants" int,
  "maximum_capacity" int NOT NULL,
  "price" decimal(19,4),
  "currency_code" char(3),
  "status" "ShoreExcursion_status" NOT NULL,
  "cancellation_reason" varchar(500),
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (maximum_capacity > 0),
  CHECK (planned_return_time > start_time)
);

CREATE TABLE "Supplier" (
  "supplier_id" uuid PRIMARY KEY NOT NULL,
  "supplier_name" varchar(250) NOT NULL,
  "supplier_type" varchar(100),
  "contact_name" varchar(200) NOT NULL,
  "contact_phone" varchar(50),
  "contact_email" varchar(255),
  "status" "Supplier_status" NOT NULL,
  "notes" varchar(1000),
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL
);

CREATE TABLE "Registration" (
  "registration_id" uuid PRIMARY KEY NOT NULL,
  "passenger_id" uuid NOT NULL,
  "onboard_activity_id" uuid,
  "registration_time" timestamptz NOT NULL,
  "quantity" int NOT NULL,
  "status" "Registration_status" NOT NULL,
  "cancellation_reason" varchar(500),
  "notes" varchar(500),
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  "booking_passenger_id" uuid NOT NULL,
  "shore_excursion_id" uuid,
  CHECK (quantity > 0),
  CHECK ((onboard_activity_id IS NOT NULL AND shore_excursion_id IS NULL) OR (onboard_activity_id IS NULL AND shore_excursion_id IS NOT NULL))
);

CREATE TABLE "Participation" (
  "participation_id" uuid PRIMARY KEY NOT NULL,
  "registration_id" uuid UNIQUE NOT NULL,
  "passenger_id" uuid NOT NULL,
  "onboard_activity_id" uuid,
  "check_in_time" timestamptz,
  "check_out_time" timestamptz,
  "status" "Participation_status" NOT NULL,
  "notes" varchar(500),
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  "shore_excursion_id" uuid,
  CHECK ((onboard_activity_id IS NOT NULL AND shore_excursion_id IS NULL) OR (onboard_activity_id IS NULL AND shore_excursion_id IS NOT NULL)),
  CHECK (check_out_time IS NULL OR (check_in_time IS NOT NULL AND check_out_time >= check_in_time))
);

CREATE TABLE "TourGroup" (
  "tour_group_id" uuid PRIMARY KEY NOT NULL,
  "shore_excursion_id" uuid NOT NULL,
  "group_code" varchar(50) NOT NULL,
  "maximum_capacity" int NOT NULL,
  "guide_id" uuid,
  "vehicle_id" uuid,
  "meeting_point" varchar(250),
  "assembly_time" timestamptz,
  "planned_departure_time" timestamptz,
  "planned_return_time" timestamptz,
  "actual_departure_time" timestamptz,
  "actual_return_time" timestamptz,
  "status" "TourGroup_status" NOT NULL,
  "notes" varchar(500),
  CHECK (maximum_capacity > 0)
);

CREATE TABLE "TourGroupPassenger" (
  "tour_group_passenger_id" uuid PRIMARY KEY NOT NULL,
  "tour_group_id" uuid NOT NULL,
  "passenger_id" uuid NOT NULL,
  "assignment_status" "TourGroupPassenger_assignment_status" NOT NULL,
  "assigned_at" timestamptz NOT NULL,
  "checked_in_at" timestamptz,
  "returned_at" timestamptz,
  "created_at" timestamptz NOT NULL
);

CREATE TABLE "OnboardAccount" (
  "onboard_account_id" uuid PRIMARY KEY NOT NULL,
  "cruise_id" uuid NOT NULL,
  "status" "OnboardAccount_status" NOT NULL,
  "credit_limit" decimal(19,4),
  "activated_at" timestamptz,
  "closed_at" timestamptz,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (credit_limit IS NULL OR credit_limit >= 0)
);

CREATE TABLE "OnboardAccountMember" (
  "account_member_id" uuid PRIMARY KEY NOT NULL,
  "onboard_account_id" uuid NOT NULL,
  "passenger_id" uuid NOT NULL,
  "member_role" "OnboardAccountMember_member_role" NOT NULL,
  "charge_permission" "OnboardAccountMember_charge_permission" NOT NULL,
  "status" "OnboardAccountMember_status" NOT NULL,
  "effective_from" timestamptz,
  "effective_to" timestamptz,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL
);

CREATE TABLE "SpendingLimit" (
  "spending_limit_id" uuid PRIMARY KEY NOT NULL,
  "account_member_id" uuid NOT NULL,
  "limit_amount" decimal(19,4) NOT NULL,
  "currency_code" char(3) NOT NULL,
  "effective_from" timestamptz NOT NULL,
  "effective_to" timestamptz,
  "status" "SpendingLimit_status" NOT NULL,
  "set_by_user_id" uuid,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (limit_amount >= 0)
);

CREATE TABLE "OnboardTransaction" (
  "transaction_id" uuid PRIMARY KEY NOT NULL,
  "onboard_account_id" uuid NOT NULL,
  "passenger_id" uuid,
  "transaction_type" "OnboardTransaction_transaction_type" NOT NULL,
  "amount" decimal(19,4) NOT NULL,
  "currency_code" char(3) NOT NULL,
  "transaction_time" timestamptz NOT NULL,
  "posted_at" timestamptz,
  "status" "OnboardTransaction_status" NOT NULL,
  "source_type" varchar(255),
  "source_reference_id" uuid,
  "original_transaction_id" uuid,
  "created_by_user_id" uuid,
  "sync_status" "OnboardTransaction_sync_status",
  "synced_at" timestamptz,
  "description" varchar(500),
  CHECK (amount <> 0)
);

CREATE TABLE "Charge" (
  "charge_id" uuid PRIMARY KEY NOT NULL,
  "transaction_id" uuid UNIQUE NOT NULL,
  "passenger_id" uuid NOT NULL,
  "source_type" varchar(255) NOT NULL,
  "source_reference_id" uuid NOT NULL,
  "quantity" decimal(12,3),
  "unit_price" decimal(19,4),
  "gross_amount" decimal(19,4),
  "discount_amount" decimal(19,4),
  "net_amount" decimal(19,4) NOT NULL,
  "charged_at" timestamptz NOT NULL,
  "created_by_user_id" uuid,
  "note" varchar(500),
  "tax_amount" decimal(19,4) NOT NULL,
  CHECK (quantity IS NULL OR quantity > 0),
  CHECK (unit_price IS NULL OR unit_price >= 0),
  CHECK (gross_amount IS NULL OR gross_amount >= 0),
  CHECK (discount_amount IS NULL OR discount_amount >= 0),
  CHECK (net_amount >= 0),
  CHECK (tax_amount >= 0)
);

CREATE TABLE "ChargeDispute" (
  "charge_dispute_id" uuid PRIMARY KEY NOT NULL,
  "charge_id" uuid NOT NULL,
  "passenger_id" uuid NOT NULL,
  "disputed_amount" decimal(19,4) NOT NULL,
  "reason_code" "ChargeDispute_reason_code" NOT NULL,
  "description" varchar(1000) NOT NULL,
  "status" "ChargeDispute_status" NOT NULL,
  "reported_at" timestamptz NOT NULL,
  "assigned_to_user_id" uuid,
  "resolution_type" "ChargeDispute_resolution_type",
  "resolution_note" varchar(1000),
  "approved_amount" decimal(19,4),
  "resolution_transaction_id" uuid,
  "resolved_by_user_id" uuid,
  "resolved_at" timestamptz,
  "created_at" timestamptz NOT NULL,
  "updated_at" timestamptz NOT NULL,
  CHECK (disputed_amount > 0)
);

CREATE TABLE "Invoice" (
  "invoice_id" uuid PRIMARY KEY NOT NULL,
  "booking_id" uuid,
  "onboard_account_id" uuid,
  "invoice_reference" varchar(255) UNIQUE NOT NULL,
  "document_type" "Invoice_document_type" NOT NULL,
  "version_no" int NOT NULL,
  "issued_at" timestamptz NOT NULL,
  "source_cutoff_at" timestamptz NOT NULL,
  "currency" char(3) NOT NULL,
  "snapshot_total_amount" decimal(19,4) NOT NULL,
  "snapshot_net_amount_due" decimal(19,4) NOT NULL,
  "status" "Invoice_status" NOT NULL,
  "document_reference" varchar(255),
  CHECK (version_no > 0),
  CHECK ((booking_id IS NOT NULL AND onboard_account_id IS NULL) OR (booking_id IS NULL AND onboard_account_id IS NOT NULL))
);

CREATE TABLE "InvoiceLine" (
  "invoice_line_id" uuid PRIMARY KEY NOT NULL,
  "invoice_id" uuid NOT NULL,
  "source_charge_id" uuid,
  "line_no" int NOT NULL,
  "description_snapshot" text NOT NULL,
  "quantity_snapshot" decimal(19,4) NOT NULL,
  "base_amount_snapshot" decimal(19,4) NOT NULL,
  "tax_amount_snapshot" decimal(19,4) NOT NULL,
  "total_amount_snapshot" decimal(19,4) NOT NULL,
  "booking_fare_amount_snapshot" decimal(19,4),
  CHECK (quantity_snapshot > 0),
  CHECK (base_amount_snapshot >= 0),
  CHECK (tax_amount_snapshot >= 0),
  CHECK (total_amount_snapshot >= 0),
  CHECK (booking_fare_amount_snapshot IS NULL OR booking_fare_amount_snapshot >= 0),
  CHECK (line_no > 0),
  CHECK ((source_charge_id IS NOT NULL AND booking_fare_amount_snapshot IS NULL) OR (source_charge_id IS NULL AND booking_fare_amount_snapshot IS NOT NULL))
);

CREATE TABLE "Installment" (
  "installment_id" uuid PRIMARY KEY NOT NULL,
  "booking_id" uuid NOT NULL,
  "sequence_no" int NOT NULL,
  "installment_type" "Installment_installment_type" NOT NULL,
  "required_amount" decimal(19,4) NOT NULL,
  "currency" char(3) NOT NULL,
  "due_at" timestamptz NOT NULL,
  "status" "Installment_status" NOT NULL,
  CHECK (sequence_no > 0),
  CHECK (required_amount >= 0)
);

CREATE TABLE "Payment" (
  "payment_id" uuid PRIMARY KEY NOT NULL,
  "booking_id" uuid,
  "onboard_account_id" uuid,
  "installment_id" uuid,
  "final_settlement_id" uuid,
  "operation_type" "Payment_operation_type" NOT NULL,
  "retry_of_payment_id" uuid,
  "parent_operation_id" uuid,
  "payment_method" "Payment_payment_method" NOT NULL,
  "requested_amount" decimal(19,4) NOT NULL,
  "confirmed_amount" decimal(19,4),
  "currency" char(3) NOT NULL,
  "requested_at" timestamptz NOT NULL,
  "confirmed_at" timestamptz,
  "processor_reference" varchar(255),
  "internal_receipt_reference" varchar(255),
  "status" "Payment_status" NOT NULL,
  "failure_reason" text,
  "idempotency_key" varchar(255) UNIQUE NOT NULL,
  CHECK (requested_amount > 0),
  CHECK ((booking_id IS NOT NULL AND onboard_account_id IS NULL) OR (booking_id IS NULL AND onboard_account_id IS NOT NULL))
);

CREATE TABLE "Refund" (
  "refund_id" uuid PRIMARY KEY NOT NULL,
  "original_payment_id" uuid NOT NULL,
  "final_settlement_id" uuid,
  "refund_reason" text NOT NULL,
  "requested_amount" decimal(19,4) NOT NULL,
  "approved_amount" decimal(19,4),
  "currency" char(3) NOT NULL,
  "requested_at" timestamptz NOT NULL,
  "approved_by_user_id" uuid,
  "approved_at" timestamptz,
  "completed_at" timestamptz,
  "processor_or_receipt_reference" varchar(255),
  "status" "Refund_status" NOT NULL,
  "idempotency_key" varchar(255) UNIQUE NOT NULL,
  CHECK (approved_amount IS NULL OR approved_amount >= 0),
  CHECK (requested_amount > 0)
);

CREATE TABLE "RefundLine" (
  "refund_line_id" uuid PRIMARY KEY NOT NULL,
  "refund_id" uuid NOT NULL,
  "invoice_line_id" uuid NOT NULL,
  "base_refund_amount" decimal(19,4) NOT NULL,
  "tax_refund_amount" decimal(19,4) NOT NULL,
  "reversal_charge_id" uuid,
  CHECK (base_refund_amount + tax_refund_amount > 0),
  CHECK (base_refund_amount >= 0),
  CHECK (tax_refund_amount >= 0)
);

CREATE TABLE "FinalSettlement" (
  "final_settlement_id" uuid PRIMARY KEY NOT NULL,
  "onboard_account_id" uuid NOT NULL,
  "final_invoice_id" uuid UNIQUE NOT NULL,
  "source_cutoff_at" timestamptz NOT NULL,
  "amount_due_snapshot" decimal(19,4) NOT NULL,
  "refund_due_snapshot" decimal(19,4) NOT NULL,
  "statement_confirmed_at" timestamptz,
  "started_at" timestamptz NOT NULL,
  "completed_at" timestamptz,
  "status" "FinalSettlement_status" NOT NULL,
  "handled_by_user_id" uuid,
  CHECK (amount_due_snapshot >= 0),
  CHECK (refund_due_snapshot >= 0)
);

CREATE UNIQUE INDEX ON "Deck" ("ship_id", "deck_number");

CREATE UNIQUE INDEX ON "Deck" ("deck_id", "ship_id");

CREATE UNIQUE INDEX ON "Cabin" ("ship_id", "cabin_number");

CREATE UNIQUE INDEX ON "Cabin" ("cabin_id", "ship_id");

CREATE UNIQUE INDEX ON "Cruise" ("cruise_id", "ship_id");

CREATE UNIQUE INDEX ON "ItineraryDay" ("itinerary_id", "day_number");

CREATE UNIQUE INDEX ON "ItineraryDay" ("itinerary_id", "date");

CREATE UNIQUE INDEX ON "ItineraryDay" ("itinerary_day_id", "itinerary_id");

CREATE UNIQUE INDEX ON "CruiseStop" ("itinerary_id", "stop_order");

CREATE UNIQUE INDEX ON "BookingPassenger" ("booking_id", "passenger_id");

CREATE UNIQUE INDEX ON "BookingPassenger" ("booking_passenger_id", "booking_id");

CREATE UNIQUE INDEX ON "CheckinStep" ("cruise_checkin_id", "step_code");

CREATE UNIQUE INDEX ON "GuidanceSessionParticipant" ("guidance_session_id", "booking_passenger_id");

CREATE UNIQUE INDEX ON "TourGroup" ("shore_excursion_id", "group_code");

CREATE UNIQUE INDEX ON "TourGroupPassenger" ("tour_group_id", "passenger_id");

CREATE UNIQUE INDEX ON "OnboardAccountMember" ("onboard_account_id", "passenger_id");

CREATE UNIQUE INDEX ON "InvoiceLine" ("invoice_id", "line_no");

CREATE UNIQUE INDEX ON "Installment" ("booking_id", "sequence_no");

CREATE UNIQUE INDEX ON "RefundLine" ("refund_id", "invoice_line_id");

COMMENT ON COLUMN "Ship"."ship_id" IS 'Khóa chính';

COMMENT ON COLUMN "Ship"."ship_code" IS 'Mã nghiệp vụ, không đổi sau khi tạo (BR-SH-01)';

COMMENT ON COLUMN "Ship"."ship_name" IS 'Không chấp nhận chuỗi rỗng hoặc chỉ có khoảng trắng';

COMMENT ON COLUMN "Ship"."status" IS 'Quyết định tàu có được gán cho Cruise hay không';

COMMENT ON COLUMN "Ship"."total_capacity" IS 'Tổng sức chứa hành khách (BR-SH-02)';

COMMENT ON COLUMN "Ship"."total_decks" IS 'Số boong (BR-SH-03)';

COMMENT ON COLUMN "Ship"."commission_date" IS 'Không dùng CURRENT_DATE trong CHECK; kiểm tra ngày hiệu lực ở service hoặc trigger';

COMMENT ON COLUMN "Ship"."created_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Ship"."updated_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Deck"."deck_id" IS 'Khóa chính';

COMMENT ON COLUMN "Deck"."ship_id" IS 'Boong thuộc đúng một tàu';

COMMENT ON COLUMN "Deck"."deck_number" IS 'Số boong; không lớn hơn ships.total_decks';

COMMENT ON COLUMN "Deck"."deck_name" IS 'Tên hiển thị';

COMMENT ON COLUMN "Deck"."created_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Deck"."updated_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Cabin"."cabin_id" IS 'Khóa chính';

COMMENT ON COLUMN "Cabin"."ship_id" IS 'BR-CAB-01';

COMMENT ON COLUMN "Cabin"."deck_id" IS 'BR-CAB-02';

COMMENT ON COLUMN "Cabin"."cabin_number" IS 'Đặc tả ghi UNIQUE toàn bảng, nhưng BR-CAB-03 chỉ yêu cầu duy nhất trong cùng tàu';

COMMENT ON COLUMN "Cabin"."cabin_type" IS 'Chuẩn hóa chuỗi viết hoa, không dấu cách';

COMMENT ON COLUMN "Cabin"."capacity" IS 'BR-CAB-04';

COMMENT ON COLUMN "Cabin"."status" IS 'Xem lưu ý về OCCUPIED bên dưới';

COMMENT ON COLUMN "Cabin"."is_accessible" IS 'Cabin dành cho người khuyết tật';

COMMENT ON COLUMN "Cabin"."created_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Cabin"."updated_at" IS 'Đặc tả ghi update_at, thống nhất thành updated_at';

COMMENT ON COLUMN "Cruise"."cruise_id" IS 'Khóa chính';

COMMENT ON COLUMN "Cruise"."cruise_code" IS 'Mã chuyến';

COMMENT ON COLUMN "Cruise"."cruise_name" IS 'Tên chuyến';

COMMENT ON COLUMN "Cruise"."ship_id" IS 'Đặc tả ghi bắt buộc, nhưng luồng gán tàu diễn ra sau khi tạo; cho phép NULL khi DRAFT';

COMMENT ON COLUMN "Cruise"."cruise_type" IS 'Đặc tả viết lẫn MULTI-DAYS và MULTI_DAYS, thống nhất MULTI_DAYS';

COMMENT ON COLUMN "Cruise"."departure_datetime" IS 'Giờ khởi hành';

COMMENT ON COLUMN "Cruise"."return_datetime" IS 'BR-CRUISE-01';

COMMENT ON COLUMN "Cruise"."departure_port_id" IS 'Cảng đi';

COMMENT ON COLUMN "Cruise"."return_port_id" IS 'Cảng về, được phép trùng cảng đi (chuyến vòng)';

COMMENT ON COLUMN "Cruise"."status" IS 'Xem mục 3';

COMMENT ON COLUMN "Cruise"."created_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Cruise"."updated_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "Port"."port_id" IS 'Mã cảng.';

COMMENT ON COLUMN "Port"."port_code" IS 'Mã nghiệp vụ/định danh cảng.';

COMMENT ON COLUMN "Port"."port_name" IS 'Tên cảng.';

COMMENT ON COLUMN "Port"."country_code" IS 'Quốc gia.';

COMMENT ON COLUMN "Port"."time_zone_id" IS 'Múi giờ địa phương.';

COMMENT ON COLUMN "Port"."latitude" IS 'Vĩ độ.';

COMMENT ON COLUMN "Port"."longitude" IS 'Kinh độ.';

COMMENT ON COLUMN "Port"."status" IS 'ACTIVE/INACTIVE.';

COMMENT ON COLUMN "Port"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Port"."updated_at" IS 'Cập nhật gần nhất.';

COMMENT ON COLUMN "Destination"."destination_id" IS 'Mã điểm đến.';

COMMENT ON COLUMN "Destination"."destination_name" IS 'Tên điểm đến.';

COMMENT ON COLUMN "Destination"."country_code" IS 'Quốc gia.';

COMMENT ON COLUMN "Destination"."description" IS 'Mô tả điểm đến.';

COMMENT ON COLUMN "Destination"."status" IS 'ACTIVE/INACTIVE.';

COMMENT ON COLUMN "Destination"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Destination"."updated_at" IS 'Cập nhật gần nhất.';

COMMENT ON TABLE "Itinerary" IS 'One current itinerary per cruise (integration choice). Dates and lifecycle must agree with Cruise; Itinerary approval remains separate from cruise operation status.';

COMMENT ON COLUMN "Itinerary"."itinerary_id" IS 'Mã hải trình.';

COMMENT ON COLUMN "Itinerary"."cruise_id" IS 'Chuyến du thuyền sở hữu hải trình.';

COMMENT ON COLUMN "Itinerary"."itinerary_name" IS 'Tên hiển thị của hải trình.';

COMMENT ON COLUMN "Itinerary"."start_date" IS 'Ngày bắt đầu.';

COMMENT ON COLUMN "Itinerary"."end_date" IS 'Ngày kết thúc.';

COMMENT ON COLUMN "Itinerary"."itinerary_type" IS 'ROUND_TRIP hoặc ONE_WAY.';

COMMENT ON COLUMN "Itinerary"."status" IS 'Trạng thái vòng đời hải trình.';

COMMENT ON COLUMN "Itinerary"."approved_by_user_id" IS 'Người phê duyệt.';

COMMENT ON COLUMN "Itinerary"."approved_at" IS 'Thời điểm phê duyệt.';

COMMENT ON COLUMN "Itinerary"."created_by_user_id" IS 'Người tạo hải trình.';

COMMENT ON COLUMN "Itinerary"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Itinerary"."updated_at" IS 'Cập nhật gần nhất.';

COMMENT ON COLUMN "ItineraryDay"."itinerary_day_id" IS 'Mã ngày hành trình.';

COMMENT ON COLUMN "ItineraryDay"."itinerary_id" IS 'Hải trình chứa ngày này.';

COMMENT ON COLUMN "ItineraryDay"."day_number" IS 'Số thứ tự ngày.';

COMMENT ON COLUMN "ItineraryDay"."date" IS 'Ngày lịch thực tế.';

COMMENT ON COLUMN "ItineraryDay"."day_type" IS 'SEA_DAY hoặc PORT_DAY.';

COMMENT ON COLUMN "ItineraryDay"."ship_time_zone_id" IS 'Múi giờ/giờ tàu áp dụng cho ngày.';

COMMENT ON COLUMN "ItineraryDay"."description" IS 'Mô tả bổ sung.';

COMMENT ON COLUMN "CruiseStop"."cruise_stop_id" IS 'Mã lượt ghé.';

COMMENT ON COLUMN "CruiseStop"."itinerary_id" IS 'Hải trình chứa lượt ghé.';

COMMENT ON COLUMN "CruiseStop"."arrival_itinerary_day_id" IS 'Ngày hành trình khi tàu đến.';

COMMENT ON COLUMN "CruiseStop"."departure_itinerary_day_id" IS 'Ngày hành trình khi tàu rời.';

COMMENT ON COLUMN "CruiseStop"."port_id" IS 'Cảng thực tế tàu ghé.';

COMMENT ON COLUMN "CruiseStop"."destination_id" IS 'Điểm đến mà lượt ghé phục vụ.';

COMMENT ON COLUMN "CruiseStop"."stop_order" IS 'Thứ tự lượt ghé.';

COMMENT ON COLUMN "CruiseStop"."planned_arrival" IS 'Thời điểm dự kiến đến.';

COMMENT ON COLUMN "CruiseStop"."planned_departure" IS 'Thời điểm dự kiến rời.';

COMMENT ON COLUMN "CruiseStop"."actual_arrival" IS 'Thời điểm đến thực tế.';

COMMENT ON COLUMN "CruiseStop"."actual_departure" IS 'Thời điểm rời thực tế.';

COMMENT ON COLUMN "CruiseStop"."call_type" IS 'NORMAL, LATE_NIGHT, OVERNIGHT.';

COMMENT ON COLUMN "CruiseStop"."arrival_method" IS 'BERTH hoặc TENDER khi áp dụng.';

COMMENT ON COLUMN "CruiseStop"."all_aboard_time" IS 'Mốc khách phải quay lại tàu.';

COMMENT ON COLUMN "CruiseStop"."tender_transfer_minutes" IS 'Thời gian tender dự kiến.';

COMMENT ON COLUMN "CruiseStop"."status" IS 'Trạng thái lượt ghé.';

COMMENT ON COLUMN "CruiseStop"."cancellation_reason" IS 'Lý do bỏ/hủy lượt ghé.';

COMMENT ON COLUMN "CruiseStop"."substituted_by_stop_id" IS 'Stop thay thế nếu có.';

COMMENT ON COLUMN "CruiseStop"."port_contact_name" IS 'Đầu mối phối hợp tại cảng nếu chỉ lưu một đầu mối chính.';

COMMENT ON COLUMN "CruiseStop"."port_contact_role" IS 'Vai trò đầu mối.';

COMMENT ON COLUMN "CruiseStop"."port_contact_phone" IS 'Thông tin liên hệ.';

COMMENT ON COLUMN "CruiseStop"."port_contact_email" IS 'Thông tin liên hệ.';

COMMENT ON COLUMN "CruiseStop"."port_contact_responsibility" IS 'Phạm vi trách nhiệm.';

COMMENT ON COLUMN "CruiseStop"."notes" IS 'Ghi chú vận hành.';

COMMENT ON COLUMN "OperationalRegulation"."regulation_id" IS 'Đặc tả ghi UUID, đổi sang INT cho thống nhất với các bảng khác';

COMMENT ON COLUMN "OperationalRegulation"."regulation_code" IS 'BR-REG-01';

COMMENT ON COLUMN "OperationalRegulation"."name" IS 'Tên quy định';

COMMENT ON COLUMN "OperationalRegulation"."description" IS 'Mô tả chi tiết';

COMMENT ON COLUMN "OperationalRegulation"."regulation_type" IS 'Giá trị đề xuất vì đặc tả chưa liệt kê';

COMMENT ON COLUMN "OperationalRegulation"."scope" IS 'GLOBAL áp dụng toàn hệ thống; SHIP/CRUISE cần có bảng nối';

COMMENT ON COLUMN "OperationalRegulation"."value" IS 'Đặc tả ghi Decimal/String; lưu dạng chuỗi và ép kiểu theo regulation_type và unit';

COMMENT ON COLUMN "OperationalRegulation"."unit" IS 'Giá trị đề xuất; NONE hoặc NULL khi value là văn bản';

COMMENT ON COLUMN "OperationalRegulation"."effective_from" IS 'Bắt đầu hiệu lực';

COMMENT ON COLUMN "OperationalRegulation"."effective_to" IS 'NULL nghĩa là không có ngày kết thúc (BR-REG-02)';

COMMENT ON COLUMN "OperationalRegulation"."status" IS 'Giá trị đề xuất; chỉ ACTIVE được dùng để kiểm tra (BR-REG-03)';

COMMENT ON COLUMN "OperationalRegulation"."created_at" IS 'Đặc tả chưa có hai cột này, thêm cho thống nhất';

COMMENT ON COLUMN "OperationalRegulation"."updated_at" IS 'Đặc tả chưa có hai cột này, thêm cho thống nhất';

COMMENT ON COLUMN "OperationalIssue"."issue_id" IS 'Khóa chính';

COMMENT ON COLUMN "OperationalIssue"."issue_code" IS 'BR-OI-01';

COMMENT ON COLUMN "OperationalIssue"."cruise_id" IS 'BR-OI-03';

COMMENT ON COLUMN "OperationalIssue"."ship_id" IS 'BR-OI-02';

COMMENT ON COLUMN "OperationalIssue"."cabin_id" IS 'BR-OI-04';

COMMENT ON COLUMN "OperationalIssue"."issue_type" IS 'Bỏ khoảng trắng thừa so với đặc tả';

COMMENT ON COLUMN "OperationalIssue"."severity" IS 'Mức độ nghiêm trọng';

COMMENT ON COLUMN "OperationalIssue"."description" IS 'Mô tả sự cố';

COMMENT ON COLUMN "OperationalIssue"."reported_at" IS 'Thời điểm báo cáo';

COMMENT ON COLUMN "OperationalIssue"."resolved_at" IS 'Bắt buộc khi RESOLVED/CLOSED';

COMMENT ON COLUMN "OperationalIssue"."status" IS 'Xem mục 3';

COMMENT ON COLUMN "OperationalIssue"."reported_by" IS 'Người báo cáo';

COMMENT ON COLUMN "OperationalIssue"."resolved_by" IS 'Bắt buộc khi RESOLVED/CLOSED';

COMMENT ON COLUMN "OperationalIssue"."resolution" IS 'Bắt buộc khi RESOLVED/CLOSED';

COMMENT ON COLUMN "OperationalIssue"."assigned_to" IS 'Người phụ trách xử lý; giữ lại vì luồng có bước Assign Responsible Staff';

COMMENT ON COLUMN "OperationalIssue"."created_at" IS 'Hệ thống sinh';

COMMENT ON COLUMN "OperationalIssue"."updated_at" IS 'Đặc tả chưa có, thêm cho thống nhất với các bảng khác';

COMMENT ON COLUMN "ItineraryChangeHistory"."change_history_id" IS 'Mã bản ghi lịch sử.';

COMMENT ON COLUMN "ItineraryChangeHistory"."itinerary_id" IS 'Hải trình bị thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."cruise_stop_id" IS 'Stop bị tác động nếu thay đổi ở cấp stop.';

COMMENT ON COLUMN "ItineraryChangeHistory"."changed_entity_type" IS 'Loại đối tượng thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."changed_entity_id" IS 'ID đối tượng thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."change_type" IS 'Loại thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."before_data" IS 'Dữ liệu trước thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."after_data" IS 'Dữ liệu sau thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."change_summary" IS 'Nội dung thay đổi dễ đọc.';

COMMENT ON COLUMN "ItineraryChangeHistory"."reason" IS 'Lý do thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."changed_by_user_id" IS 'Người thay đổi.';

COMMENT ON COLUMN "ItineraryChangeHistory"."changed_at" IS 'Thời điểm thay đổi.';

COMMENT ON COLUMN "User"."user_id" IS 'Mã người dùng.';

COMMENT ON COLUMN "User"."username" IS 'Tên đăng nhập.';

COMMENT ON COLUMN "User"."password_hash" IS 'Mật khẩu đã băm nếu CASMS tự xác thực.';

COMMENT ON COLUMN "User"."email" IS 'Email tài khoản.';

COMMENT ON COLUMN "User"."status" IS 'ACTIVE, LOCKED, DISABLED.';

COMMENT ON COLUMN "User"."user_type" IS 'Nhóm người dùng ở tầng xác thực nếu cần.';

COMMENT ON COLUMN "User"."last_login_at" IS 'Lần đăng nhập gần nhất.';

COMMENT ON COLUMN "User"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "User"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON COLUMN "Passenger"."passenger_id" IS 'khóa chính hồ sơ hành khách';

COMMENT ON COLUMN "Passenger"."user_id" IS 'FK User; NULL nếu khách chưa có tài khoản';

COMMENT ON COLUMN "Passenger"."full_name" IS 'họ tên hành khách';

COMMENT ON COLUMN "Passenger"."date_of_birth" IS 'ngày sinh';

COMMENT ON COLUMN "Passenger"."nationality" IS 'quốc tịch nếu cần cho hồ sơ';

COMMENT ON COLUMN "Passenger"."document_type" IS 'NationalID hoặc Passport theo yêu cầu của chuyến';

COMMENT ON COLUMN "Passenger"."document_number_protected" IS 'số giấy tờ được bảo vệ, không đưa vào QR/thẻ';

COMMENT ON COLUMN "Passenger"."email" IS 'email liên hệ';

COMMENT ON COLUMN "Passenger"."phone_number" IS 'số điện thoại liên hệ';

COMMENT ON COLUMN "Passenger"."created_at" IS 'thời điểm tạo hồ sơ';

COMMENT ON COLUMN "Booking"."booking_id" IS 'khóa chính booking';

COMMENT ON COLUMN "Booking"."cruise_id" IS 'FK Cruise; nguồn chuyến đi duy nhất';

COMMENT ON COLUMN "Booking"."representative_booking_passenger_id" IS 'FK BookingPassenger thuộc cùng booking; NULL khi Draft';

COMMENT ON COLUMN "Booking"."booking_reference" IS 'mã đặt chuyến duy nhất';

COMMENT ON COLUMN "Booking"."booked_by_user_id" IS 'FK User đặt chuyến/đặt thay';

COMMENT ON COLUMN "Booking"."booking_status" IS 'Draft, Confirmed hoặc Cancelled; độc lập với check-in';

COMMENT ON COLUMN "Booking"."agreed_fare_amount" IS 'giá tour đã chốt, không phải số dư còn phải trả; thay giá phải có log';

COMMENT ON COLUMN "Booking"."currency" IS 'tiền tệ booking, MVP dùng một tiền tệ thống nhất';

COMMENT ON COLUMN "Booking"."required_deposit_amount" IS 'mức cọc theo cấu hình đơn giản của nhóm; không mặc định quy tắc mọi hãng';

COMMENT ON COLUMN "Booking"."final_payment_due_at" IS 'hạn thanh toán nếu nhóm dùng lịch trả trước chuyến';

COMMENT ON COLUMN "Booking"."created_at" IS 'thời điểm tạo';

COMMENT ON COLUMN "Booking"."confirmed_at" IS 'thời điểm đủ điều kiện xác nhận';

COMMENT ON COLUMN "Booking"."cancelled_at" IS 'thời điểm hủy';

COMMENT ON COLUMN "Booking"."cancellation_reason" IS 'lý do hủy';

COMMENT ON COLUMN "Booking"."settlement_onboard_account_id" IS 'ADDED: account responsible for the remaining booking fare at final settlement; same cruise, fare allocated once. NULL until an account is assigned or if booking is fully handled before sailing.';

COMMENT ON COLUMN "BookingPassenger"."booking_passenger_id" IS 'khóa chính quan hệ một người đi trong booking';

COMMENT ON COLUMN "BookingPassenger"."booking_id" IS 'FK Booking';

COMMENT ON COLUMN "BookingPassenger"."passenger_id" IS 'FK Passenger; UNIQUE với booking_id';

COMMENT ON COLUMN "BookingPassenger"."participation_status" IS 'Active hoặc Cancelled';

COMMENT ON COLUMN "BookingPassenger"."requested_cabin_category" IS 'hạng phòng khách yêu cầu, nếu chưa gán phòng cụ thể';

COMMENT ON COLUMN "BookingContract"."booking_contract_id" IS 'khóa chính thỏa thuận booking';

COMMENT ON COLUMN "BookingContract"."booking_id" IS 'FK Booking';

COMMENT ON COLUMN "BookingContract"."contract_reference" IS 'mã thỏa thuận';

COMMENT ON COLUMN "BookingContract"."document_reference" IS 'bản điều khoản/tệp đã lưu';

COMMENT ON COLUMN "BookingContract"."accepted_by_booking_passenger_id" IS 'FK BookingPassenger thuộc booking';

COMMENT ON COLUMN "BookingContract"."accepted_at" IS 'thời điểm xác nhận đồng ý';

COMMENT ON COLUMN "BookingContract"."status" IS 'Draft, Accepted hoặc Cancelled';

COMMENT ON COLUMN "CabinAssignment"."cabin_assignment_id" IS 'khóa chính một lần gán phòng';

COMMENT ON COLUMN "CabinAssignment"."booking_passenger_id" IS 'FK BookingPassenger';

COMMENT ON COLUMN "CabinAssignment"."cabin_id" IS 'FK Cabin thuộc tàu/chuyến phù hợp';

COMMENT ON COLUMN "CabinAssignment"."valid_from" IS 'bắt đầu hiệu lực';

COMMENT ON COLUMN "CabinAssignment"."valid_to" IS 'kết thúc hiệu lực; NULL là mapping hiện hành';

COMMENT ON COLUMN "CabinAssignment"."assigned_by_user_id" IS 'FK User gán/đổi phòng';

COMMENT ON COLUMN "CabinAssignment"."change_reason" IS 'lý do thay đổi nếu có';

COMMENT ON COLUMN "Notification"."notification_id" IS 'khóa chính thông báo cho một người nhận';

COMMENT ON COLUMN "Notification"."recipient_user_id" IS 'FK User nhận thông báo';

COMMENT ON COLUMN "Notification"."booking_passenger_id" IS 'FK BookingPassenger nếu gắn với chuyến; có thể NULL';

COMMENT ON COLUMN "Notification"."notification_type" IS 'loại thông báo';

COMMENT ON COLUMN "Notification"."title" IS 'tiêu đề';

COMMENT ON COLUMN "Notification"."content" IS 'nội dung';

COMMENT ON COLUMN "Notification"."created_at" IS 'thời điểm tạo';

COMMENT ON COLUMN "Notification"."read_at" IS 'thời điểm đọc; NULL nếu chưa đọc';

COMMENT ON COLUMN "CruiseCheckin"."cruise_checkin_id" IS 'khóa chính hồ sơ check-in';

COMMENT ON COLUMN "CruiseCheckin"."booking_passenger_id" IS 'FK BookingPassenger; UNIQUE, suy ra Passenger/Booking/Cruise';

COMMENT ON COLUMN "CruiseCheckin"."checkin_status" IS 'NotStarted, InProgress, NeedsReview, Completed hoặc BoardingReady';

COMMENT ON COLUMN "CruiseCheckin"."identity_document_snapshot" IS 'một bộ tên/ngày sinh/giấy tờ được xác nhận theo lần đi; không chứa danh sách giấy tờ';

COMMENT ON COLUMN "CruiseCheckin"."emergency_contact_name" IS 'người liên hệ khẩn cấp chính cho chuyến';

COMMENT ON COLUMN "CruiseCheckin"."emergency_contact_phone" IS 'số liên hệ khẩn cấp';

COMMENT ON COLUMN "CruiseCheckin"."location_tracking_allowed" IS 'cho phép demo định vị nếu chức năng được sử dụng';

COMMENT ON COLUMN "CruiseCheckin"."location_sharing_allowed" IS 'cho phép hiển thị vị trí cho nhóm được phân quyền; không lưu danh sách ID';

COMMENT ON COLUMN "CruiseCheckin"."started_at" IS 'bắt đầu check-in';

COMMENT ON COLUMN "CruiseCheckin"."completed_at" IS 'hoàn tất checklist, chưa đồng nghĩa đã lên tàu';

COMMENT ON COLUMN "CruiseCheckin"."boarding_ready_at" IS 'đủ điều kiện phát QR/credential';

COMMENT ON COLUMN "CruiseCheckin"."boarding_block_reason" IS 'lý do chưa đủ điều kiện';

COMMENT ON COLUMN "CheckinStep"."checkin_step_id" IS 'khóa chính một bước kiểm tra';

COMMENT ON COLUMN "CheckinStep"."cruise_checkin_id" IS 'FK CruiseCheckin';

COMMENT ON COLUMN "CheckinStep"."step_code" IS 'Identity, Document, EmergencyContact hoặc PaymentEligibility';

COMMENT ON COLUMN "CheckinStep"."is_required" IS 'bắt buộc theo cấu hình chuyến';

COMMENT ON COLUMN "CheckinStep"."status" IS 'Pending, Accepted hoặc NeedsReview';

COMMENT ON COLUMN "CheckinStep"."checked_by_user_id" IS 'FK User xác nhận nếu cần nhân viên';

COMMENT ON COLUMN "CheckinStep"."checked_at" IS 'thời điểm kiểm tra';

COMMENT ON COLUMN "CheckinStep"."review_note" IS 'lý do cần bổ sung';

COMMENT ON COLUMN "PassengerIdentifier"."passenger_identifier_id" IS 'khóa chính QR/thẻ định danh';

COMMENT ON COLUMN "PassengerIdentifier"."cruise_checkin_id" IS 'FK CruiseCheckin; nguồn duy nhất nối credential với người đi/chuyến';

COMMENT ON COLUMN "PassengerIdentifier"."identifier_type" IS 'QR, CruiseCard hoặc WearableDemo';

COMMENT ON COLUMN "PassengerIdentifier"."secure_token_reference" IS 'token duy nhất, không chứa toàn bộ PII hoặc thẻ thanh toán';

COMMENT ON COLUMN "PassengerIdentifier"."issued_at" IS 'thời điểm cấp';

COMMENT ON COLUMN "PassengerIdentifier"."valid_to" IS 'hạn dùng theo voyage';

COMMENT ON COLUMN "PassengerIdentifier"."status" IS 'Active, Revoked hoặc Expired';

COMMENT ON COLUMN "PassengerIdentifier"."previous_identifier_id" IS 'FK PassengerIdentifier bị thay thế nếu mất/hỏng';

COMMENT ON COLUMN "PassengerIdentifier"."revoked_at" IS 'thời điểm vô hiệu hóa';

COMMENT ON COLUMN "PassengerIdentifier"."last_known_area" IS 'vị trí gần nhất nếu demo BLE/3D cần hiển thị; cache, không phải lịch sử hay nguồn presence';

COMMENT ON COLUMN "PassengerIdentifier"."location_updated_at" IS 'thời điểm quan sát vị trí gần nhất; có thể NULL';

COMMENT ON COLUMN "GangwayEvent"."gangway_event_id" IS 'khóa chính một lần scan lên/xuống tàu';

COMMENT ON COLUMN "GangwayEvent"."passenger_identifier_id" IS 'FK PassengerIdentifier đang được dùng';

COMMENT ON COLUMN "GangwayEvent"."cruise_stop_id" IS 'FK CruiseStop; có thể NULL tại boarding/final disembark ở cảng chính';

COMMENT ON COLUMN "GangwayEvent"."event_type" IS 'Embark, ShoreExit, ShoreReturn hoặc FinalDisembark';

COMMENT ON COLUMN "GangwayEvent"."occurred_at" IS 'thời điểm sự kiện thực tế';

COMMENT ON COLUMN "GangwayEvent"."result" IS 'Accepted hoặc Rejected';

COMMENT ON COLUMN "GangwayEvent"."rejection_reason" IS 'lý do từ chối nếu có';

COMMENT ON COLUMN "GangwayEvent"."idempotency_key" IS 'khóa duy nhất chống scan/đồng bộ ghi trùng';

COMMENT ON COLUMN "GuidanceSession"."guidance_session_id" IS 'khóa chính buổi hướng dẫn đã có trong phạm vi';

COMMENT ON COLUMN "GuidanceSession"."cruise_id" IS 'FK Cruise';

COMMENT ON COLUMN "GuidanceSession"."session_name" IS 'tên buổi';

COMMENT ON COLUMN "GuidanceSession"."location" IS 'địa điểm';

COMMENT ON COLUMN "GuidanceSession"."start_at" IS 'giờ bắt đầu';

COMMENT ON COLUMN "GuidanceSession"."end_at" IS 'giờ kết thúc';

COMMENT ON COLUMN "GuidanceSession"."instructor_user_id" IS 'FK User hướng dẫn';

COMMENT ON COLUMN "GuidanceSession"."status" IS 'Scheduled, Completed hoặc Cancelled';

COMMENT ON COLUMN "GuidanceSessionParticipant"."guidance_session_participant_id" IS 'khóa chính quan hệ buổi hướng dẫn–người đi';

COMMENT ON COLUMN "GuidanceSessionParticipant"."guidance_session_id" IS 'FK GuidanceSession';

COMMENT ON COLUMN "GuidanceSessionParticipant"."booking_passenger_id" IS 'FK BookingPassenger cùng cruise';

COMMENT ON COLUMN "GuidanceSessionParticipant"."attendance_status" IS 'Registered, Attended hoặc Absent';

COMMENT ON COLUMN "GuidanceSessionParticipant"."attended_at" IS 'thời điểm tham dự nếu có';

COMMENT ON COLUMN "CruiseCheckout"."cruise_checkout_id" IS 'khóa chính check-out';

COMMENT ON COLUMN "CruiseCheckout"."booking_passenger_id" IS 'FK BookingPassenger; UNIQUE';

COMMENT ON COLUMN "CruiseCheckout"."final_settlement_id" IS 'FK FinalSettlement; chỉ tham chiếu, không sao chép số tiền/financial clearance';

COMMENT ON COLUMN "CruiseCheckout"."checkout_status" IS 'NotStarted, InProgress, Blocked hoặc Completed';

COMMENT ON COLUMN "CruiseCheckout"."requested_at" IS 'bắt đầu check-out';

COMMENT ON COLUMN "CruiseCheckout"."cabin_released_at" IS 'thời điểm trả phòng, độc lập với thanh toán';

COMMENT ON COLUMN "CruiseCheckout"."exit_verified_by_user_id" IS 'FK User cho phép rời tàu theo quy trình demo';

COMMENT ON COLUMN "CruiseCheckout"."completed_at" IS 'hoàn tất sau scan FinalDisembark Accepted';

COMMENT ON COLUMN "CruiseCheckout"."block_reason" IS 'lý do chưa đạt';

COMMENT ON COLUMN "TourPackage"."tour_package_id" IS 'Mã package.';

COMMENT ON COLUMN "TourPackage"."package_code" IS 'Mã nghiệp vụ của package.';

COMMENT ON COLUMN "TourPackage"."package_name" IS 'Tên package.';

COMMENT ON COLUMN "TourPackage"."description" IS 'Mô tả package.';

COMMENT ON COLUMN "TourPackage"."package_type" IS 'Phân loại package.';

COMMENT ON COLUMN "TourPackage"."price" IS 'Giá package.';

COMMENT ON COLUMN "TourPackage"."currency_code" IS 'Đơn vị tiền tệ.';

COMMENT ON COLUMN "TourPackage"."status" IS 'Trạng thái package.';

COMMENT ON COLUMN "TourPackage"."start_date" IS 'Ngày bắt đầu hiệu lực.';

COMMENT ON COLUMN "TourPackage"."end_date" IS 'Ngày kết thúc hiệu lực.';

COMMENT ON COLUMN "TourPackage"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "TourPackage"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON TABLE "BookingPassengerPackage" IS 'ADDED: MVP chooses one package per booking passenger. Upgrades are an update subject to business rules; package versioning is not added.';

COMMENT ON COLUMN "BookingPassengerPackage"."agreed_price" IS 'Price snapshot; clarify whether included in agreed_fare_amount to prevent duplicate charges.';

COMMENT ON TABLE "TourPackageService" IS 'ADDED: explicit package contents. No discount/usage quota engine is implied.';

COMMENT ON COLUMN "TourPackageService"."benefit_type" IS 'INCLUDED or OPTIONAL; OPTIONAL is charged at normal catalogue price.';

COMMENT ON TABLE "TourPackageActivity" IS 'ADDED: explicit package contents. No discount/usage quota engine is implied.';

COMMENT ON COLUMN "TourPackageActivity"."benefit_type" IS 'INCLUDED or OPTIONAL; OPTIONAL is charged at normal catalogue price.';

COMMENT ON TABLE "TourPackageExcursion" IS 'ADDED: explicit package contents. No discount/usage quota engine is implied.';

COMMENT ON COLUMN "TourPackageExcursion"."benefit_type" IS 'INCLUDED or OPTIONAL; OPTIONAL is charged at normal catalogue price.';

COMMENT ON COLUMN "Service"."service_id" IS 'Mã dịch vụ.';

COMMENT ON COLUMN "Service"."service_code" IS 'Mã định danh nghiệp vụ của dịch vụ.';

COMMENT ON COLUMN "Service"."service_name" IS 'Tên dịch vụ.';

COMMENT ON COLUMN "Service"."description" IS 'Mô tả dịch vụ.';

COMMENT ON COLUMN "Service"."service_type" IS 'Loại dịch vụ.';

COMMENT ON COLUMN "Service"."price" IS 'Giá dịch vụ nếu có tính phí.';

COMMENT ON COLUMN "Service"."currency_code" IS 'Đơn vị tiền tệ của Price.';

COMMENT ON COLUMN "Service"."capacity" IS 'Số lượng tối đa có thể phục vụ tại cùng thời điểm nếu dịch vụ có giới hạn.';

COMMENT ON COLUMN "Service"."status" IS 'Trạng thái cung cấp dịch vụ.';

COMMENT ON COLUMN "Service"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Service"."updated_at" IS 'Thời điểm cập nhật gần nhất.';

COMMENT ON COLUMN "Product"."product_id" IS 'Mã sản phẩm.';

COMMENT ON COLUMN "Product"."product_code" IS 'Mã sản phẩm.';

COMMENT ON COLUMN "Product"."product_name" IS 'Tên sản phẩm.';

COMMENT ON COLUMN "Product"."description" IS 'Mô tả sản phẩm.';

COMMENT ON COLUMN "Product"."product_type" IS 'Loại sản phẩm.';

COMMENT ON COLUMN "Product"."unit_price" IS 'Giá trên một đơn vị.';

COMMENT ON COLUMN "Product"."currency_code" IS 'Đơn vị tiền tệ.';

COMMENT ON COLUMN "Product"."status" IS 'Trạng thái sản phẩm.';

COMMENT ON COLUMN "Product"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Product"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON TABLE "ServiceRequest" IS 'MVP voyage requests use booking_passenger_id. PassengerId and BookingId are retained from source, validated against BookingPassenger. Context consistency enforced in service.';

COMMENT ON COLUMN "ServiceRequest"."service_request_id" IS 'Mã yêu cầu dịch vụ.';

COMMENT ON COLUMN "ServiceRequest"."passenger_id" IS 'Hành khách tạo yêu cầu.';

COMMENT ON COLUMN "ServiceRequest"."service_id" IS 'Dịch vụ được yêu cầu.';

COMMENT ON COLUMN "ServiceRequest"."booking_id" IS 'Context chosen as Booking; Cruise is derived.';

COMMENT ON COLUMN "ServiceRequest"."request_time" IS 'Thời điểm gửi yêu cầu.';

COMMENT ON COLUMN "ServiceRequest"."quantity" IS 'Số lượng yêu cầu.';

COMMENT ON COLUMN "ServiceRequest"."status" IS 'Trạng thái xử lý.';

COMMENT ON COLUMN "ServiceRequest"."assigned_user_id" IS 'Nhân viên được phân công.';

COMMENT ON COLUMN "ServiceRequest"."note" IS 'Ghi chú của yêu cầu.';

COMMENT ON COLUMN "ServiceRequest"."completed_at" IS 'Thời điểm hoàn thành.';

COMMENT ON COLUMN "ServiceRequest"."created_at" IS 'Thời điểm tạo record.';

COMMENT ON COLUMN "ServiceRequest"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON COLUMN "ServiceRequest"."booking_passenger_id" IS 'ADDED: exact person and voyage context; must match passenger_id and booking_id.';

COMMENT ON TABLE "Feedback" IS 'Service feedback uses service_id; activity/excursion feedback uses registration_id; general voyage feedback uses booking_passenger_id. Passenger consistency and completed usage checked in service.';

COMMENT ON COLUMN "Feedback"."feedback_id" IS 'Mã feedback.';

COMMENT ON COLUMN "Feedback"."passenger_id" IS 'Hành khách gửi feedback.';

COMMENT ON COLUMN "Feedback"."service_id" IS 'Service được đánh giá.';

COMMENT ON COLUMN "Feedback"."rating" IS 'Điểm đánh giá.';

COMMENT ON COLUMN "Feedback"."comment" IS 'Nội dung phản hồi.';

COMMENT ON COLUMN "Feedback"."submitted_at" IS 'Thời điểm gửi.';

COMMENT ON COLUMN "Feedback"."status" IS 'Trạng thái feedback.';

COMMENT ON COLUMN "Feedback"."registration_id" IS 'ADDED: feedback for an onboard activity or shore excursion.';

COMMENT ON COLUMN "Feedback"."booking_passenger_id" IS 'ADDED: voyage context for general cruise feedback; must match passenger_id.';

COMMENT ON COLUMN "OnboardActivity"."onboard_activity_id" IS 'Mã hoạt động.';

COMMENT ON COLUMN "OnboardActivity"."activity_code" IS 'Mã hoạt động.';

COMMENT ON COLUMN "OnboardActivity"."activity_name" IS 'Tên hoạt động.';

COMMENT ON COLUMN "OnboardActivity"."description" IS 'Mô tả.';

COMMENT ON COLUMN "OnboardActivity"."start_time" IS 'Thời gian bắt đầu.';

COMMENT ON COLUMN "OnboardActivity"."end_time" IS 'Thời gian kết thúc.';

COMMENT ON COLUMN "OnboardActivity"."location" IS 'Địa điểm trên tàu.';

COMMENT ON COLUMN "OnboardActivity"."capacity" IS 'Sức chứa tối đa.';

COMMENT ON COLUMN "OnboardActivity"."price" IS 'Giá nếu hoạt động không nằm trong quyền lợi package hoặc được bán riêng.';

COMMENT ON COLUMN "OnboardActivity"."currency_code" IS 'Đơn vị tiền tệ.';

COMMENT ON COLUMN "OnboardActivity"."status" IS 'Trạng thái hoạt động.';

COMMENT ON COLUMN "OnboardActivity"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "OnboardActivity"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON COLUMN "OnboardActivity"."cruise_id" IS 'ADDED: activity belongs to one cruise.';

COMMENT ON COLUMN "ShoreExcursion"."shore_excursion_id" IS 'Mã excursion.';

COMMENT ON COLUMN "ShoreExcursion"."cruise_stop_id" IS 'Lượt ghé cảng mà excursion thuộc về.';

COMMENT ON COLUMN "ShoreExcursion"."excursion_code" IS 'Mã nghiệp vụ của excursion.';

COMMENT ON COLUMN "ShoreExcursion"."excursion_name" IS 'Tên excursion.';

COMMENT ON COLUMN "ShoreExcursion"."supplier_id" IS 'Nhà cung cấp.';

COMMENT ON COLUMN "ShoreExcursion"."start_time" IS 'Thời điểm khởi hành dự kiến.';

COMMENT ON COLUMN "ShoreExcursion"."planned_return_time" IS 'Thời điểm dự kiến quay lại.';

COMMENT ON COLUMN "ShoreExcursion"."meeting_point" IS 'Điểm tập trung.';

COMMENT ON COLUMN "ShoreExcursion"."minimum_participants" IS 'Số khách tối thiểu nếu áp dụng.';

COMMENT ON COLUMN "ShoreExcursion"."maximum_capacity" IS 'Sức chứa tối đa.';

COMMENT ON COLUMN "ShoreExcursion"."price" IS 'Giá nếu excursion trả phí.';

COMMENT ON COLUMN "ShoreExcursion"."currency_code" IS 'Đồng tiền của Price.';

COMMENT ON COLUMN "ShoreExcursion"."status" IS 'Trạng thái excursion.';

COMMENT ON COLUMN "ShoreExcursion"."cancellation_reason" IS 'Lý do hủy/thay đổi.';

COMMENT ON COLUMN "ShoreExcursion"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "ShoreExcursion"."updated_at" IS 'Cập nhật gần nhất.';

COMMENT ON COLUMN "Supplier"."supplier_id" IS 'Mã nhà cung cấp.';

COMMENT ON COLUMN "Supplier"."supplier_name" IS 'Tên nhà cung cấp.';

COMMENT ON COLUMN "Supplier"."supplier_type" IS 'Loại nhà cung cấp nếu nhóm cần phân loại.';

COMMENT ON COLUMN "Supplier"."contact_name" IS 'Đầu mối vận hành.';

COMMENT ON COLUMN "Supplier"."contact_phone" IS 'Số liên hệ.';

COMMENT ON COLUMN "Supplier"."contact_email" IS 'Email liên hệ.';

COMMENT ON COLUMN "Supplier"."status" IS 'ACTIVE/INACTIVE.';

COMMENT ON COLUMN "Supplier"."notes" IS 'Ghi chú điều kiện/phối hợp.';

COMMENT ON COLUMN "Supplier"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Supplier"."updated_at" IS 'Cập nhật gần nhất.';

COMMENT ON COLUMN "Registration"."registration_id" IS 'Mã đăng ký.';

COMMENT ON COLUMN "Registration"."passenger_id" IS 'Hành khách đăng ký.';

COMMENT ON COLUMN "Registration"."onboard_activity_id" IS 'Hoạt động được đăng ký.';

COMMENT ON COLUMN "Registration"."registration_time" IS 'Thời điểm đăng ký.';

COMMENT ON COLUMN "Registration"."quantity" IS 'Số lượng suất đăng ký.';

COMMENT ON COLUMN "Registration"."status" IS 'Trạng thái đăng ký.';

COMMENT ON COLUMN "Registration"."cancellation_reason" IS 'Lý do hủy.';

COMMENT ON COLUMN "Registration"."notes" IS 'Ghi chú.';

COMMENT ON COLUMN "Registration"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Registration"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON COLUMN "Registration"."booking_passenger_id" IS 'ADDED: person registered on this voyage; must match passenger_id.';

COMMENT ON COLUMN "Registration"."shore_excursion_id" IS 'ADDED: exactly one of onboard_activity_id and shore_excursion_id is present.';

COMMENT ON TABLE "Participation" IS 'One attendance record per Registration (MVP choice). Passenger and target must match Registration; redundant source fields retained for compatibility. Group quantities do not identify every attendee; use one Registration per named participant when individual attendance is needed.';

COMMENT ON COLUMN "Participation"."participation_id" IS 'Mã lượt tham gia.';

COMMENT ON COLUMN "Participation"."registration_id" IS 'Registration tương ứng.';

COMMENT ON COLUMN "Participation"."passenger_id" IS 'Hành khách tham gia.';

COMMENT ON COLUMN "Participation"."onboard_activity_id" IS 'Activity được tham gia.';

COMMENT ON COLUMN "Participation"."check_in_time" IS 'Thời điểm ghi nhận tham gia.';

COMMENT ON COLUMN "Participation"."check_out_time" IS 'Thời điểm kết thúc/rời hoạt động.';

COMMENT ON COLUMN "Participation"."status" IS 'Trạng thái tham gia.';

COMMENT ON COLUMN "Participation"."notes" IS 'Ghi chú.';

COMMENT ON COLUMN "Participation"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "Participation"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON COLUMN "Participation"."shore_excursion_id" IS 'ADDED: must match the target of Registration.';

COMMENT ON TABLE "TourGroup" IS 'OPTIONAL SOURCE TABLE: retain only if this functionality is in project scope.';

COMMENT ON COLUMN "TourGroup"."tour_group_id" IS 'Mã nhóm tour.';

COMMENT ON COLUMN "TourGroup"."shore_excursion_id" IS 'Excursion chứa nhóm.';

COMMENT ON COLUMN "TourGroup"."group_code" IS 'Mã nhóm.';

COMMENT ON COLUMN "TourGroup"."maximum_capacity" IS 'Sức chứa nhóm.';

COMMENT ON COLUMN "TourGroup"."guide_id" IS 'UNRESOLVED EXTERNAL REFERENCE: guide_id has no entity definition in the six source documents. No enforceable FK is declared.';

COMMENT ON COLUMN "TourGroup"."vehicle_id" IS 'UNRESOLVED EXTERNAL REFERENCE: vehicle_id has no entity definition in the six source documents. No enforceable FK is declared.';

COMMENT ON COLUMN "TourGroup"."meeting_point" IS 'Điểm tập trung riêng của nhóm.';

COMMENT ON COLUMN "TourGroup"."assembly_time" IS 'Giờ tập trung nhóm.';

COMMENT ON COLUMN "TourGroup"."planned_departure_time" IS 'Giờ nhóm xuất phát.';

COMMENT ON COLUMN "TourGroup"."planned_return_time" IS 'Giờ nhóm dự kiến quay lại.';

COMMENT ON COLUMN "TourGroup"."actual_departure_time" IS 'Giờ xuất phát thực tế.';

COMMENT ON COLUMN "TourGroup"."actual_return_time" IS 'Giờ quay lại thực tế.';

COMMENT ON COLUMN "TourGroup"."status" IS 'Trạng thái nhóm.';

COMMENT ON COLUMN "TourGroup"."notes" IS 'Ghi chú.';

COMMENT ON TABLE "TourGroupPassenger" IS 'OPTIONAL SOURCE TABLE: retain only if this functionality is in project scope.';

COMMENT ON COLUMN "TourGroupPassenger"."tour_group_passenger_id" IS 'Mã phân bổ.';

COMMENT ON COLUMN "TourGroupPassenger"."tour_group_id" IS 'Nhóm tour.';

COMMENT ON COLUMN "TourGroupPassenger"."passenger_id" IS 'Hành khách được phân bổ.';

COMMENT ON COLUMN "TourGroupPassenger"."assignment_status" IS 'Trạng thái phân bổ.';

COMMENT ON COLUMN "TourGroupPassenger"."assigned_at" IS 'Thời điểm phân nhóm.';

COMMENT ON COLUMN "TourGroupPassenger"."checked_in_at" IS 'Thời điểm khách có mặt tại nhóm nếu theo dõi.';

COMMENT ON COLUMN "TourGroupPassenger"."returned_at" IS 'Thời điểm xác nhận khách quay lại nếu theo dõi.';

COMMENT ON COLUMN "TourGroupPassenger"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "OnboardAccount"."onboard_account_id" IS 'Định danh duy nhất của tài khoản.';

COMMENT ON COLUMN "OnboardAccount"."cruise_id" IS 'Chuyến mà tài khoản có hiệu lực.';

COMMENT ON COLUMN "OnboardAccount"."status" IS 'Trạng thái vòng đời tài khoản.';

COMMENT ON COLUMN "OnboardAccount"."credit_limit" IS 'Hạn mức tín dụng toàn tài khoản nếu áp dụng.';

COMMENT ON COLUMN "OnboardAccount"."activated_at" IS 'Thời điểm kích hoạt.';

COMMENT ON COLUMN "OnboardAccount"."closed_at" IS 'Thời điểm tài khoản đóng.';

COMMENT ON COLUMN "OnboardAccount"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "OnboardAccount"."updated_at" IS 'Thời điểm cập nhật gần nhất.';

COMMENT ON COLUMN "OnboardAccountMember"."account_member_id" IS 'Định danh quan hệ thành viên.';

COMMENT ON COLUMN "OnboardAccountMember"."onboard_account_id" IS 'Tài khoản mà thành viên được gắn vào.';

COMMENT ON COLUMN "OnboardAccountMember"."passenger_id" IS 'Hành khách được phép sử dụng tài khoản.';

COMMENT ON COLUMN "OnboardAccountMember"."member_role" IS 'Vai trò OWNER hoặc AUTHORIZED.';

COMMENT ON COLUMN "OnboardAccountMember"."charge_permission" IS 'ALLOWED hoặc NOT_ALLOWED.';

COMMENT ON COLUMN "OnboardAccountMember"."status" IS 'ACTIVE hoặc REVOKED.';

COMMENT ON COLUMN "OnboardAccountMember"."effective_from" IS 'Thời điểm quyền bắt đầu.';

COMMENT ON COLUMN "OnboardAccountMember"."effective_to" IS 'Thời điểm quyền kết thúc.';

COMMENT ON COLUMN "OnboardAccountMember"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "OnboardAccountMember"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON TABLE "SpendingLimit" IS 'OPTIONAL SOURCE TABLE: retain only if this functionality is in project scope.';

COMMENT ON COLUMN "SpendingLimit"."spending_limit_id" IS 'Mã hạn mức.';

COMMENT ON COLUMN "SpendingLimit"."account_member_id" IS 'Thành viên bị áp hạn mức.';

COMMENT ON COLUMN "SpendingLimit"."limit_amount" IS 'Mức chi tiêu tối đa.';

COMMENT ON COLUMN "SpendingLimit"."currency_code" IS 'Đồng tiền của hạn mức.';

COMMENT ON COLUMN "SpendingLimit"."effective_from" IS 'Thời điểm bắt đầu hiệu lực.';

COMMENT ON COLUMN "SpendingLimit"."effective_to" IS 'Thời điểm kết thúc hiệu lực.';

COMMENT ON COLUMN "SpendingLimit"."status" IS 'ACTIVE, EXPIRED, CANCELLED.';

COMMENT ON COLUMN "SpendingLimit"."set_by_user_id" IS 'Người thiết lập.';

COMMENT ON COLUMN "SpendingLimit"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "SpendingLimit"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON TABLE "OnboardTransaction" IS 'INTEGRATION DECISION: signed amount. CHARGE > 0, PAYMENT < 0, actual REFUND > 0, charge VOID < 0; ADJUSTMENT signed. Balance = SUM(amount) WHERE status=POSTED. SourceType + SourceReferenceId is polymorphic, not FK. PAYMENT/REFUND entry must resolve to one confirmed payment/completed refund and be posted once. Booking payments enter booking balance, never again reduce onboard charges.';

COMMENT ON COLUMN "OnboardTransaction"."transaction_id" IS 'Mã giao dịch.';

COMMENT ON COLUMN "OnboardTransaction"."onboard_account_id" IS 'Tài khoản chịu tác động.';

COMMENT ON COLUMN "OnboardTransaction"."passenger_id" IS 'Hành khách thực tế phát sinh.';

COMMENT ON COLUMN "OnboardTransaction"."transaction_type" IS 'Loại tác động được ghi trong ledger.';

COMMENT ON COLUMN "OnboardTransaction"."amount" IS 'Giá trị giao dịch.';

COMMENT ON COLUMN "OnboardTransaction"."currency_code" IS 'Đồng tiền.';

COMMENT ON COLUMN "OnboardTransaction"."transaction_time" IS 'Thời điểm nghiệp vụ phát sinh.';

COMMENT ON COLUMN "OnboardTransaction"."posted_at" IS 'Thời điểm được ghi chính thức.';

COMMENT ON COLUMN "OnboardTransaction"."status" IS 'PENDING, POSTED, FAILED.';

COMMENT ON COLUMN "OnboardTransaction"."source_type" IS 'Nguồn phát sinh.';

COMMENT ON COLUMN "OnboardTransaction"."source_reference_id" IS 'ID bản ghi nguồn.';

COMMENT ON COLUMN "OnboardTransaction"."original_transaction_id" IS 'Transaction gốc được void/refund/adjust.';

COMMENT ON COLUMN "OnboardTransaction"."created_by_user_id" IS 'Người ghi nhận.';

COMMENT ON COLUMN "OnboardTransaction"."sync_status" IS 'Trạng thái đồng bộ khi giao dịch được tạo trong chế độ Offline POS.';

COMMENT ON COLUMN "OnboardTransaction"."synced_at" IS 'Thời điểm đồng bộ thành công.';

COMMENT ON COLUMN "OnboardTransaction"."description" IS 'Mô tả ngắn.';

COMMENT ON TABLE "Charge" IS 'SourceType + SourceReferenceId is polymorphic; resolve in service, NOT a database FK. Original charge remains non-negative. Reversal is a signed ledger VOID/ADJUSTMENT entry; RefundLine.reversal_charge_id is optional and unused under this convention. net_amount = gross_amount - discount_amount + tax_amount.';

COMMENT ON COLUMN "Charge"."charge_id" IS 'Mã khoản phí.';

COMMENT ON COLUMN "Charge"."transaction_id" IS 'Transaction loại CHARGE tương ứng.';

COMMENT ON COLUMN "Charge"."passenger_id" IS 'Người thực tế sử dụng.';

COMMENT ON COLUMN "Charge"."source_type" IS 'SERVICE/PRODUCT/ACTIVITY.';

COMMENT ON COLUMN "Charge"."source_reference_id" IS 'ID của dữ liệu nguồn.';

COMMENT ON COLUMN "Charge"."quantity" IS 'Số lượng.';

COMMENT ON COLUMN "Charge"."unit_price" IS 'Đơn giá.';

COMMENT ON COLUMN "Charge"."gross_amount" IS 'Tổng trước giảm trừ.';

COMMENT ON COLUMN "Charge"."discount_amount" IS 'Giảm giá/quyền lợi.';

COMMENT ON COLUMN "Charge"."net_amount" IS 'Số thực tế ghi account.';

COMMENT ON COLUMN "Charge"."charged_at" IS 'Thời điểm phát sinh.';

COMMENT ON COLUMN "Charge"."created_by_user_id" IS 'Người ghi nhận.';

COMMENT ON COLUMN "Charge"."note" IS 'Ghi chú.';

COMMENT ON COLUMN "Charge"."tax_amount" IS 'ADDED: InvoiceLine requires a source tax amount; net_amount includes tax.';

COMMENT ON COLUMN "ChargeDispute"."charge_dispute_id" IS 'Mã hồ sơ tranh chấp.';

COMMENT ON COLUMN "ChargeDispute"."charge_id" IS 'Khoản phí bị tranh chấp.';

COMMENT ON COLUMN "ChargeDispute"."passenger_id" IS 'Hành khách gửi yêu cầu.';

COMMENT ON COLUMN "ChargeDispute"."disputed_amount" IS 'Số tiền đang tranh chấp.';

COMMENT ON COLUMN "ChargeDispute"."reason_code" IS 'Nhóm lý do.';

COMMENT ON COLUMN "ChargeDispute"."description" IS 'Nội dung phản ánh.';

COMMENT ON COLUMN "ChargeDispute"."status" IS 'OPEN, UNDER_REVIEW, RESOLVED, REJECTED, CANCELLED.';

COMMENT ON COLUMN "ChargeDispute"."reported_at" IS 'Thời điểm tiếp nhận.';

COMMENT ON COLUMN "ChargeDispute"."assigned_to_user_id" IS 'Người phụ trách.';

COMMENT ON COLUMN "ChargeDispute"."resolution_type" IS 'Kết luận/hành động được xác định sau khi xử lý dispute.';

COMMENT ON COLUMN "ChargeDispute"."resolution_note" IS 'Giải thích kết quả.';

COMMENT ON COLUMN "ChargeDispute"."approved_amount" IS 'Số tiền được chấp nhận điều chỉnh.';

COMMENT ON COLUMN "ChargeDispute"."resolution_transaction_id" IS 'Ledger transaction dùng để đảo/điều chỉnh Charge nếu có.';

COMMENT ON COLUMN "ChargeDispute"."resolved_by_user_id" IS 'Người chốt xử lý.';

COMMENT ON COLUMN "ChargeDispute"."resolved_at" IS 'Thời điểm hoàn tất.';

COMMENT ON COLUMN "ChargeDispute"."created_at" IS 'Thời điểm tạo.';

COMMENT ON COLUMN "ChargeDispute"."updated_at" IS 'Thời điểm cập nhật.';

COMMENT ON COLUMN "Invoice"."invoice_id" IS 'khóa chính chứng từ/snapshot tài chính';

COMMENT ON COLUMN "Invoice"."booking_id" IS 'FK Booking nếu scope Booking';

COMMENT ON COLUMN "Invoice"."onboard_account_id" IS 'FK OnboardAccount nếu scope Onboard; đúng một FK context khác NULL';

COMMENT ON COLUMN "Invoice"."invoice_reference" IS 'mã chứng từ duy nhất';

COMMENT ON COLUMN "Invoice"."document_type" IS 'BookingInvoice, PreliminaryFolio, FinalStatement hoặc AdjustmentStatement';

COMMENT ON COLUMN "Invoice"."version_no" IS 'phiên bản chứng từ; không ghi đè bản đã phát hành';

COMMENT ON COLUMN "Invoice"."issued_at" IS 'thời điểm phát hành';

COMMENT ON COLUMN "Invoice"."source_cutoff_at" IS 'mốc chốt dữ liệu nguồn';

COMMENT ON COLUMN "Invoice"."currency" IS 'tiền tệ thống nhất của scope';

COMMENT ON COLUMN "Invoice"."snapshot_total_amount" IS 'tổng từ dòng snapshot, không tự tạo charge';

COMMENT ON COLUMN "Invoice"."snapshot_net_amount_due" IS 'số dư chụp từ nguồn tại cutoff; không phải balance nhập tay';

COMMENT ON COLUMN "Invoice"."status" IS 'Draft, Issued, Superseded hoặc Voided';

COMMENT ON COLUMN "Invoice"."document_reference" IS 'tham chiếu file chứng từ';

COMMENT ON TABLE "InvoiceLine" IS 'source_charge_id is optional for a booking-fare line. Exactly one of source_charge_id and booking_fare_amount_snapshot. Other amounts are immutable snapshots; no second fee is created.';

COMMENT ON COLUMN "InvoiceLine"."invoice_line_id" IS 'khóa chính dòng chứng từ';

COMMENT ON COLUMN "InvoiceLine"."invoice_id" IS 'FK Invoice';

COMMENT ON COLUMN "InvoiceLine"."source_charge_id" IS 'FK Charge nguồn đã ghi nhận trong nhóm Onboard';

COMMENT ON COLUMN "InvoiceLine"."line_no" IS 'thứ tự; UNIQUE trong invoice';

COMMENT ON COLUMN "InvoiceLine"."description_snapshot" IS 'tên/mô tả tại thời điểm chốt';

COMMENT ON COLUMN "InvoiceLine"."quantity_snapshot" IS 'số lượng chụp từ nguồn';

COMMENT ON COLUMN "InvoiceLine"."base_amount_snapshot" IS 'giá sau discount/entitlement, chưa cộng thuế của dòng';

COMMENT ON COLUMN "InvoiceLine"."tax_amount_snapshot" IS 'thuế đã tính ở Charge; MVP dùng cấu hình thuế đơn giản';

COMMENT ON COLUMN "InvoiceLine"."total_amount_snapshot" IS 'tổng chụp từ Charge, không nhập độc lập';

COMMENT ON COLUMN "InvoiceLine"."booking_fare_amount_snapshot" IS 'ADDED: booking fare snapshot when source_charge_id is NULL; prevents inventing an onboard charge.';

COMMENT ON COLUMN "Installment"."installment_id" IS 'khóa chính một đợt thanh toán booking';

COMMENT ON COLUMN "Installment"."booking_id" IS 'FK Booking';

COMMENT ON COLUMN "Installment"."sequence_no" IS 'thứ tự; UNIQUE trong booking';

COMMENT ON COLUMN "Installment"."installment_type" IS 'Deposit hoặc FinalFare; không có financing bên thứ ba';

COMMENT ON COLUMN "Installment"."required_amount" IS 'số phải trả của đợt';

COMMENT ON COLUMN "Installment"."currency" IS 'tiền tệ thống nhất';

COMMENT ON COLUMN "Installment"."due_at" IS 'hạn thanh toán';

COMMENT ON COLUMN "Installment"."status" IS 'Scheduled hoặc Cancelled; trạng thái Paid/Overdue tính từ Payment';

COMMENT ON COLUMN "Payment"."payment_id" IS 'khóa chính một thao tác/lần thử thu tiền';

COMMENT ON COLUMN "Payment"."booking_id" IS 'FK Booking nếu context booking';

COMMENT ON COLUMN "Payment"."onboard_account_id" IS 'FK OnboardAccount nếu context onboard; đúng một context khác NULL';

COMMENT ON COLUMN "Payment"."installment_id" IS 'FK Installment nếu trả một đợt; NULL ở thanh toán account';

COMMENT ON COLUMN "Payment"."final_settlement_id" IS 'FK FinalSettlement nếu trả số dư cuối chuyến';

COMMENT ON COLUMN "Payment"."operation_type" IS 'Collect hoặc Void; Void chỉ dùng cho lần thu chưa hoàn tất, tiền đã thu dùng Refund';

COMMENT ON COLUMN "Payment"."retry_of_payment_id" IS 'FK Payment lần thử thất bại trước; không ghi đè bản cũ';

COMMENT ON COLUMN "Payment"."parent_operation_id" IS 'FK Payment gốc nếu Void; NULL với Collect, tách ý nghĩa với retry';

COMMENT ON COLUMN "Payment"."payment_method" IS 'Cash hoặc GatewaySandbox theo phương thức nhóm triển khai';

COMMENT ON COLUMN "Payment"."requested_amount" IS 'số đề nghị thu';

COMMENT ON COLUMN "Payment"."confirmed_amount" IS 'tiền thực thu chỉ khi Collect Confirmed; không có authorization hold trong MVP';

COMMENT ON COLUMN "Payment"."currency" IS 'tiền tệ thống nhất';

COMMENT ON COLUMN "Payment"."requested_at" IS 'thời điểm tạo thao tác';

COMMENT ON COLUMN "Payment"."confirmed_at" IS 'thời điểm callback sandbox/cash đã được xác nhận';

COMMENT ON COLUMN "Payment"."processor_reference" IS 'reference sandbox nếu dùng gateway';

COMMENT ON COLUMN "Payment"."internal_receipt_reference" IS 'reference nội bộ cho cash; phải có reference phù hợp khi Confirmed';

COMMENT ON COLUMN "Payment"."status" IS 'Pending, Confirmed, Failed hoặc Cancelled';

COMMENT ON COLUMN "Payment"."failure_reason" IS 'lý do thất bại';

COMMENT ON COLUMN "Payment"."idempotency_key" IS 'khóa duy nhất chống submit/callback trùng';

COMMENT ON COLUMN "Refund"."refund_id" IS 'khóa chính yêu cầu hoàn tiền';

COMMENT ON COLUMN "Refund"."original_payment_id" IS 'FK Payment Collect Confirmed gốc';

COMMENT ON COLUMN "Refund"."final_settlement_id" IS 'FK FinalSettlement nếu trả lại tiền dư cuối chuyến';

COMMENT ON COLUMN "Refund"."refund_reason" IS 'lý do hoàn';

COMMENT ON COLUMN "Refund"."requested_amount" IS 'số đề nghị hoàn';

COMMENT ON COLUMN "Refund"."approved_amount" IS 'số được duyệt, bằng tổng RefundLine của yêu cầu';

COMMENT ON COLUMN "Refund"."currency" IS 'tiền tệ khớp giao dịch gốc';

COMMENT ON COLUMN "Refund"."requested_at" IS 'thời điểm yêu cầu';

COMMENT ON COLUMN "Refund"."approved_by_user_id" IS 'FK User duyệt';

COMMENT ON COLUMN "Refund"."approved_at" IS 'thời điểm duyệt';

COMMENT ON COLUMN "Refund"."completed_at" IS 'thời điểm xác nhận tiền hoàn theo sandbox/cash';

COMMENT ON COLUMN "Refund"."processor_or_receipt_reference" IS 'reference xác nhận refund';

COMMENT ON COLUMN "Refund"."status" IS 'Requested, Approved, Completed, Failed hoặc Rejected';

COMMENT ON COLUMN "Refund"."idempotency_key" IS 'khóa duy nhất chống hoàn trùng';

COMMENT ON COLUMN "RefundLine"."refund_line_id" IS 'khóa chính phần hoàn của một dòng hóa đơn';

COMMENT ON COLUMN "RefundLine"."refund_id" IS 'FK Refund';

COMMENT ON COLUMN "RefundLine"."invoice_line_id" IS 'FK InvoiceLine đã chụp charge gốc';

COMMENT ON COLUMN "RefundLine"."base_refund_amount" IS 'giá cơ sở được hoàn sau chính sách đơn giản của nhóm';

COMMENT ON COLUMN "RefundLine"."tax_refund_amount" IS 'phần thuế tương ứng được hoàn; không chỉ hoàn base';

COMMENT ON COLUMN "RefundLine"."reversal_charge_id" IS 'FK Charge credit/reversal đã post nếu dùng folio; giữ nguyên Charge gốc';

COMMENT ON TABLE "FinalSettlement" IS 'Final account settlement includes remaining fare from Booking.settlement_onboard_account_id plus onboard ledger balance. Service allocates each remaining booking fare once and attributes confirmed final payments without subtracting booking collections again from onboard charges. Snapshots are immutable.';

COMMENT ON COLUMN "FinalSettlement"."final_settlement_id" IS 'khóa chính một phiên chốt thanh toán';

COMMENT ON COLUMN "FinalSettlement"."onboard_account_id" IS 'FK OnboardAccount; nguồn context duy nhất';

COMMENT ON COLUMN "FinalSettlement"."final_invoice_id" IS 'FK Invoice FinalStatement của cùng account';

COMMENT ON COLUMN "FinalSettlement"."source_cutoff_at" IS 'mốc chốt dữ liệu';

COMMENT ON COLUMN "FinalSettlement"."amount_due_snapshot" IS 'số cần thu chụp từ ledger tại cutoff, không tính lại/nhập tay';

COMMENT ON COLUMN "FinalSettlement"."refund_due_snapshot" IS 'số cần hoàn chụp từ ledger khi có tiền dư';

COMMENT ON COLUMN "FinalSettlement"."statement_confirmed_at" IS 'thời điểm khách xác nhận hóa đơn';

COMMENT ON COLUMN "FinalSettlement"."started_at" IS 'bắt đầu xử lý';

COMMENT ON COLUMN "FinalSettlement"."completed_at" IS 'hoàn tất thu/hoàn theo quy trình';

COMMENT ON COLUMN "FinalSettlement"."status" IS 'Pending, PaymentRequired hoặc Settled';

COMMENT ON COLUMN "FinalSettlement"."handled_by_user_id" IS 'FK User xử lý nếu có';

ALTER TABLE "Cabin" ADD FOREIGN KEY ("deck_id", "ship_id") REFERENCES "Deck" ("deck_id", "ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Cabin" ADD FOREIGN KEY ("ship_id") REFERENCES "Ship" ("ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Itinerary" ADD FOREIGN KEY ("cruise_id") REFERENCES "Cruise" ("cruise_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Cruise" ADD FOREIGN KEY ("departure_port_id") REFERENCES "Port" ("port_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Cruise" ADD FOREIGN KEY ("return_port_id") REFERENCES "Port" ("port_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Cruise" ADD FOREIGN KEY ("ship_id") REFERENCES "Ship" ("ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseRegulation" ADD FOREIGN KEY ("cruise_id") REFERENCES "Cruise" ("cruise_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseRegulation" ADD FOREIGN KEY ("regulation_id") REFERENCES "OperationalRegulation" ("regulation_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseStop" ADD FOREIGN KEY ("arrival_itinerary_day_id", "itinerary_id") REFERENCES "ItineraryDay" ("itinerary_day_id", "itinerary_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseStop" ADD FOREIGN KEY ("departure_itinerary_day_id", "itinerary_id") REFERENCES "ItineraryDay" ("itinerary_day_id", "itinerary_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseStop" ADD FOREIGN KEY ("destination_id") REFERENCES "Destination" ("destination_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseStop" ADD FOREIGN KEY ("itinerary_id") REFERENCES "Itinerary" ("itinerary_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseStop" ADD FOREIGN KEY ("port_id") REFERENCES "Port" ("port_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseStop" ADD FOREIGN KEY ("substituted_by_stop_id") REFERENCES "CruiseStop" ("cruise_stop_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Deck" ADD FOREIGN KEY ("ship_id") REFERENCES "Ship" ("ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ItineraryChangeHistory" ADD FOREIGN KEY ("cruise_stop_id") REFERENCES "CruiseStop" ("cruise_stop_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ItineraryChangeHistory" ADD FOREIGN KEY ("itinerary_id") REFERENCES "Itinerary" ("itinerary_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ItineraryDay" ADD FOREIGN KEY ("itinerary_id") REFERENCES "Itinerary" ("itinerary_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OperationalIssue" ADD FOREIGN KEY ("cabin_id", "ship_id") REFERENCES "Cabin" ("cabin_id", "ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OperationalIssue" ADD FOREIGN KEY ("cruise_id", "ship_id") REFERENCES "Cruise" ("cruise_id", "ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OperationalIssue" ADD FOREIGN KEY ("ship_id") REFERENCES "Ship" ("ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ShipRegulation" ADD FOREIGN KEY ("regulation_id") REFERENCES "OperationalRegulation" ("regulation_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ShipRegulation" ADD FOREIGN KEY ("ship_id") REFERENCES "Ship" ("ship_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Booking" ADD FOREIGN KEY ("representative_booking_passenger_id", "booking_id") REFERENCES "BookingPassenger" ("booking_passenger_id", "booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BookingContract" ADD FOREIGN KEY ("accepted_by_booking_passenger_id", "booking_id") REFERENCES "BookingPassenger" ("booking_passenger_id", "booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BookingContract" ADD FOREIGN KEY ("booking_id") REFERENCES "Booking" ("booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BookingPassenger" ADD FOREIGN KEY ("booking_id") REFERENCES "Booking" ("booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BookingPassenger" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CabinAssignment" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Notification" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CheckinStep" ADD FOREIGN KEY ("cruise_checkin_id") REFERENCES "CruiseCheckin" ("cruise_checkin_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "GangwayEvent" ADD FOREIGN KEY ("passenger_identifier_id") REFERENCES "PassengerIdentifier" ("passenger_identifier_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "GuidanceSessionParticipant" ADD FOREIGN KEY ("guidance_session_id") REFERENCES "GuidanceSession" ("guidance_session_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "PassengerIdentifier" ADD FOREIGN KEY ("cruise_checkin_id") REFERENCES "CruiseCheckin" ("cruise_checkin_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "PassengerIdentifier" ADD FOREIGN KEY ("previous_identifier_id") REFERENCES "PassengerIdentifier" ("passenger_identifier_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BookingPassengerPackage" ADD FOREIGN KEY ("tour_package_id") REFERENCES "TourPackage" ("tour_package_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Feedback" ADD FOREIGN KEY ("service_id") REFERENCES "Service" ("service_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ServiceRequest" ADD FOREIGN KEY ("service_id") REFERENCES "Service" ("service_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourPackageActivity" ADD FOREIGN KEY ("tour_package_id") REFERENCES "TourPackage" ("tour_package_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourPackageExcursion" ADD FOREIGN KEY ("tour_package_id") REFERENCES "TourPackage" ("tour_package_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourPackageService" ADD FOREIGN KEY ("service_id") REFERENCES "Service" ("service_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourPackageService" ADD FOREIGN KEY ("tour_package_id") REFERENCES "TourPackage" ("tour_package_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Participation" ADD FOREIGN KEY ("onboard_activity_id") REFERENCES "OnboardActivity" ("onboard_activity_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Participation" ADD FOREIGN KEY ("shore_excursion_id") REFERENCES "ShoreExcursion" ("shore_excursion_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Registration" ADD FOREIGN KEY ("onboard_activity_id") REFERENCES "OnboardActivity" ("onboard_activity_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Participation" ADD FOREIGN KEY ("registration_id") REFERENCES "Registration" ("registration_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Registration" ADD FOREIGN KEY ("shore_excursion_id") REFERENCES "ShoreExcursion" ("shore_excursion_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ShoreExcursion" ADD FOREIGN KEY ("supplier_id") REFERENCES "Supplier" ("supplier_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourGroup" ADD FOREIGN KEY ("shore_excursion_id") REFERENCES "ShoreExcursion" ("shore_excursion_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourGroupPassenger" ADD FOREIGN KEY ("tour_group_id") REFERENCES "TourGroup" ("tour_group_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ChargeDispute" ADD FOREIGN KEY ("charge_id") REFERENCES "Charge" ("charge_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ChargeDispute" ADD FOREIGN KEY ("resolution_transaction_id") REFERENCES "OnboardTransaction" ("transaction_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "FinalSettlement" ADD FOREIGN KEY ("final_invoice_id") REFERENCES "Invoice" ("invoice_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "FinalSettlement" ADD FOREIGN KEY ("onboard_account_id") REFERENCES "OnboardAccount" ("onboard_account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Invoice" ADD FOREIGN KEY ("onboard_account_id") REFERENCES "OnboardAccount" ("onboard_account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "InvoiceLine" ADD FOREIGN KEY ("invoice_id") REFERENCES "Invoice" ("invoice_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "InvoiceLine" ADD FOREIGN KEY ("source_charge_id") REFERENCES "Charge" ("charge_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardAccountMember" ADD FOREIGN KEY ("onboard_account_id") REFERENCES "OnboardAccount" ("onboard_account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardTransaction" ADD FOREIGN KEY ("onboard_account_id") REFERENCES "OnboardAccount" ("onboard_account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardTransaction" ADD FOREIGN KEY ("original_transaction_id") REFERENCES "OnboardTransaction" ("transaction_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Charge" ADD FOREIGN KEY ("transaction_id") REFERENCES "OnboardTransaction" ("transaction_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Payment" ADD FOREIGN KEY ("final_settlement_id") REFERENCES "FinalSettlement" ("final_settlement_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Payment" ADD FOREIGN KEY ("installment_id") REFERENCES "Installment" ("installment_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Payment" ADD FOREIGN KEY ("onboard_account_id") REFERENCES "OnboardAccount" ("onboard_account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Payment" ADD FOREIGN KEY ("parent_operation_id") REFERENCES "Payment" ("payment_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Payment" ADD FOREIGN KEY ("retry_of_payment_id") REFERENCES "Payment" ("payment_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Refund" ADD FOREIGN KEY ("final_settlement_id") REFERENCES "FinalSettlement" ("final_settlement_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Refund" ADD FOREIGN KEY ("original_payment_id") REFERENCES "Payment" ("payment_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "RefundLine" ADD FOREIGN KEY ("invoice_line_id") REFERENCES "InvoiceLine" ("invoice_line_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "RefundLine" ADD FOREIGN KEY ("reversal_charge_id") REFERENCES "Charge" ("charge_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "SpendingLimit" ADD FOREIGN KEY ("account_member_id") REFERENCES "OnboardAccountMember" ("account_member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Booking" ADD FOREIGN KEY ("cruise_id") REFERENCES "Cruise" ("cruise_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Booking" ADD FOREIGN KEY ("settlement_onboard_account_id") REFERENCES "OnboardAccount" ("onboard_account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BookingPassengerPackage" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseCheckin" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseCheckout" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseCheckout" ADD FOREIGN KEY ("final_settlement_id") REFERENCES "FinalSettlement" ("final_settlement_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "GangwayEvent" ADD FOREIGN KEY ("cruise_stop_id") REFERENCES "CruiseStop" ("cruise_stop_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "GuidanceSession" ADD FOREIGN KEY ("cruise_id") REFERENCES "Cruise" ("cruise_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "GuidanceSessionParticipant" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Feedback" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Feedback" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Feedback" ADD FOREIGN KEY ("registration_id") REFERENCES "Registration" ("registration_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ServiceRequest" ADD FOREIGN KEY ("booking_id") REFERENCES "Booking" ("booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ServiceRequest" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ServiceRequest" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourPackageActivity" ADD FOREIGN KEY ("onboard_activity_id") REFERENCES "OnboardActivity" ("onboard_activity_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourPackageExcursion" ADD FOREIGN KEY ("shore_excursion_id") REFERENCES "ShoreExcursion" ("shore_excursion_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardActivity" ADD FOREIGN KEY ("cruise_id") REFERENCES "Cruise" ("cruise_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Participation" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Registration" ADD FOREIGN KEY ("booking_passenger_id") REFERENCES "BookingPassenger" ("booking_passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Registration" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ShoreExcursion" ADD FOREIGN KEY ("cruise_stop_id") REFERENCES "CruiseStop" ("cruise_stop_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TourGroupPassenger" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Charge" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ChargeDispute" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Installment" ADD FOREIGN KEY ("booking_id") REFERENCES "Booking" ("booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Invoice" ADD FOREIGN KEY ("booking_id") REFERENCES "Booking" ("booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardAccount" ADD FOREIGN KEY ("cruise_id") REFERENCES "Cruise" ("cruise_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardAccountMember" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardTransaction" ADD FOREIGN KEY ("passenger_id") REFERENCES "Passenger" ("passenger_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Payment" ADD FOREIGN KEY ("booking_id") REFERENCES "Booking" ("booking_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Booking" ADD FOREIGN KEY ("booked_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CabinAssignment" ADD FOREIGN KEY ("assigned_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Charge" ADD FOREIGN KEY ("created_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ChargeDispute" ADD FOREIGN KEY ("assigned_to_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ChargeDispute" ADD FOREIGN KEY ("resolved_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CheckinStep" ADD FOREIGN KEY ("checked_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CruiseCheckout" ADD FOREIGN KEY ("exit_verified_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "FinalSettlement" ADD FOREIGN KEY ("handled_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "GuidanceSession" ADD FOREIGN KEY ("instructor_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Itinerary" ADD FOREIGN KEY ("approved_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Itinerary" ADD FOREIGN KEY ("created_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ItineraryChangeHistory" ADD FOREIGN KEY ("changed_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Notification" ADD FOREIGN KEY ("recipient_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OnboardTransaction" ADD FOREIGN KEY ("created_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OperationalIssue" ADD FOREIGN KEY ("assigned_to") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OperationalIssue" ADD FOREIGN KEY ("reported_by") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "OperationalIssue" ADD FOREIGN KEY ("resolved_by") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Passenger" ADD FOREIGN KEY ("user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "Refund" ADD FOREIGN KEY ("approved_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ServiceRequest" ADD FOREIGN KEY ("assigned_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "SpendingLimit" ADD FOREIGN KEY ("set_by_user_id") REFERENCES "User" ("user_id") DEFERRABLE INITIALLY IMMEDIATE;
