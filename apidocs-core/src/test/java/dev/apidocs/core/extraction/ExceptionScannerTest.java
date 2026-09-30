package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.body.MethodDeclaration;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExceptionScannerTest {

    private final List<MethodDeclaration> methods = JavaSnippets.parse("""
            package com.x;
            class Holder {
                Order nestedCreation() {
                    return repo.find().orElseThrow(() -> new BusinessException(new ErrorDetail("x"), List.of(new Extra())));
                }
                Order constructorReference() { return repo.find().orElseThrow(NotFoundException::new); }
                Order qualifiedReference() { return repo.find().orElseThrow(errors.Missing::new); }
                Order blockSupplier() {
                    return repo.find().orElseThrow(() -> {
                        ErrorDetail detail = new ErrorDetail("x");
                        return new BusinessException(detail);
                    });
                }
                Order nestedLambdaSupplier() {
                    return repo.find().orElseThrow(() -> {
                        Supplier<Object> decoy = () -> { return new Decoy(); };
                        return new Real(decoy);
                    });
                }
                Order conditionalSupplier() {
                    return repo.find().orElseThrow(() -> flag ? new First(new Detail()) : new Second());
                }
                Order factorySupplier() { return repo.find().orElseThrow(() -> factory.missing(new Detail())); }
                void throwsNested() throws IOException {
                    throw new BusinessException(new ErrorDetail("x"));
                }
            }
            """).get(0).cu().getClassByName("Holder").orElseThrow().getMethods();

    private List<String> scan(String name) {
        return ExceptionScanner.scan(methods.stream().filter(m -> m.getNameAsString().equals(name)).findFirst()
                .orElseThrow());
    }

    @Test
    void orElseThrowKeepsOnlyTheSupplierCreationNotNestedOnes() {
        assertThat(scan("nestedCreation")).containsExactly("BusinessException");
    }

    @Test
    void orElseThrowKeepsOnlyReturnedCreationOfBlockSuppliers() {
        assertThat(scan("blockSupplier")).containsExactly("BusinessException");
    }

    @Test
    void orElseThrowIgnoresReturnsOfLambdasNestedInTheSupplier() {
        assertThat(scan("nestedLambdaSupplier")).containsExactly("Real");
    }

    @Test
    void orElseThrowKeepsEveryBranchOfAConditionalSupplierWithoutNestedCreations() {
        assertThat(scan("conditionalSupplier")).containsExactly("First", "Second");
    }

    @Test
    void orElseThrowIgnoresCreationsPassedToAFactory() {
        assertThat(scan("factorySupplier")).isEmpty();
    }

    @Test
    void orElseThrowUnderstandsConstructorReferences() {
        assertThat(scan("constructorReference")).containsExactly("NotFoundException");
        assertThat(scan("qualifiedReference")).containsExactly("Missing");
    }

    @Test
    void throwStatementsKeepTheOutermostCreationAndThrowsClauseComesLast() {
        assertThat(scan("throwsNested")).containsExactly("BusinessException", "IOException");
    }
}
