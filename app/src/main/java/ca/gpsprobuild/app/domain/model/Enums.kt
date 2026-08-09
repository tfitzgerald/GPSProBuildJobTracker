package ca.gpsprobuild.app.domain.model

/**
 * Every enum in the app. Room persists enums by name, not ordinal, so reordering
 * these is safe but renaming a constant is a breaking change that needs a migration.
 *
 * `label` is what appears in the UI. Keep it sentence case and in the vocabulary a
 * contractor actually uses on site.
 */

interface Labelled {
    val label: String
}

// --- Customers -------------------------------------------------------------

enum class CustomerType(override val label: String) : Labelled {
    RESIDENTIAL("Homeowner"),
    COMMERCIAL("Commercial"),
    PROPERTY_MANAGER("Property manager"),
    BUILDER("Builder / GC"),
    INSURANCE("Insurance");
}

enum class CustomerStatus(override val label: String) : Labelled {
    LEAD("Lead"),
    ACTIVE("Active"),
    PAST("Past client"),
    DORMANT("Dormant");
}

enum class ContactMethod(override val label: String) : Labelled {
    PHONE("Call"), TEXT("Text"), EMAIL("Email"), ANY("Any");
}

enum class ContactRole(override val label: String) : Labelled {
    SPOUSE("Spouse / partner"),
    TENANT("Tenant"),
    PROPERTY_MANAGER("Property manager"),
    DESIGNER("Designer"),
    ARCHITECT("Architect"),
    INSPECTOR("Inspector"),
    ADJUSTER("Insurance adjuster"),
    OTHER("Other");
}

// --- Jobs ------------------------------------------------------------------

enum class JobType(override val label: String) : Labelled {
    KITCHEN("Kitchen"),
    BATHROOM("Bathroom"),
    BASEMENT_FINISHING("Basement"),
    ADDITION("Addition"),
    DECK_PORCH("Deck / porch"),
    FENCE("Fence"),
    ROOFING("Roofing"),
    SIDING_EXTERIOR("Siding / exterior"),
    WINDOWS_DOORS("Windows & doors"),
    FLOORING("Flooring"),
    PAINTING("Painting"),
    DRYWALL_TAPING("Drywall & taping"),
    TRIM_CARPENTRY("Trim carpentry"),
    ELECTRICAL("Electrical"),
    PLUMBING("Plumbing"),
    HVAC("HVAC"),
    CONCRETE_MASONRY("Concrete & masonry"),
    LANDSCAPING("Landscaping"),
    DEMOLITION("Demolition"),
    GENERAL_REPAIR("General repair"),
    EMERGENCY_CALL("Emergency call"),
    INSURANCE_WORK("Insurance restoration"),
    OTHER("Other");
}

/**
 * The job pipeline. [pipelineOrder] drives the status chip strip on Job Detail;
 * off-pipeline states return -1 and are shown separately.
 */
enum class JobStatus(override val label: String, val pipelineOrder: Int) : Labelled {
    LEAD("Lead", 0),
    SITE_VISIT("Site visit", 1),
    ESTIMATING("Estimating", 2),
    QUOTED("Quoted", 3),
    APPROVED("Approved", 4),
    SCHEDULED("Scheduled", 5),
    IN_PROGRESS("In progress", 6),
    PUNCH_LIST("Punch list", 7),
    COMPLETE("Complete", 8),
    INVOICED("Invoiced", 9),
    PAID("Paid", 10),
    ON_HOLD("On hold", -1),
    CANCELLED("Cancelled", -1),
    LOST("Lost", -1);

    val isOnPipeline: Boolean get() = pipelineOrder >= 0
    val isOpen: Boolean get() = this !in setOf(PAID, CANCELLED, LOST)

    companion object {
        val pipeline: List<JobStatus> get() = entries.filter { it.isOnPipeline }.sortedBy { it.pipelineOrder }
    }
}

enum class Priority(override val label: String) : Labelled {
    LOW("Low"), NORMAL("Normal"), HIGH("High"), URGENT("Urgent");
}

enum class PermitStatus(override val label: String) : Labelled {
    NOT_NEEDED("Not needed"),
    APPLIED("Applied"),
    ISSUED("Issued"),
    INSPECTIONS_IN_PROGRESS("Inspections underway"),
    CLOSED("Closed");
}

// --- Tasks -----------------------------------------------------------------

