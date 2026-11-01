package com.myapp.adventure.data

enum class IssueSeverity { ERROR, WARNING }

data class ValidationIssue(
    val severity: IssueSeverity,
    val sceneUid: String,
    val title: String,
    val description: String
)