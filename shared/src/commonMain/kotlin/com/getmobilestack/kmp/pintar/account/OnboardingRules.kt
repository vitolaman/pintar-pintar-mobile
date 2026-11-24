package com.getmobilestack.kmp.pintar.account

val onboardingRoles = listOf("mahasiswa", "content_creator", "professional", "ibu_rumah_tangga", "brand_bisnis", "custom")

fun onboardingPayload(role: String?, customRole: String, skills: List<String>): OnboardingUpdate {
    require(role in onboardingRoles) { "Pilih peran Anda." }
    val custom = customRole.trim()
    require(role != "custom" || (custom.isNotEmpty() && custom.length <= 60)) { "Peran kustom wajib diisi, maksimal 60 karakter." }
    val unique = skills.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }
    require(unique.size <= 20 && unique.all { it.length <= 50 }) { "Maksimal 20 keahlian, masing-masing 50 karakter." }
    return OnboardingUpdate(role!!, custom.takeIf { role == "custom" }, unique)
}