enum class JobPhase(override val label: String, val order: Int) : Labelled {
    ADMIN("Admin", 0),
    PREP("Prep", 1),
    DEMO("Demolition", 2),
    ROUGH_IN("Rough-in", 3),
    INSPECTION("Inspection", 4),
    INSULATION("Insulation", 5),
    DRYWALL("Drywall", 6),
    PAINT("Paint", 7),
    FINISH("Finish", 8),
    FIXTURES("Fixtures", 9),
    CLEANUP("Cleanup", 10),
    PUNCH_LIST("Punch list", 11);
}

enum class TaskStatus(override val label: String) : Labelled {
    NOT_STARTED("Not started"),
    IN_PROGRESS("In progress"),
    BLOCKED("Blocked"),
    DONE("Done"),
    SKIPPED("Skipped");

    val isFinished: Boolean get() = this == DONE || this == SKIPPED
}

// --- Staff -----------------------------------------------------------------

enum class StaffRole(override val label: String) : Labelled {
    OWNER("Owner"),
    PROJECT_MANAGER("Project manager"),
    FOREMAN("Foreman"),
    CARPENTER("Carpenter"),
    APPRENTICE("Apprentice"),
    LABOURER("Labourer"),
    ELECTRICIAN("Electrician"),
    PLUMBER("Plumber"),
    HVAC_TECH("HVAC tech"),
    DRYWALLER("Drywaller"),
    PAINTER("Painter"),
    TILE_SETTER("Tile setter"),
    ROOFER("Roofer"),
    MASON("Mason"),
    LANDSCAPER("Landscaper"),
    SUBCONTRACTOR("Subcontractor"),
    OFFICE("Office");
}

enum class EmploymentType(override val label: String) : Labelled {
    EMPLOYEE("Employee"), SUBCONTRACTOR("Subcontractor"), TEMP("Temp / casual");
}

// --- Materials -------------------------------------------------------------

enum class MaterialCategory(override val label: String) : Labelled {
    LUMBER("Lumber"),
    SHEET_GOODS("Sheet goods"),
    FASTENERS("Fasteners"),
    DRYWALL("Drywall"),
    INSULATION("Insulation"),
    ELECTRICAL("Electrical"),
    PLUMBING("Plumbing"),
    HVAC("HVAC"),
    TILE_STONE("Tile & stone"),
    FLOORING("Flooring"),
    PAINT_FINISH("Paint & finishes"),
    TRIM_MOULDING("Trim & moulding"),
    HARDWARE("Hardware"),
    FIXTURES("Fixtures"),
    APPLIANCES("Appliances"),
    CABINETRY("Cabinetry"),
    CONCRETE_MASONRY("Concrete & masonry"),
    ROOFING("Roofing"),
    WINDOWS_DOORS("Windows & doors"),
    ADHESIVE_SEALANT("Adhesives & sealants"),
    TOOL_RENTAL("Tool rental"),
    DISPOSAL("Disposal"),
    OTHER("Other");
}

enum class MaterialUnit(override val label: String, val short: String) : Labelled {
    EA("each", "ea"),
    BOX("box", "box"),
    CASE("case", "case"),
    SHEET("sheet", "sht"),
    BUNDLE("bundle", "bdl"),
    PACK("pack", "pk"),
    LF("linear feet", "lf"),
    SF("square feet", "sf"),
    BF("board feet", "bf"),
    SQ("roofing square", "sq"),
    CUYD("cubic yard", "yd³"),
    BAG("bag", "bag"),
    PAIL("pail", "pail"),
    GAL("gallon", "gal"),
    LITRE("litre", "L"),
    ROLL("roll", "roll"),
    TUBE("tube", "tube"),
    HOUR("hour", "hr");
}

enum class MaterialStatus(override val label: String) : Labelled {
    NEEDED("Needed"),
    QUOTED("Quoted"),
    ORDERED("Ordered"),
    PARTIAL("Partially received"),
    RECEIVED("Received"),
    INSTALLED("Installed"),
    BACKORDERED("Backordered"),
    RETURNED("Returned"),
    CANCELLED("Cancelled");

    /** Items that still have to be bought or chased. Drives the cross-job buy list. */
    val isOutstanding: Boolean
        get() = this == NEEDED || this == QUOTED || this == ORDERED ||
            this == PARTIAL || this == BACKORDERED

    /** Items that count toward job material cost. */
    val countsTowardCost: Boolean
        get() = this != CANCELLED && this != RETURNED
}

// --- Media -----------------------------------------------------------------

