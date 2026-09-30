package dev.apidocs.core.ai.narrative;

import dev.apidocs.core.ai.Describe;
import java.util.List;

public record TechnicalDocNarrative(
        @Describe("Two or three paragraphs: what the system does, who uses it, main capabilities.") String overview,
        @Describe("Main domain concepts.") List<DomainConcept> domainConcepts,
        @Describe("Business rules grouped by domain area.") List<RuleGroup> businessRules,
        @Describe("One paragraph on how errors are reported to clients.") String errorHandling,
        @Describe("Domain terms a new developer must know.") List<GlossaryEntry> glossary) {
}
