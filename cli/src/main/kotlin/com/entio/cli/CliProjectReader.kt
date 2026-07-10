package com.entio.cli

import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.LoadedSymbol
import com.entio.semantic.ProjectLoader
import java.nio.file.Path

public class CliProjectReader(
    private val projectLoader: ProjectLoader = ProjectLoader(),
) {
    public fun loadSymbols(projectRoot: Path): EntioResult<List<LoadedSymbol>> =
        when (val result = projectLoader.loadProject(projectRoot)) {
            is EntioResult.Failure -> result
            is EntioResult.Success -> EntioResult.Success(result.value.symbols)
        }

    public fun loadGraph(projectRoot: Path): EntioResult<GraphState> =
        when (val result = projectLoader.loadProject(projectRoot)) {
            is EntioResult.Failure -> result
            is EntioResult.Success -> EntioResult.Success(result.value.graph)
        }
}