enum class PhotoCategory(override val label: String) : Labelled {
    BEFORE("Before"),
    PROGRESS("Progress"),
    AFTER("After"),
    DAMAGE("Damage"),
    ISSUE("Issue"),
    MEASUREMENT("Measurement"),
    MATERIAL("Material"),
    RECEIPT("Receipt"),
    PERMIT("Permit"),
    PLAN_DRAWING("Plan / drawing"),
    SIGNATURE("Signature"),
    OTHER("Other");
}

enum class DocumentType(override val label: String) : Labelled {
    QUOTE("Quote"),
    CONTRACT("Contract"),
    CHANGE_ORDER("Change order"),
    PERMIT("Permit"),
    INVOICE("Invoice"),
    RECEIPT("Receipt"),
    WARRANTY("Warranty"),
    SPEC_SHEET("Spec sheet"),
    DRAWING("Drawing"),
    REPORT("Report"),
    OTHER("Other");
}

// --- Money -----------------------------------------------------------------

enum class ExpenseCategory(override val label: String) : Labelled {
    MATERIALS("Materials"),
    TOOL_RENTAL("Tool rental"),
    PERMIT_FEE("Permit fee"),
    DISPOSAL("Disposal"),
    SUBCONTRACTOR("Subcontractor"),
    FUEL("Fuel"),
    EQUIPMENT("Equipment"),
    OTHER("Other");
}

enum class PaymentMethod(override val label: String) : Labelled {
    COMPANY_CARD("Company card"),
    PERSONAL_CARD("Personal card"),
    CASH("Cash"),
    CHEQUE("Cheque"),
    ETRANSFER("e-Transfer"),
    ACCOUNT("Supplier account");
}

enum class ChangeOrderStatus(override val label: String) : Labelled {
    DRAFT("Draft"),
    PRESENTED("Presented"),
    APPROVED("Approved"),
    DECLINED("Declined");
}

// --- Timeline & schedule ---------------------------------------------------

enum class JobEventType(override val label: String) : Labelled {
    STATUS_CHANGE("Status change"),
    SITE_LOG("Site log"),
    NOTE("Note"),
    PHOTO_ADDED("Photos added"),
    TASK_COMPLETED("Task completed"),
    MATERIAL_RECEIVED("Material received"),
    CHANGE_ORDER("Change order"),
    INSPECTION("Inspection"),
    CUSTOMER_CONTACT("Customer contact"),
    SYNC("Sync");
}

enum class AppointmentType(override val label: String) : Labelled {
    SITE_VISIT("Site visit"),
    ESTIMATE("Estimate"),
    WORK_DAY("Work day"),
    DELIVERY("Delivery"),
    INSPECTION("Inspection"),
    SUPPLIER_PICKUP("Supplier pickup"),
    WALKTHROUGH("Walkthrough"),
    FOLLOW_UP("Follow up"),
    PERSONAL("Personal");
}

enum class Weather(override val label: String) : Labelled {
    CLEAR("Clear"),
    CLOUDY("Cloudy"),
    RAIN("Rain"),
    SNOW("Snow"),
    WIND("High wind"),
    EXTREME_COLD("Extreme cold"),
    EXTREME_HEAT("Extreme heat");
}

// --- Device, sync and privacy ----------------------------------------------

enum class DeviceRole(override val label: String) : Labelled {
    /** Holds the complete book of record. One per business. */
    OWNER("Owner"),

    /** Carries only assigned jobs, and never receives cost figures. */
    FIELD("Field");
}

/**
 * Controls which currency figures render. Read through a CompositionLocal by
 * `MoneyText`, so no screen implements hiding logic of its own — a new screen
 * cannot leak a figure by forgetting to check.
 */
enum class PrivacyMode(override val label: String, val description: String) : Labelled {
    FULL("Show everything", "All figures visible"),
    CLIENT_SAFE(
        "Client safe",
        "Hides internal cost, margin and crew rates. Contract value stays visible."
    ),
    LOCKED("Hide all money", "Every currency figure is masked");

    val hidesInternalCost: Boolean get() = this != FULL
    val hidesContractValue: Boolean get() = this == LOCKED
}

enum class PacketType(override val label: String) : Labelled {
    ASSIGNMENT("Assignment"),
    FIELD_REPORT("Field report");
}

enum class SyncDirection(override val label: String) : Labelled {
    EXPORT("Sent"), IMPORT("Received");
}

enum class LeadStatus(override val label: String) : Labelled {
    PENDING("Pending"), ACCEPTED("Accepted"), DISMISSED("Dismissed");
}
