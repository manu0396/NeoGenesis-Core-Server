package com.neogenesis.server.application.compliance

import com.neogenesis.server.domain.model.TraceabilityRequirement
import java.io.BufferedReader
import java.io.InputStreamReader

class ComplianceTraceabilityService(
    private val requirements: List<TraceabilityRequirement>
) {

    fun allRequirements(): List<TraceabilityRequirement> = requirements

    fun operationCoverage(): Map<String, List<String>> {
        return requirements
            .flatMap { requirement ->
                requirement.linkedOperations.map { operation -> operation to requirement.requirementId }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, reqs) -> reqs.distinct().sorted() }
    }

    companion object {
        private const val TRACEABILITY_FILE = "iso13485/traceability.csv"

        fun fromClasspath(): ComplianceTraceabilityService {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(TRACEABILITY_FILE)
                ?: error("Missing traceability matrix resource: $TRACEABILITY_FILE")

            val lines = BufferedReader(InputStreamReader(stream)).readLines()
                .filter { it.isNotBlank() }
            require(lines.size > 1) { "Traceability matrix must include at least one requirement" }

            val requirements = lines.drop(1).map { line ->
                val tokens = splitCsvLine(line)
                require(tokens.size == 5) { "Traceability line has invalid format: $line" }
                val requirementId = tokens[0].trim()
                require(requirementId.isNotBlank()) { "Traceability requirement id cannot be blank" }

                TraceabilityRequirement(
                    requirementId = requirementId,
                    isoClause = tokens[1].trim(),
                    title = tokens[2].trim(),
                    linkedOperations = tokens[3].split(',').map { it.trim() }.filter { it.isNotBlank() },
                    verification = tokens[4].trim()
                )
            }

            val duplicateIds = requirements.groupBy { it.requirementId }.filterValues { it.size > 1 }.keys
            require(duplicateIds.isEmpty()) { "Duplicate requirement IDs found: $duplicateIds" }

            return ComplianceTraceabilityService(requirements)
        }

        private fun splitCsvLine(line: String): List<String> {
            val values = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false

            line.forEach { ch ->
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    ch == ',' && !inQuotes -> {
                        values += current.toString()
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            values += current.toString()
            return values
        }
    }
}
