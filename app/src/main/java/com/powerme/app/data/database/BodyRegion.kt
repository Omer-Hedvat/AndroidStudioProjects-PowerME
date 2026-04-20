package com.powerme.app.data.database

/** Body regions tracked for stress accumulation in the Body Heatmap (P6). */
enum class BodyRegion(val displayName: String) {
    ANTERIOR_DELTOID("Front Deltoid"),
    POSTERIOR_DELTOID("Rear Deltoid"),
    ELBOW_JOINT("Elbow"),
    WRIST_JOINT("Wrist"),
    KNEE_JOINT("Knee"),
    HIP_JOINT("Hip"),
    LOWER_BACK("Lower Back"),
    UPPER_BACK("Upper Back"),
    PECS("Pecs"),
    LATS("Lats"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CORE("Core"),
    NECK_CERVICAL("Neck / Cervical Spine")
}
