package com.entio.diff

import com.entio.core.SemanticDiff

public class SemanticDiffFormatter {
    public fun format(diff: SemanticDiff): String =
        if (diff.entries.isEmpty()) {
            "No semantic changes."
        } else {
            diff.entries.joinToString(separator = "\n") { entry -> entry.description }
        }
}
