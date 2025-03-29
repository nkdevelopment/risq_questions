package nkdevelopment.net.sire_questions

// Section data class
data class Section(
    val id: Int,
    val title: String,
    val description: String
)

// List of sections - centralized in one location
object SectionData {
    val sections = listOf(
        Section(1, "General Information", "Basic vessel and inspection details"),
        Section(
            2,
            "Certification and Personnel Management",
            "Vessel certificates and crew documentation"
        ),
        Section(3, "Navigation", "Navigation equipment and procedures"),
        Section(4, "ISM System", "Safety management systems and documentation"),
        Section(5, "Pollution Prevention and Control", "Environmental protection measures"),
        Section(6, "Fire Safety", "Fire prevention and firefighting systems"),
        Section(71, "Fuel Management (Oil)", "Oil fuel handling and storage"),
        Section(72, "Fuel Management (Alternative Fuel-LNG)", "LNG fuel systems and procedures"),
        Section(
            73,
            "Fuel Management (Alternative Fuel-Methane)",
            "Methane fuel handling and systems"
        ),
        Section(74, "Fuel Management (Alternative Fuel-Ammonia)", "Ammonia fuel management"),
        Section(
            81,
            "Cargo Operation-Solid Bulk Cargo other than Grain",
            "Solid bulk cargo handling procedures"
        ),
        Section(82, "Cargo Operation-Bulk Grain", "Grain cargo operations and safety"),
        Section(83, "Cargo Operation-General Cargo", "General cargo handling procedures"),
        Section(84, "Cargo Operation-Cellular Container Ships", "Container stowage and handling"),
        Section(
            85,
            "Cargo Operation-Self-Unloading Transshipment",
            "Self-unloading systems and procedures"
        ),
        Section(9, "Hatch Cover and Lifting Appliances", "Hatch cover operations and maintenance"),
        Section(91, "Gantry Cranes", "Gantry crane operations and safety"),
        Section(10, "Mooring Operations", "Mooring equipment and procedures"),
        Section(11, "Radio and Communication", "Communication systems and procedures"),
        Section(12, "Security", "Security measures and protocols"),
        Section(13, "Machinery Space", "Engine room and machinery systems"),
        Section(
            14,
            "General Appearance - Hull and Superstructure",
            "Hull condition and maintenance"
        ),
        Section(15, "Health and Welfare of Seafarers", "Crew accommodation and welfare"),
        Section(16, "Ice or Polar Water Operations", "Operations in ice conditions"),
        Section(17, "Ship To Ship Operation", "Ship to ship transfer procedures")
    )
}