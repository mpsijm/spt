package org.metaborg.spt.core;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.core.project.IProject;
import org.metaborg.core.source.ISourceRegion;
import org.spoofax.interpreter.terms.IStrategoTerm;

public interface ITestCase {

    /**
     * The description or name of the test case.
     */
    public String getDescription();

    /**
     * The source region covered by the test's description.
     * 
     * Use this to place messages that appear during test runs, but that have no corresponding region in the test
     * fragment.
     */
    public ISourceRegion getDescriptionRegion();

    /**
     * The fragment of this test case. I.e., the piece of code written in the language under test that is being tested.
     */
    public IFragment getFragment();

    /**
     * The source file (or other resource) of the test suite from which this test case was extracted.
     */
    public FileObject getResource();

    /**
     * The project that contains this test. It is required for analysis of fragments.
     */
    public IProject getProject();

    /**
     * A list of tuples of an SPT AST term of a test expectation, and the ITestExpectation that can be used to evaluate
     * it. One for each expectation on this test case.
     */
    public List<ExpectationPair> getExpectations();

    /**
     * A tuple of a test expectation and an ITestExpectation that claims to be able to evaluate it.
     * 
     * Note that the evaluator can be null if no such ITestExpectation was found. Whether you consider that to be an
     * error is up to you.
     */
    public static class ExpectationPair {
        @Nullable public final ITestExpectation evaluator;
        public final IStrategoTerm expectation;

        public ExpectationPair(@Nullable ITestExpectation evaluator, IStrategoTerm expectation) {
            this.evaluator = evaluator;
            this.expectation = expectation;
        }
    }
}
